package com.inkvault

import com.google.common.truth.Truth.assertThat
import com.inkvault.pen.FakeNeoPenSdk
import com.inkvault.pen.InMemoryPenPrefs
import com.inkvault.pen.FirmwareUpdateState
import com.inkvault.pen.PasswordOpState
import com.inkvault.pen.PenConnState
import com.inkvault.pen.PenConnectionManager
import com.inkvault.pen.PenMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Tests for the reconnect state machine (brief FIX #2/#4). */
@OptIn(ExperimentalCoroutinesApi::class)
class PenConnectionManagerTest {

    private fun manager(
        sdk: FakeNeoPenSdk,
        prefs: InMemoryPenPrefs,
        scope: CoroutineScope,
    ) = PenConnectionManager(
        sdk = sdk, prefs = prefs, scope = scope, onDot = {},
        backoffSchedule = listOf(1_000, 2_000, 4_000),
    )

    @Test
    fun `connect succeeds and remembers the pen`() = runTest(UnconfinedTestDispatcher()) {
        val sdk = FakeNeoPenSdk(); val prefs = InMemoryPenPrefs()
        val mgr = manager(sdk, prefs, backgroundScope)

        mgr.connect("AA:BB")

        assertThat(mgr.state.value).isInstanceOf(PenConnState.Connected::class.java)
        assertThat(prefs.lastPenMac).isEqualTo("AA:BB")
    }

    @Test
    fun `a pen bonded to another device surfaces an actionable takeover state`() = runTest(UnconfinedTestDispatcher()) {
        val sdk = FakeNeoPenSdk().apply { connectShouldFailWith = PenMessage.FailureReason.BONDED_ELSEWHERE }
        val prefs = InMemoryPenPrefs("AA:BB")
        val mgr = manager(sdk, prefs, backgroundScope)

        mgr.connect("AA:BB")
        assertThat(mgr.state.value).isInstanceOf(PenConnState.BondedElsewhere::class.java)

        // Take over releases the bond and reconnects.
        mgr.takeOver("AA:BB")
        advanceUntilIdle()
        assertThat(mgr.state.value).isInstanceOf(PenConnState.Connected::class.java)
    }

    @Test
    fun `an unexpected drop auto-reconnects on its own`() = runTest(UnconfinedTestDispatcher()) {
        val sdk = FakeNeoPenSdk(); val prefs = InMemoryPenPrefs()
        val mgr = manager(sdk, prefs, backgroundScope)
        mgr.connect("AA:BB")

        sdk.emitDisconnected()                     // link lost while we believed we were connected
        assertThat(mgr.state.value).isInstanceOf(PenConnState.Reconnecting::class.java)

        // The reconnect loop runs in backgroundScope; advanceUntilIdle() does NOT service
        // background coroutines, so move the virtual clock to let the backoff delay elapse.
        advanceTimeBy(10_000)                       // backoff elapses → reconnect succeeds
        assertThat(mgr.state.value).isInstanceOf(PenConnState.Connected::class.java)
    }

    @Test
    fun `reconnect keeps retrying with backoff while the pen is unreachable`() = runTest(UnconfinedTestDispatcher()) {
        val sdk = FakeNeoPenSdk(); val prefs = InMemoryPenPrefs()
        val mgr = manager(sdk, prefs, backgroundScope)
        mgr.connect("AA:BB")                        // connectAttempts = 1
        val afterFirstConnect = sdk.connectAttempts

        sdk.connectShouldFailWith = PenMessage.FailureReason.TIMEOUT
        sdk.emitDisconnected()
        assertThat((mgr.state.value as PenConnState.Reconnecting).attempt).isEqualTo(1)

        advanceTimeBy(10_000)                        // several backoff cycles, all failing
        assertThat(sdk.connectAttempts).isGreaterThan(afterFirstConnect + 1) // retried multiple times

        mgr.disconnect()                             // stops the loop
        assertThat(mgr.state.value).isInstanceOf(PenConnState.Disconnected::class.java)
    }

    @Test
    fun `reconnect gives up after the attempt cap instead of looping forever`() =
        runTest(UnconfinedTestDispatcher()) {
            val sdk = FakeNeoPenSdk(); val prefs = InMemoryPenPrefs()
            val mgr = PenConnectionManager(
                sdk = sdk, prefs = prefs, scope = backgroundScope, onDot = {},
                backoffSchedule = listOf(1_000), maxReconnectAttempts = 3,
            )
            mgr.connect("AA:BB")                       // initial connect succeeds
            sdk.connectShouldFailWith = PenMessage.FailureReason.TIMEOUT
            sdk.emitDisconnected()                     // drop → reconnect loop, every attempt fails

            advanceTimeBy(60_000)

            assertThat(mgr.state.value).isInstanceOf(PenConnState.Disconnected::class.java)
            assertThat(sdk.connectAttempts).isEqualTo(1 + 3) // initial + exactly 3 capped retries
        }

    @Test
    fun `a locked pen surfaces a password prompt and unlocks when the password is submitted`() =
        runTest(UnconfinedTestDispatcher()) {
            val sdk = FakeNeoPenSdk(); val prefs = InMemoryPenPrefs()
            val mgr = manager(sdk, prefs, backgroundScope)
            mgr.connect("AA:BB")

            sdk.emitPasswordRequired()
            assertThat(mgr.state.value).isInstanceOf(PenConnState.PasswordRequired::class.java)

            mgr.submitPassword("1234")               // fake authorizes on any non-empty password
            assertThat(mgr.state.value).isInstanceOf(PenConnState.Connected::class.java)
        }

    @Test
    fun `an accepted typed password is reported once so the host can store it`() =
        runTest(UnconfinedTestDispatcher()) {
            val sdk = FakeNeoPenSdk(); val prefs = InMemoryPenPrefs()
            val accepted = mutableListOf<String>()
            val mgr = PenConnectionManager(
                sdk = sdk, prefs = prefs, scope = backgroundScope, onDot = {},
                onPasswordAccepted = { accepted += it },
            )
            mgr.connect("AA:BB")                       // initial connect (no password) must not store
            sdk.emitPasswordRequired()
            mgr.submitPassword("4862")                 // fake authorizes → Connected

            assertThat(accepted).containsExactly("4862")
        }

    @Test
    fun `a wrong password that gets re-prompted is never stored`() =
        runTest(UnconfinedTestDispatcher()) {
            val sdk = FakeNeoPenSdk(); val prefs = InMemoryPenPrefs()
            val accepted = mutableListOf<String>()
            val mgr = PenConnectionManager(
                sdk = sdk, prefs = prefs, scope = backgroundScope, onDot = {},
                onPasswordAccepted = { accepted += it },
            )
            mgr.connect("AA:BB")
            sdk.emitPasswordRequired()
            mgr.submitPassword("")                     // fake rejects empty → no Connected
            sdk.emitPasswordRequired()                 // pen re-asks: the attempt was wrong

            assertThat(accepted).isEmpty()
        }

    @Test
    fun `changing the password persists the new one and reports success`() =
        runTest(UnconfinedTestDispatcher()) {
            val sdk = FakeNeoPenSdk().apply { fakePassword = "4862" }
            val accepted = mutableListOf<String>()
            var cleared = false
            val mgr = PenConnectionManager(
                sdk = sdk, prefs = InMemoryPenPrefs(), scope = backgroundScope, onDot = {},
                onPasswordAccepted = { accepted += it }, onPasswordCleared = { cleared = true },
            )

            mgr.changePassword("4862", "1111")

            assertThat(sdk.fakePassword).isEqualTo("1111")
            assertThat(accepted).containsExactly("1111")
            assertThat(cleared).isFalse()
            assertThat(mgr.passwordOp.value)
                .isEqualTo(PasswordOpState.Done(success = true, disabled = false))
        }

    @Test
    fun `disabling the password clears the stored secret and reports success`() =
        runTest(UnconfinedTestDispatcher()) {
            val sdk = FakeNeoPenSdk().apply { fakePassword = "4862" }
            var cleared = false
            val mgr = PenConnectionManager(
                sdk = sdk, prefs = InMemoryPenPrefs(), scope = backgroundScope, onDot = {},
                onPasswordCleared = { cleared = true },
            )

            mgr.disablePassword("4862")

            assertThat(sdk.fakePassword).isNull()
            assertThat(cleared).isTrue()
            assertThat(mgr.passwordOp.value)
                .isEqualTo(PasswordOpState.Done(success = true, disabled = true))
        }

    @Test
    fun `a rejected password change reports failure and stores nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val sdk = FakeNeoPenSdk().apply { fakePassword = "4862" }
            val accepted = mutableListOf<String>()
            val mgr = PenConnectionManager(
                sdk = sdk, prefs = InMemoryPenPrefs(), scope = backgroundScope, onDot = {},
                onPasswordAccepted = { accepted += it },
            )

            mgr.changePassword("wrong", "1111")

            assertThat(sdk.fakePassword).isEqualTo("4862") // unchanged
            assertThat(accepted).isEmpty()
            assertThat(mgr.passwordOp.value)
                .isEqualTo(PasswordOpState.Done(success = false, disabled = false))
        }

    @Test
    fun `firmware version is exposed and a flash reports a result`() =
        runTest(UnconfinedTestDispatcher()) {
            val sdk = FakeNeoPenSdk()
            val mgr = manager(sdk, InMemoryPenPrefs(), backgroundScope)

            sdk.emitFirmwareVersion("2.18")
            assertThat(mgr.firmwareVersion.value).isEqualTo("2.18")

            val img = java.io.File.createTempFile("fwimg", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            mgr.updateFirmware(img)
            assertThat(mgr.firmwareUpdate.value)
                .isEqualTo(FirmwareUpdateState.Done(success = true))
            img.delete()
        }
}
