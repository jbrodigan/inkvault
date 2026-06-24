package com.inkvault.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvault.export.SyncMethod
import com.inkvault.export.ThemeMode
import com.inkvault.pen.FirmwareUpdateState
import com.inkvault.pen.PasswordOpState
import com.inkvault.pen.PenConnState
import com.inkvault.ui.theme.monoData
import com.inkvault.ui.theme.monoEyebrow

/**
 * Settings (design-system §5/§9): Newsreader title, monoEyebrow section labels, controls grouped in
 * surface cards. Dropdowns render as a value+chevron row (Teal value), each revealing only its
 * contextual field. Every choice persists via DataStore and takes effect on the next export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: InkViewModel, onBack: () -> Unit, onOpenCaptureLab: () -> Unit = {}) {
    val context = LocalContext.current
    val method by vm.syncMethod.collectAsStateWithLifecycle()
    val folderUri by vm.localFolderUri.collectAsStateWithLifecycle()
    val endpoint by vm.tailscaleEndpoint.collectAsStateWithLifecycle()
    val translateEndpoint by vm.translateEndpoint.collectAsStateWithLifecycle()
    val translateModel by vm.translateModel.collectAsStateWithLifecycle()
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val penState by vm.penState.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setLocalFolderUri(uri.toString())
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onBack) { Text("Back") }
            }
        }

        item { SectionLabel("Appearance") }
        item {
            SettingsCard {
                DropdownRow(
                    title = "Theme",
                    desc = "System, or force light / dark",
                    current = theme.label,
                    options = ThemeMode.entries,
                    optionLabel = { it.label },
                    onPick = vm::setThemeMode,
                )
            }
        }

        item { SectionLabel("Sync") }
        item {
            SettingsCard {
                DropdownRow(
                    title = "Sync method",
                    desc = "Where exports are delivered",
                    current = method.label,
                    options = SyncMethod.entries,
                    optionLabel = { it.label },
                    onPick = vm::setSyncMethod,
                )
            }
        }
        // Contextual field for the selected method only.
        item {
            when (method) {
                SyncMethod.LOCAL_FOLDER -> InlineField("FOLDER", folderUri.ifEmpty { "not chosen" }) {
                    Button(onClick = { pickFolder.launch(null) }) { Text("Choose…") }
                }
                SyncMethod.TAILSCALE_PUSH -> Column(Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = vm::setTailscaleEndpoint,
                        label = { Text("NAS endpoint URL") },
                        placeholder = { Text("https://<truenas-tailscale-ip>:<port>/ingest") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SyncMethod.LOCAL_ONLY -> Text(
                    "Exports stay on this device (app storage).",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
        }

        item { SectionLabel("Translation") }
        item {
            SettingsCard {
                Text(
                    "Best-quality translation runs on your own GPU box (a translation LLM over your tailnet — " +
                        "one model, every language, nothing leaves your network). Leave blank to use the " +
                        "on-device offline translator. See tools/TRANSLATION_SETUP.md.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = translateEndpoint,
                    onValueChange = vm::setTranslateEndpoint,
                    label = { Text("Translation endpoint (OpenAI-compatible)") },
                    placeholder = { Text("http://ocr-host.lan:11434") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = translateModel,
                    onValueChange = vm::setTranslateModel,
                    label = { Text("Translation model") },
                    placeholder = { Text("eurollm  (or tower-plus, etc.)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { SectionLabel("Pen security") }
        item { PenSecurityCard(vm, penState) }

        item { SectionLabel("Firmware") }
        item { FirmwareCard(vm, penState) }

        item { SectionLabel("Calendar") }
        item { SettingsCard { CalendarSettingsCard(vm) } }

        item { SectionLabel("Page action icons") }
        item { SettingsCard { ActionZoneSettingsCard(vm) } }

        item { SectionLabel("Capture lab") }
        item {
            SettingsCard {
                Column(Modifier.padding(vertical = 14.dp)) {
                    Text("Capture lab", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Measure the coordinate scale, record raw Ncode data, or capture planner reference points from the pen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenCaptureLab, modifier = Modifier.padding(top = 10.dp)) { Text("Open capture lab") }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Pen unlock-password management. The password is entered once at the unlock prompt and stored
 * encrypted for silent auto-unlock; here the user can change it or turn it off. Both talk to the
 * pen over BLE, so they're only available while a pen is connected.
 */
