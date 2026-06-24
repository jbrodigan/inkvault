package com.inkvault.pen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the pen connection lifecycle and fixes complaint #4 (fragile pairing /
 * no reconnect). Instead of a one-shot connect, this is an explicit state
 * machine with auto-reconnect-with-backoff to the last-known pen.
 *
 *   DISCONNECTED ─connect()→ CONNECTING ─ok→ CONNECTED
 *        ▲                        │ fail        │ link lost
 *        └──── backoff(2..16s) ◄ RECONNECTING ◄─┘
 *
 * Every [PenDot] received while connected is forwarded to [onDot] — which in
 * production is [com.inkvault.ingest.StrokeIngestor.onDot], persisting it
 * immediately (complaint #1).
 *
 * @param backoffSchedule reconnection delays in ms; the last value repeats.
 */
class PenConnectionManager(
    private val sdk: NeoPenSdk,
    private val prefs: PenPrefs,
    private val scope: CoroutineScope,
    private val onDot: (PenDot) -> Unit,
    /**
     * Called with a password the user typed that the pen then accepted (a successful unlock). The
     * host persists it to its secure store so the next connect auto-unlocks ("enter once"). NOT
     * called for passwords the pen auto-tried from storage — those are already stored.
     */
    private val onPasswordAccepted: (String) -> Unit = {},
    /** Called when the pen's password is turned off — the host clears its stored secret. */
    private val onPasswordCleared: () -> Unit = {},
    private val backoffSchedule: List<Long> = listOf(2_000, 4_000, 8_000, 16_000),
    /** Stop auto-reconnecting after this many failed attempts and return to Disconnected. */
    private val maxReconnectAttempts: Int = 6,
    private val now: () -> Long = System::currentTimeMillis,
    /** How often to poll the pen for a fresh battery reading while connected. */
    private val batteryPollMs: Long = 60_000L,
) {
    private val _state = MutableStateFlow<PenConnState>(PenConnState.Disconnected())
    val state: StateFlow<PenConnState> = _state.asStateFlow()

    /** Latest pen battery (with a charge-time estimate when it's rising), or null when unknown. */
    private val _battery = MutableStateFlow<BatteryStatus?>(null)
    val battery: StateFlow<BatteryStatus?> = _battery.asStateFlow()
    private val batterySamples = ArrayDeque<Pair<Long, Int>>() // (timeMs, percent)
    private var pollJob: Job? = null

    private var reconnecting = false

    /** A password the user just typed via the prompt, held until the pen accepts it (then persisted). */
    private var pendingPassword: String? = null

    /** Result of an in-flight change/disable-password request, for the Settings UI. */
    private val _passwordOp = MutableStateFlow<PasswordOpState>(PasswordOpState.Idle)
    val passwordOp: StateFlow<PasswordOpState> = _passwordOp.asStateFlow()
    private var pendingNewPassword: String? = null // the new password to persist if a change succeeds
    private var pendingDisable = false             // true while a disable-password op is in flight

    /** The connected pen's firmware version, or null until it reports one. */
    private val _firmwareVersion = MutableStateFlow<String?>(null)
    val firmwareVersion: StateFlow<String?> = _firmwareVersion.asStateFlow()

    /** Progress/result of an in-flight firmware update, for the Settings UI. */
    private val _firmwareUpdate = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val firmwareUpdate: StateFlow<FirmwareUpdateState> = _firmwareUpdate.asStateFlow()

    /**
     * The last pen we were asked to connect to, kept so reconnect uses the SAME spp/le/protocol.
     * The LE address is a rotating random address, so this is only valid within a session — across
     * a process restart you must re-scan (a bare persisted MAC can't be GATT-connected).
     */
    private var lastTarget: PenTarget? = null

    init {
        sdk.setDotListener(onDot)
        sdk.setListener(::onPenMessage)
    }

    /** Connect to an explicitly chosen pen (e.g. from a scan result). */
    fun connect(target: PenTarget) {
        lastTarget = target
        prefs.lastPenMac = target.sppAddress
        _state.value = PenConnState.Connecting(target.id)
        sdk.connect(target)
    }

    /** Convenience for tests / the legacy `-PpenMac` path, where only a bare address is known. */
    fun connect(macAddress: String) = connect(PenTarget.legacy(macAddress))

    /**
     * Auto-reconnect to the last pen of this session. After a process restart there is no usable
     * target (the LE address has rotated), so this is a no-op until the user re-scans.
     */
    fun autoConnect() {
        connect(lastTarget ?: return)
    }

    /** Resolve the single-device-pairing block by forcing a bond takeover, then retry. */
    fun takeOver(macAddress: String) {
        sdk.releaseExistingBond(macAddress)
        connect(lastTarget ?: PenTarget.legacy(macAddress))
    }

    fun disconnect() {
        reconnecting = false
        stopBatteryPolling()
        sdk.disconnect()
        _state.value = PenConnState.Disconnected()
    }

    private fun onPenMessage(msg: PenMessage) {
        when (msg) {
            is PenMessage.Connected -> {
                reconnecting = false
                // The pen accepted the password the user just typed → persist it for next time.
                pendingPassword?.let { onPasswordAccepted(it); pendingPassword = null }
                prefs.lastPenMac = msg.macAddress
                _state.value = PenConnState.Connected(msg.macAddress, msg.penName)
                startBatteryPolling()
            }
            is PenMessage.Disconnected -> {
                stopBatteryPolling()
                // Unexpected drop while we believed we were connected -> auto-reconnect.
                if (_state.value is PenConnState.Connected) scheduleReconnect()
                else _state.value = PenConnState.Disconnected()
            }
            is PenMessage.ConnectFailed -> {
                if (msg.reason == PenMessage.FailureReason.BONDED_ELSEWHERE) {
                    // Surface an actionable state instead of a dead end (complaint #4).
                    _state.value = PenConnState.BondedElsewhere(prefs.lastPenMac.orEmpty())
                } else {
                    scheduleReconnect()
                }
            }
            is PenMessage.PasswordRequired -> {
                // A (re)prompt means any password we just tried was wrong — don't persist it.
                pendingPassword = null
                _state.value = PenConnState.PasswordRequired(prefs.lastPenMac.orEmpty())
            }
            is PenMessage.PasswordResult -> {
                if (msg.success) {
                    if (pendingDisable) onPasswordCleared()
                    else pendingNewPassword?.let { onPasswordAccepted(it) }
                }
                _passwordOp.value = PasswordOpState.Done(success = msg.success, disabled = pendingDisable)
                pendingNewPassword = null
                pendingDisable = false
            }
            is PenMessage.FirmwareVersion -> _firmwareVersion.value = msg.version
            is PenMessage.FirmwareProgress -> _firmwareUpdate.value = FirmwareUpdateState.Working(msg.percent)
            is PenMessage.FirmwareResult -> _firmwareUpdate.value = FirmwareUpdateState.Done(msg.success)
            is PenMessage.Battery -> onBattery(msg.percent)
            is PenMessage.OfflineDataAvailable, PenMessage.BatteryLow -> Unit
        }
    }

    // --- battery + charge-rate estimate ---

    private fun onBattery(percent: Int) {
        val t = now()
        batterySamples.addLast(t to percent)
        // Keep ~30 min of history (but always at least two points to estimate a rate from).
        while (batterySamples.size > 2 && t - batterySamples.first().first > 30 * 60_000L) {
            batterySamples.removeFirst()
        }
        _battery.value = BatteryStatus(percent.coerceIn(0, 100), chargeEtaMinutes(percent, t))
    }

    /** Minutes to 100% if the level is rising (charging), computed from the observed rate; else null. */
    private fun chargeEtaMinutes(percent: Int, t: Long): Int? {
        if (percent >= 100) return null
        val oldest = batterySamples.firstOrNull() ?: return null
        val dPct = percent - oldest.second
        val dMs = t - oldest.first
        if (dPct <= 0 || dMs < 90_000L) return null // not rising, or too little data to trust
        val msPerPct = dMs.toDouble() / dPct
        return ((100 - percent) * msPerPct / 60_000.0).toInt().takeIf { it in 1..6000 }
    }

    private fun startBatteryPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                sdk.requestStatus() // reply arrives as PenMessage.Battery
                delay(batteryPollMs)
            }
        }
    }

    private fun stopBatteryPolling() {
        pollJob?.cancel()
        pollJob = null
        batterySamples.clear()
        _battery.value = null
    }

    /** Answer a [PenConnState.PasswordRequired] with the user-entered password. */
    fun submitPassword(password: String) {
        pendingPassword = password // persisted via onPasswordAccepted once the pen authorizes
        _state.value = PenConnState.Connecting(prefs.lastPenMac.orEmpty())
        sdk.inputPassword(password)
    }

    /** Change the pen's unlock password (old → new). Watch [passwordOp] for the result. */
    fun changePassword(oldPassword: String, newPassword: String) {
        pendingNewPassword = newPassword
        pendingDisable = false
        _passwordOp.value = PasswordOpState.Working
        sdk.changePassword(oldPassword, newPassword)
    }

    /** Turn the pen's unlock password off. Watch [passwordOp] for the result. */
    fun disablePassword(currentPassword: String) {
        pendingNewPassword = null
        pendingDisable = true
        _passwordOp.value = PasswordOpState.Working
        sdk.disablePassword(currentPassword)
    }

    /** Dismiss a finished [passwordOp] result (the Settings UI calls this after showing it). */
    fun acknowledgePasswordOp() { _passwordOp.value = PasswordOpState.Idle }

    /** Flash a firmware image to the pen. Watch [firmwareUpdate] for progress/result. */
    fun updateFirmware(file: java.io.File) {
        _firmwareUpdate.value = FirmwareUpdateState.Working(0)
        sdk.updateFirmware(file)
    }

    /** Dismiss a finished [firmwareUpdate] result. */
    fun acknowledgeFirmwareUpdate() { _firmwareUpdate.value = FirmwareUpdateState.Idle }

    private fun scheduleReconnect() {
        val target = lastTarget ?: run {
            _state.value = PenConnState.Disconnected()
            return
        }
        if (reconnecting) return
        reconnecting = true
        scope.launch {
            var attempt = 0
            while (reconnecting && attempt < maxReconnectAttempts) {
                val delayMs = backoffSchedule[minOf(attempt, backoffSchedule.lastIndex)]
                _state.value = PenConnState.Reconnecting(target.id, attempt + 1, delayMs)
                delay(delayMs)
                if (!reconnecting) break
                _state.value = PenConnState.Connecting(target.id)
                sdk.connect(target) // success cancels the loop via onPenMessage(Connected)
                attempt++
                // Wait one cycle for the result before trying again.
                delay(backoffSchedule.first())
                if (_state.value is PenConnState.Connected) break
            }
            // Exhausted the retries without connecting → stop and return to a clean Disconnected
            // state (no endless loop); the user can re-scan and pick again.
            if (reconnecting && _state.value !is PenConnState.Connected) {
                reconnecting = false
                _state.value = PenConnState.Disconnected()
            }
        }
    }
}

