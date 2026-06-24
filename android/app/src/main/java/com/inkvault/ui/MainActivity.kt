package com.inkvault.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.inkvault.di.ServiceLocator
import com.inkvault.pen.PenForegroundService
import com.inkvault.security.AppLock

// FragmentActivity (not bare ComponentActivity) so the AndroidX BiometricPrompt can attach — see AppLock.
class MainActivity : FragmentActivity() {

    // Vault unlocked for this foreground session (Section C1); re-locked in onStop when backgrounded.
    private var unlocked by mutableStateOf(false)

    private val viewModel: InkViewModel by viewModels {
        val sl = ServiceLocator.from(applicationContext)
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                InkViewModel(sl.repository, sl.penManager, sl.settings, sl.penScanner, sl::exportPageNow, sl.strokeEditor, sl.inkColor, sl.inkWidth, sl.recordingController, sl.captureSignals, sl.penPassword::has, sl.calendarGateway, sl.actionZones, sl::captureNextTrace, sl::cancelTraceCapture, sl.captureLog, sl.backgrounds, sl.transcriptImporter, sl::transcribeOnDevice, sl.translator) as T
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            // Need at least the BLE permissions to run the connection service.
            if (result.filterKeys { it in blePermissions() }.values.all { it }) {
                startPenService()
            }
        }

    // Auto-connect to the build-time pen MAC if one was injected (-PpenMac); otherwise the service
    // just auto-reconnects to the last-remembered pen. A scan/pair screen replaces this in Phase 1.
    private fun startPenService() =
        PenForegroundService.start(this, com.inkvault.BuildConfig.PEN_MAC.ifBlank { null })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 (targetSdk 35) draws edge-to-edge by default; opt in explicitly (transparent
        // system bars) and inset our content for them below, so the OS bars never overlap the UI.
        enableEdgeToEdge()
        // Keep the screen awake while the app is foreground so the pen connection / capture isn't
        // interrupted during use (and you don't have to keep tapping the screen while testing).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ensurePermissionsThenStartService()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val lockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
            InkVaultTheme(themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) {
                        if (lockEnabled && !unlocked) {
                            LockScreen { AppLock.prompt(this@MainActivity) { ok -> if (ok) unlocked = true } }
                        } else {
                            InkApp(viewModel)
                        }
                    }
                }
            }
        }
    }

    // Re-lock when the app is genuinely backgrounded — but not on a rotation/config change.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) unlocked = false
    }

    private fun ensurePermissionsThenStartService() {
        val needed = (blePermissions() + notificationPermission())
            .filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) startPenService()
        else requestPermissions.launch(needed.toTypedArray())
    }

    private fun blePermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun notificationPermission(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyList()
}

/** Minimal placeholder shown while the vault is locked; auto-prompts on appear, with a manual retry. */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("InkVault is locked", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 16.dp)) { Text("Unlock") }
    }
}
