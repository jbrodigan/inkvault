package com.inkvault.pen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.inkvault.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Phase 1 — fixes the brief's #2 (background BLE reconnect). The OS (and OneUI especially) will
 * sleep a backgrounded app and drop the pen; a foreground service keeps the connection alive.
 *
 * This service OWNS the connection lifecycle but delegates the actual connect + auto-reconnect to
 * the singleton [PenConnectionManager] in [ServiceLocator], and mirrors its [PenConnState] in a
 * persistent notification. Foreground-service type = `connectedDevice`.
 *
 * Start it from a foreground Activity AFTER BLE runtime permissions are granted (see
 * [com.inkvault.ui.MainActivity]): `PenForegroundService.start(context)`. The pen then reconnects
 * on its own; the service runs until [stop] / the user taps "Stop". Document the
 * battery-optimization exemption — see [BatteryOptimization].
 */
class PenForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sl: ServiceLocator
    private val penManager get() = sl.penManager
    private var offlineRequested = false
    private var prevState: PenConnState = PenConnState.Disconnected()
    private var pagesSafe = 0
    private var lowBatteryWarned = false

    override fun onCreate() {
        super.onCreate()
        sl = ServiceLocator.from(this)
        createChannels()
        // startForeground must happen promptly after startForegroundService().
        startForeground(penManager.state.value, com.inkvault.repo.LiveCaptureStatus(null, null, 0))
        // Keep the "pages safe" figure current for the disconnect alert.
        scope.launch { sl.repository.pageCount().collect { pagesSafe = it } }
        // Proactive pen health: warn once when battery is low and NOT charging; reset on recovery.
        scope.launch {
            penManager.battery.collect { b ->
                if (b == null) return@collect
                val lowAndDraining = b.percent <= LOW_BATTERY_PCT && b.chargeEtaMinutes == null
                if (lowAndDraining && !lowBatteryWarned) {
                    lowBatteryWarned = true
                    notificationManager().notify(PenNotifications.BATTERY_NOTIF_ID, buildBatteryWarning(b.percent))
                } else if (b.percent > LOW_BATTERY_CLEAR_PCT || b.chargeEtaMinutes != null) {
                    lowBatteryWarned = false
                }
            }
        }
        // Drive the silent ongoing notification from connection state + live capture status, and
        // fire/clear the loud failure alert on connection-loss transitions.
        scope.launch {
            combine(penManager.state, sl.repository.liveCaptureStatus()) { s, st -> s to st }
                .collect { (state, status) ->
                    if (state != prevState) {
                        Log.i("InkVaultPen", "state -> $state")
                        // On each fresh connection, pull anything the pen captured offline (idempotent),
                        // and reset the ink signals so the diagnostic reflects THIS session.
                        if (state is PenConnState.Connected) {
                            sl.captureSignals.onConnected()
                            if (!offlineRequested) { offlineRequested = true; sl.offlineSync.requestAll() }
                        } else {
                            offlineRequested = false
                        }
                        handleAlertTransition(prevState, state)
                        prevState = state
                    }
                    // Silent ongoing notification (stays up while disconnected so the user can scan).
                    notificationManager().notify(PenNotifications.NOTIF_ID, buildCaptureNotification(state, status))
                }
        }
    }

    /**
     * Loud failure ONLY on connection loss during capture (the reliable trigger). Was-connected →
     * not-connected fires a high-importance alert + haptic naming the pages already safe; returning
     * to Connected clears it. Dot-stall is deliberately not a trigger (it would false-alarm on idle).
     */
    private fun handleAlertTransition(prev: PenConnState, cur: PenConnState) {
        val wasConnected = prev is PenConnState.Connected
        when {
            wasConnected && (cur is PenConnState.Reconnecting || cur is PenConnState.Disconnected) ->
                notificationManager().notify(
                    PenNotifications.ALERT_NOTIF_ID,
                    buildAlertNotification(reconnecting = cur is PenConnState.Reconnecting),
                )
            cur is PenConnState.Connected ->
                notificationManager().cancel(PenNotifications.ALERT_NOTIF_ID)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            penManager.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        // An explicit pen MAC (from the scan/pick UI or -PpenMac) connects it; otherwise reconnect to
        // the last-used pen. Discovery itself is driven by the UI via PenScanner.
        val mac = intent?.getStringExtra(EXTRA_MAC)
        if (mac != null) penManager.connect(mac) else penManager.autoConnect()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Clearing the app from recents is a deliberate "I'm done" — drop the pen link and stop the
        // service instead of keeping a background connection alive. (Foreground services otherwise
        // survive task removal, which is why the pen looked still-connected after swiping away.)
        penManager.disconnect()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForeground(state: PenConnState, status: com.inkvault.repo.LiveCaptureStatus) {
        ServiceCompat.startForeground(
            this,
            PenNotifications.NOTIF_ID,
            buildCaptureNotification(state, status),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            else 0,
        )
    }

    private fun openAppIntent() =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

    /** The silent ongoing capture notification — a reassurance object, never an alert. */
    private fun buildCaptureNotification(
        state: PenConnState,
        status: com.inkvault.repo.LiveCaptureStatus,
    ): Notification {
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, PenForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, PenNotifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("InkVault")
            .setContentText(PenNotifications.captureText(state, status.notebookTitle, status.page, status.strokeCount))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop", stopPi)
            .build()
    }

    /** The loud failure notification — high-importance channel + haptic; clears on reconnect. */
    private fun buildAlertNotification(reconnecting: Boolean): Notification =
        NotificationCompat.Builder(this, PenNotifications.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Pen disconnected")
            .setContentText(PenNotifications.alertText(pagesSafe, reconnecting))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun buildBatteryWarning(percent: Int): Notification =
        NotificationCompat.Builder(this, PenNotifications.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Pen battery low")
            .setContentText(PenNotifications.batteryWarningText(percent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Silent, low-importance: the ongoing capture state.
            val capture = NotificationChannel(
                PenNotifications.CHANNEL_ID, PenNotifications.CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW,
            ).apply { setSound(null, null); enableVibration(false) }
            // High-importance with vibration: failures only. Respects the user's system settings.
            val alert = NotificationChannel(
                PenNotifications.ALERT_CHANNEL_ID, PenNotifications.ALERT_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH,
            ).apply { enableVibration(true); description = "Pen disconnects and capture failures" }
            notificationManager().createNotificationChannel(capture)
            notificationManager().createNotificationChannel(alert)
        }
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val ACTION_STOP = "com.inkvault.pen.STOP"
        private const val EXTRA_MAC = "mac"
        private const val LOW_BATTERY_PCT = 15
        private const val LOW_BATTERY_CLEAR_PCT = 20

        /** Start capture. Call from a foreground Activity with BLE permissions already granted. */
        fun start(context: Context, macAddress: String? = null) {
            val intent = Intent(context, PenForegroundService::class.java)
            if (macAddress != null) intent.putExtra(EXTRA_MAC, macAddress)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PenForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