/** Pen battery for the UI: level 0–100, plus minutes-to-full when charging (null otherwise). */
data class BatteryStatus(val percent: Int, val chargeEtaMinutes: Int?)

/** Lifecycle of a change/disable-password request, surfaced to the Settings UI. */
sealed interface PasswordOpState {
    data object Idle : PasswordOpState
    data object Working : PasswordOpState
    /** Finished: [success] true/false; [disabled] true if it was a disable (vs a change). */
    data class Done(val success: Boolean, val disabled: Boolean) : PasswordOpState
}

/** Lifecycle of a firmware update, surfaced to the Settings UI. */
sealed interface FirmwareUpdateState {
    data object Idle : FirmwareUpdateState
    data class Working(val percent: Int) : FirmwareUpdateState
    data class Done(val success: Boolean) : FirmwareUpdateState
}

/** Observable connection state for the UI. */
sealed interface PenConnState {
    data class Disconnected(val nothing: Unit = Unit) : PenConnState
    data class Connecting(val mac: String) : PenConnState
    data class Connected(val mac: String, val penName: String) : PenConnState
    data class Reconnecting(val mac: String, val attempt: Int, val nextDelayMs: Long) : PenConnState
    data class BondedElsewhere(val mac: String) : PenConnState
    data class PasswordRequired(val mac: String) : PenConnState
}