@Composable
private fun PenSecurityCard(vm: InkViewModel, penState: PenConnState) {
    val cs = MaterialTheme.colorScheme
    val connected = penState is PenConnState.Connected
    var showChange by remember { mutableStateOf(false) }
    var showDisable by remember { mutableStateOf(false) }

    val rememberOn by vm.rememberPassword.collectAsStateWithLifecycle()

    SettingsCard {
        Column(Modifier.padding(vertical = 14.dp)) {
            // The remember toggle: off (default) = ask every connect; on = save encrypted for 30 days.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Remember password 30 days", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (rememberOn) "Saved encrypted on this device — auto-unlocks for 30 days."
                        else "Off — you'll be asked for the password each time the pen connects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Switch(checked = rememberOn, onCheckedChange = vm::setRememberPassword)
            }

            Spacer(Modifier.height(8.dp))
            Text("Unlock password", style = MaterialTheme.typography.titleMedium)
            Text(
                "Change or remove the password stored on the pen itself.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            if (connected) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { showChange = true }) { Text("Change") }
                    OutlinedButton(onClick = { showDisable = true }) { Text("Disable") }
                }
            } else {
                Text(
                    "Connect your pen to change or remove its password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    if (showChange) {
        PasswordOpDialog(
            title = "Change pen password",
            fields = listOf("Current password", "New password", "Confirm new password"),
            passwordOp = vm.passwordOp,
            onAcknowledge = vm::acknowledgePasswordOp,
            onDismiss = { showChange = false },
            validate = { f -> when {
                f[1].length < 4 -> "New password must be at least 4 digits."
                f[1] != f[2] -> "New passwords don't match."
                else -> null
            } },
            onSubmit = { f -> vm.changePenPassword(f[0], f[1]) },
        )
    }
    if (showDisable) {
        PasswordOpDialog(
            title = "Disable pen password",
            fields = listOf("Current password"),
            passwordOp = vm.passwordOp,
            onAcknowledge = vm::acknowledgePasswordOp,
            onDismiss = { showDisable = false },
            validate = { f -> if (f[0].isEmpty()) "Enter the current password." else null },
            onSubmit = { f -> vm.disablePenPassword(f[0]) },
        )
    }
}

/**
 * Firmware: shows the connected pen's version and offers a deliberate "update from file" flash.
 * There is intentionally no online "check for updates" — NeoLAB ships firmware through its own app
 * and there's no open update API; a bad image bricks the pen. So updating is a user-driven action
 * with an explicit warning, and you supply the official image file yourself.
 */
@Composable
private fun FirmwareCard(vm: InkViewModel, penState: PenConnState) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val connected = penState is PenConnState.Connected
    val version by vm.firmwareVersion.collectAsStateWithLifecycle()
    val update by vm.firmwareUpdate.collectAsStateWithLifecycle()
    var pendingFile by remember { mutableStateOf<java.io.File?>(null) }

    val pickFirmware = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // Copy the chosen image into app cache so the SDK gets a real File path to flash.
            val dest = java.io.File(context.cacheDir, "pen_firmware.bin")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
            if (dest.length() > 0) pendingFile = dest
        }
    }

    SettingsCard {
        Column(Modifier.padding(vertical = 14.dp)) {
            InlineField("VERSION", if (connected) version ?: "reading…" else "connect pen to read") {}
            when (val u = update) {
                is FirmwareUpdateState.Working -> Text(
                    "Updating… ${u.percent}% — keep the pen powered and nearby.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                is FirmwareUpdateState.Done -> Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (u.success) "Update complete." else "Update failed — the pen kept its old firmware.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (u.success) cs.onSurfaceVariant else cs.error,
                    )
                    TextButton(onClick = vm::acknowledgeFirmwareUpdate) { Text("OK") }
                }
                FirmwareUpdateState.Idle -> if (connected) {
                    OutlinedButton(
                        onClick = { pickFirmware.launch(arrayOf("*/*")) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Update from file…") }
                } else {
                    Text(
                        "Connect your pen to read its firmware or update it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    pendingFile?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingFile = null },
            title = { Text("Flash firmware?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "This writes \"${file.name}\" to the pen. Use only an official NeoLAB image for " +
                        "your exact model — a wrong or corrupt file can permanently brick the pen. " +
                        "Keep the pen charged and close to the phone until it finishes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = { vm.updateFirmware(file); pendingFile = null }) { Text("Flash") }
            },
            dismissButton = { TextButton(onClick = { pendingFile = null }) { Text("Cancel") } },
        )
    }
}

/**
 * A small dialog of masked numeric password [fields] that runs one pen [onSubmit] and reflects the
 * [passwordOp] result: closes on success, shows the pen's rejection inline on failure.
 */
@Composable
private fun PasswordOpDialog(
    title: String,
    fields: List<String>,
    passwordOp: kotlinx.coroutines.flow.StateFlow<PasswordOpState>,
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit,
    validate: (List<String>) -> String?,
    onSubmit: (List<String>) -> Unit,
) {
    val values = remember { mutableStateListOf(*Array(fields.size) { "" }) }
    var error by remember { mutableStateOf<String?>(null) }
    val op by passwordOp.collectAsStateWithLifecycle()
    val working = op is PasswordOpState.Working

    // React to the pen's answer: success closes the dialog, failure shows why and lets them retry.
    LaunchedEffect(op) {
        val done = op as? PasswordOpState.Done ?: return@LaunchedEffect
        if (done.success) onDismiss() else error = "Pen rejected the request. Check the current password."
        onAcknowledge()
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                fields.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { values[i] = it; error = null },
                        label = { Text(label) },
                        singleLine = true,
                        enabled = !working,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                if (working) Text("Talking to the pen…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            Button(
                enabled = !working,
                onClick = {
                    val v = values.toList()
                    val msg = validate(v)
                    if (msg != null) error = msg else onSubmit(v)
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = monoEyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) { content() }
    }
}

/** A settings row whose control is a value+chevron dropdown anchor (Teal value), per §9. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownRow(
    title: String,
    desc: String,
    current: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Row(
                Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(current, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = cs.primary)
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onPick(option); expanded = false },
                )
            }
        }
    }
}

/** Inline contextual field: a mono key + mono value, with an optional trailing control (§9). */
@Composable
private fun InlineField(key: String, value: String, trailing: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(key, style = monoEyebrow, color = cs.onSurfaceVariant)
            Text(
                value,
                style = monoData,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}
