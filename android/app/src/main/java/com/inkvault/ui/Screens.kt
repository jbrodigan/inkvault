package com.inkvault.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.print.PrintHelper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvault.R
import com.inkvault.audio.RecordingController
import com.inkvault.audio.firstStrokeDuring
import com.inkvault.audio.isStrokeSpoken
import com.inkvault.audio.markerStrokes
import com.inkvault.audio.recordingForStroke
import com.inkvault.data.Point
import com.inkvault.data.RecordingEntity
import com.inkvault.data.StrokeEntity
import com.inkvault.health.ConnectionDiagnostic
import com.inkvault.pen.BatteryStatus
import com.inkvault.pen.PenConnState
import com.inkvault.share.PageRender
import com.inkvault.ui.theme.InkGradientStops
import com.inkvault.ui.theme.InkText
import com.inkvault.pen.BatteryOptimization
import com.inkvault.ui.theme.LiveGreen
import com.inkvault.ui.theme.NavyDeep
import com.inkvault.ui.theme.InkTokens
import com.inkvault.ui.theme.FabShape
import com.inkvault.ui.theme.glow
import com.inkvault.ui.theme.steelBorder
import com.inkvault.ui.theme.freehandPath
import com.inkvault.ui.theme.monoData
import com.inkvault.ui.theme.monoEyebrow
import com.inkvault.ui.theme.ncodeDotGrid

private enum class Tab { PENS, LIBRARY, ACTIVITY }

/**
 * Root shell for the "Ink & Ncode" UI (design-system §10): a bottom-nav scaffold over Pens /
 * Library / Activity, plus full-screen takeovers for a single page, scanning, and settings. The
 * bold lives in one place — teal ink on the Ncode dot-grid — everything else stays quiet.
 */
@Composable
fun InkApp(vm: InkViewModel) {
    // The vault "unlocks" on launch, then reveals the app. rememberSaveable so it plays once per
    // process, not on every recomposition / config change.
    var splashDone by rememberSaveable { mutableStateOf(false) }
    if (!splashDone) { VaultSplash(onDone = { splashDone = true }); return }

    val notebooks by vm.notebooks.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val strokes by vm.strokes.collectAsStateWithLifecycle()
    val pen by vm.penState.collectAsStateWithLifecycle()
    val pageId by vm.selectedPageId.collectAsStateWithLifecycle()
    val notebookId by vm.selectedNotebookId.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(Tab.PENS) }
    var showSettings by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var showLive by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var showCaptureLab by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showExitPrompt by remember { mutableStateOf(false) }

    // The OS Back button mirrors the in-app Back: pop overlays → page → notebook → back to the Pens
    // tab. At the root it asks before leaving instead of dropping straight to the home screen.
    BackHandler {
        when {
            showExitPrompt -> showExitPrompt = false
            showSearch -> showSearch = false
            showCaptureLab -> showCaptureLab = false
            showSettings -> showSettings = false
            showScan -> showScan = false
            showLive -> showLive = false
            showDiag -> showDiag = false
            pageId != null || notebookId != null -> vm.back()
            tab != Tab.PENS -> tab = Tab.PENS
            else -> showExitPrompt = true
        }
    }

    // A locked pen can ask for its password from any screen — surface it globally.
    if (pen is PenConnState.PasswordRequired) PasswordDialog(onSubmit = vm::submitPassword)

    // A notebook the app hasn't set up prompts once (from any screen) for its type + a label.
    val notebookSetup by vm.notebookNeedingSetup.collectAsStateWithLifecycle()
    notebookSetup?.let { nb ->
        NotebookSetupDialog(
            notebook = nb,
            defaultTypeId = vm.resolvedTypeId(nb.book),
            onSave = { typeId, label -> vm.setUpNotebook(nb.id, nb.book, typeId, label) },
            onSkip = { vm.skipNotebookSetup(nb.id) },
        )
    }
    // First-connect nudge (FIX #2): when a pen connects while the app isn't battery-exempt, prompt
    // once to allow background capture. Dismissible + persisted, so it asks only once; the Settings
    // "Capture reliability" card is the permanent path.
    val nudgeCtx = LocalContext.current
    val bgNudgeDismissed by vm.bgCaptureNudgeDismissed.collectAsStateWithLifecycle()
    var showBgNudge by remember { mutableStateOf(false) }
    LaunchedEffect(pen, bgNudgeDismissed) {
        if (pen is PenConnState.Connected && !bgNudgeDismissed && !BatteryOptimization.isIgnoring(nudgeCtx)) {
            showBgNudge = true
        }
    }
    if (showBgNudge) {
        BackgroundCaptureNudgeDialog(
            onAllow = { showBgNudge = false; vm.dismissBgCaptureNudge(); BatteryOptimization.openSettings(nudgeCtx) },
            onDismiss = { showBgNudge = false; vm.dismissBgCaptureNudge() },
        )
    }
    if (showExitPrompt) {
        val activity = LocalContext.current.activity()
        ExitConfirmDialog(onDismiss = { showExitPrompt = false }, onConfirm = { activity?.finish() })
    }

    if (showSearch) { SearchScreen(vm, onBack = { showSearch = false }); return }
    if (showCaptureLab) { CaptureLabScreen(vm, onBack = { showCaptureLab = false }); return }
    if (showSettings) { SettingsScreen(vm, onBack = { showSettings = false }, onOpenCaptureLab = { showSettings = false; showCaptureLab = true }); return }
    if (showScan) { ScanScreen(vm, onBack = { showScan = false }); return }
    if (showLive) { LiveCaptureScreen(vm, onBack = { showLive = false }); return }
    if (showDiag) { ConnectionDiagnosticScreen(vm, onBack = { showDiag = false }); return }

    // Opening a page from the library reads as a container transform — the page grows in over the
    // grid and shrinks back on return (design-system §8). Tab changes (inner) use shared-axis X.
    AnimatedContent(
        targetState = pageId != null,
        transitionSpec = { containerTransform(opening = targetState) },
        label = "page",
    ) { onPage ->
        if (onPage) {
            PageDetail(strokes, vm)
        } else {
            Scaffold(
                bottomBar = { InkBottomNav(tab) { tab = it } },
                floatingActionButton = {
                    if (tab == Tab.PENS) {
                        // Connected → start a live capture; otherwise → find a pen. (Mock #1: "+" FAB.)
                        // Labeled (extended) so the teal button says what it does, not just "+".
                        val connected = pen is PenConnState.Connected
                        val label = if (connected) "New capture" else "Find a pen"
                        // v3 motion (mockup §motion): a gentle 3.8s vertical float + a 7s gradient
                        // pan so the FAB reads "alive". Both go static under the system "remove
                        // animations" setting (Compose infinite transitions ignore it on their own).
                        val reduced = rememberReducedMotion()
                        val fab = rememberInfiniteTransition(label = "fab")
                        val floatY by fab.animateFloat(
                            0f, if (reduced) 0f else -5f,
                            infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "floatFab",
                        )
                        val pan by fab.animateFloat(
                            0f, if (reduced) 0f else 1f,
                            infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "gradPan",
                        )
                        // Gradient rounded-square FAB (mockup): transparent container + a panning
                        // gradient brush, white content. span > FAB so the gradient slides visibly.
                        val span = 320f
                        val fabBrush = Brush.linearGradient(
                            InkGradientStops,
                            start = Offset(-span + pan * span, 0f),
                            end = Offset(pan * span, span),
                        )
                        ExtendedFloatingActionButton(
                            onClick = { if (connected) showLive = true else showScan = true },
                            shape = FabShape,
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                            modifier = Modifier
                                .graphicsLayer { translationY = floatY.dp.toPx() }
                                .glow(FabShape)
                                .background(fabBrush, FabShape),
                            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            text = { Text(label) },
                        )
                    }
                },
            ) { inner ->
                Box(Modifier.padding(inner)) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { sharedAxisX(forward = targetState.ordinal > initialState.ordinal) },
                        label = "tab",
                    ) { t ->
                        when (t) {
                            Tab.PENS -> PensHome(
                                vm, pen, notebooks,
                                onScan = { showScan = true },
                                onSettings = { showSettings = true },
                                onSearch = { showSearch = true },
                                onCheckConnection = { showDiag = true },
                                onOpenActivity = { tab = Tab.ACTIVITY },
                                // Open the notebook AND switch to the Library tab so its pages show
                                // (selecting alone left the user on the Pens screen — looked dead).
                                onOpenNotebook = { vm.openNotebook(it); tab = Tab.LIBRARY },
                            )
                            Tab.LIBRARY -> LibraryScreen(vm, notebooks, pages)
                            Tab.ACTIVITY -> ActivityScreen(vm)
                        }
                    }
                }
            }
        }
    }
}

/** Material shared-axis X: forward slides content left→right through a fade; back reverses it. */
private fun sharedAxisX(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    return ContentTransform(
        targetContentEnter = slideInHorizontally(tween(250)) { it / 10 * dir } + fadeIn(tween(250)),
        initialContentExit = slideOutHorizontally(tween(200)) { -it / 10 * dir } + fadeOut(tween(150)),
        sizeTransform = null,
    )
}

/** Container-transform-like grow/shrink for page open/close (design-system §8). */
private fun containerTransform(opening: Boolean): ContentTransform =
    if (opening) {
        ContentTransform(
            targetContentEnter = scaleIn(animationSpec = tween(300), initialScale = 0.86f) + fadeIn(tween(220)),
            initialContentExit = fadeOut(tween(160)),
            sizeTransform = null,
        )
    } else {
        ContentTransform(
            targetContentEnter = fadeIn(tween(200)),
            initialContentExit = scaleOut(animationSpec = tween(300), targetScale = 0.86f) + fadeOut(tween(220)),
            sizeTransform = null,
        )
    }

@Composable
private fun InkBottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(tonalElevation = 2.dp) {
        val items = listOf(
            Triple(Tab.PENS, Icons.Outlined.Edit, "Pens"),
            Triple(Tab.LIBRARY, Icons.Outlined.GridView, "Library"),
            Triple(Tab.ACTIVITY, Icons.Outlined.History, "Activity"),
        )
        items.forEach { (t, icon, label) ->
            NavigationBarItem(
                selected = selected == t,
                onClick = { onSelect(t) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/** App bar: Newsreader title over an uppercase mono sub-label, with optional trailing actions. */
@Composable
private fun InkAppBar(title: String, sub: String? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (sub != null) {
                Text(
                    sub.uppercase(),
                    style = monoEyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        style = monoEyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 10.dp),
    )
}

/** The InkVault lockup (mock §appbar): the vault mark + live "Ink"/gradient-"Vault" wordmark. */
@Composable
private fun BrandWordmark(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(if (dark) R.drawable.brand_logo_dark else R.drawable.brand_logo_light),
            contentDescription = null,
            modifier = Modifier.height(44.dp),
        )
        Spacer(Modifier.width(11.dp))
        WordmarkText(fontSize = 32)
    }
}

// ---- Pens / Home (mock #1) ----

@Composable
private fun PensHome(
    vm: InkViewModel,
    pen: PenConnState,
    notebooks: List<com.inkvault.data.NotebookEntity>,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onCheckConnection: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenNotebook: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { BrandWordmark(Modifier.padding(top = 14.dp, bottom = 2.dp)) }
        item {
            InkAppBar(title = "Pens", sub = if (pen is PenConnState.Connected) "1 connected" else "no pen") {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search handwriting")
                }
                IconButton(onClick = onCheckConnection) {
                    Icon(Icons.Outlined.Troubleshoot, contentDescription = "Check connection")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }
        item { PenStatusCard(vm, pen, onScan) }
        item { SyncStatusCard(vm, onOpenActivity) }
        item { Eyebrow("Recent notebooks") }
        if (notebooks.isEmpty()) {
            item { QuietLine("Nothing yet — write on Ncode paper with the pen connected.") }
        } else {
            items(notebooks.chunked(2)) { row ->
                ThumbRow(row.map { it.id to it.title }) { id, label, m ->
                    NotebookThumb(id, label, vm, m) { onOpenNotebook(id) }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) } // clear the FAB
    }
}

/** Pen-status card (design-system §9): nib badge + name + mono status tag + battery. */
@Composable
private fun PenStatusCard(vm: InkViewModel, pen: PenConnState, onScan: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val battery by vm.battery.collectAsStateWithLifecycle()
    data class Look(val live: Boolean, val name: String, val tag: String, val tagColor: Color, val sub: String, val onTap: (() -> Unit)?)
    val l = when (pen) {
        is PenConnState.Connected -> Look(true, pen.penName.ifBlank { "Smartpen" }, "CONNECTED", cs.primary, "Receiving ink", null)
        is PenConnState.Connecting -> Look(false, "Smartpen", "CONNECTING…", cs.secondary, "Hold the pen on and nearby", null)
        is PenConnState.Reconnecting -> Look(false, "Smartpen", "RECONNECTING (${pen.attempt})", cs.tertiary, "Lost the link — retrying", null)
        is PenConnState.PasswordRequired -> Look(false, "Smartpen", "LOCKED", cs.tertiary, "Enter the pen password", null)
        is PenConnState.BondedElsewhere -> Look(false, "Smartpen", "PAIRED ELSEWHERE", cs.error, "Release it to take over", { vm.takeOver(pen.mac) })
        is PenConnState.Disconnected -> Look(false, "No pen", "TAP TO CONNECT", cs.secondary, "Find a pen over Bluetooth", onScan)
    }
    Card(
        Modifier.fillMaxWidth().padding(top = 4.dp).let { if (l.onTap != null) it.clickable { l.onTap!!() } else it }
            .steelBorder(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                NibBadge(live = l.live)
                Spacer(Modifier.size(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.name, style = MaterialTheme.typography.titleMedium)
                    Text(l.tag, style = monoData, color = l.tagColor)
                    Text(l.sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                val bat = battery
                if (l.live && bat != null) BatteryBadge(bat)
                else Text(if (l.live) "•" else "—", style = monoData, color = cs.onSurfaceVariant)
            }
            val context = LocalContext.current
            val penName = (pen as? PenConnState.Connected)?.penName
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Opens the manufacturer's official page for the detected pen (user-initiated link).
                if (penName != null) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.inkvault.pen.PenLinks.officialUrl(penName))),
                            )
                        }
                    }) { Text("Official page ↗") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                // Disconnect drops the link + stops auto-reconnect; only selectable once connected.
                TextButton(onClick = { vm.disconnect() }, enabled = pen is PenConnState.Connected) {
                    Text("Disconnect")
                }
            }
        }
    }
}

/** One glanceable backup state from the outbox: "All backed up" or "N pending" (tap → Activity). */
@Composable
private fun SyncStatusCard(vm: InkViewModel, onOpen: () -> Unit) {
    val pending by vm.pendingUploads.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val backed = pending == 0
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (backed) Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = if (backed) cs.primary else cs.tertiary,
            )
            Spacer(Modifier.size(13.dp))
            Column(Modifier.weight(1f)) {
                Text(if (backed) "All backed up" else "$pending pending", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (backed) "Every stroke saved on device & synced" else "Tap to see what's still queued",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Text(if (backed) "✓" else "$pending", style = monoData, color = if (backed) cs.primary else cs.tertiary)
        }
    }
}

/**
 * "Check connection" (Phase A): walks the BLE chain and tells you the first thing to fix. Gathers
 * a [ConnectionDiagnostic.Probe] from Android (Bluetooth + permissions) and app state (pen state,
 * live ink signals), then renders each step pass/fail/skipped with the fix for the first failure.
 */
@Composable
private fun ConnectionDiagnosticScreen(vm: InkViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val pen by vm.penState.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    var steps by remember { mutableStateOf(ConnectionDiagnostic.run(buildConnectionProbe(context, pen, vm))) }
    fun recheck() { steps = ConnectionDiagnostic.run(buildConnectionProbe(context, pen, vm)) }
    // Re-evaluate live so writing a stroke (or fixing Bluetooth) flips its step while the screen is open.
    LaunchedEffect(pen) { while (true) { recheck(); kotlinx.coroutines.delay(1_200) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) { Text("Back") }
            Text("CHECK CONNECTION", style = monoEyebrow, color = cs.onSurfaceVariant)
            Button(onClick = { recheck() }) { Text("Re-check") }
        }
        val firstFail = ConnectionDiagnostic.firstFailure(steps)
        LazyColumn(Modifier.fillMaxSize()) {
            items(steps) { step ->
                val isFirstFail = step == firstFail
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isFirstFail) 3.dp else 1.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val (glyph, tint) = when (step.status) {
                            ConnectionDiagnostic.Status.PASS -> "✓" to cs.primary
                            ConnectionDiagnostic.Status.FAIL -> "✕" to cs.error
                            ConnectionDiagnostic.Status.SKIPPED -> "·" to cs.onSurfaceVariant
                        }
                        Text(glyph, style = MaterialTheme.typography.titleMedium, color = tint)
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(step.name, style = MaterialTheme.typography.titleSmall)
                            if (step.status != ConnectionDiagnostic.Status.PASS) {
                                Text(step.detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Gather the diagnostic probe from Android + app state. */
private fun buildConnectionProbe(
    context: android.content.Context,
    pen: PenConnState,
    vm: InkViewModel,
): ConnectionDiagnostic.Probe {
    fun granted(p: String) = context.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val s = Build.VERSION.SDK_INT
    val bluetoothOn =
        (context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)
            ?.adapter?.isEnabled == true
    val scanPerm = if (s >= Build.VERSION_CODES.S) granted(android.Manifest.permission.BLUETOOTH_SCAN)
        else granted(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val connectPerm = if (s >= Build.VERSION_CODES.S) granted(android.Manifest.permission.BLUETOOTH_CONNECT) else true
    val connected = pen is PenConnState.Connected
    val receiving = vm.receivingInk()
    return ConnectionDiagnostic.Probe(
        bluetoothOn = bluetoothOn,
        scanPermission = scanPerm,
        connectPermission = connectPerm,
        penPoweredOrConnecting = pen !is PenConnState.Disconnected,
        gattConnected = connected,
        authorized = connected, // our flow only emits Connected after the password is accepted
        receivingDots = receiving,
        paperRecognized = receiving && vm.paperRecognized(),
    )
}

/** A battery glyph with a proportional fill + percent; optionally the charge time to full. */
@Composable
private fun BatteryBadge(status: BatteryStatus, showEta: Boolean = false, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val pct = status.percent.coerceIn(0, 100)
    val fill = when {
        pct <= 15 -> cs.error
        pct <= 35 -> cs.tertiary
        else -> cs.primary
    }
    val outline = cs.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 26.dp, height = 13.dp)) {
            val nub = 2.dp.toPx()
            val bodyW = size.width - nub
            val sw = 1.5.dp.toPx()
            val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            val rIn = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            drawRoundRect(outline, Offset(sw / 2, sw / 2), Size(bodyW - sw, size.height - sw), r, style = Stroke(sw))
            drawRoundRect(outline, Offset(bodyW, size.height * 0.3f), Size(nub, size.height * 0.4f), rIn)
            val pad = sw + 1.dp.toPx()
            val innerW = (bodyW - 2 * pad).coerceAtLeast(0f)
            drawRoundRect(fill, Offset(pad, pad), Size(innerW * (pct / 100f), size.height - 2 * pad), rIn)
        }
        Spacer(Modifier.width(6.dp))
        Text("$pct%", style = monoData, color = cs.onSurface)
        val eta = status.chargeEtaMinutes
        if (showEta && eta != null) {
            Spacer(Modifier.width(6.dp))
            Text("· ${formatEta(eta)} to full", style = monoData, color = cs.primary)
        }
    }
}

private fun formatEta(min: Int): String = if (min < 60) "${min}m" else "${min / 60}h ${min % 60}m"

/** 38dp nib badge: primaryContainer fill, 9dp center dot — Brass when live, Slate when idle (§9). */
@Composable
private fun NibBadge(live: Boolean) {
    val cs = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val t = rememberInfiniteTransition(label = "nib")
    // Live → a ring pulses out from the nib dot (mockup .live .pulse, ~1.9s); static otherwise.
    val ringScale by t.animateFloat(
        1f, if (live && !reduced) 2.6f else 1f,
        infiniteRepeatable(tween(1900), RepeatMode.Restart), label = "nibRing",
    )
    val ringAlpha by t.animateFloat(
        if (live && !reduced) 0.5f else 0f, 0f,
        infiniteRepeatable(tween(1900), RepeatMode.Restart), label = "nibRingAlpha",
    )
    val dotColor = if (live) cs.tertiary else cs.secondary
    Box(
        Modifier.size(38.dp).background(cs.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (live) {
            Box(
                Modifier.size(9.dp)
                    .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                    .background(dotColor, CircleShape),
            )
        }
        Box(Modifier.size(9.dp).background(dotColor, CircleShape))
    }
}

// ---- Library (mock #3) ----

@Composable
private fun LibraryScreen(
    vm: InkViewModel,
    notebooks: List<com.inkvault.data.NotebookEntity>,
    pages: List<com.inkvault.data.PageEntity>,
) {
    val notebookId by vm.selectedNotebookId.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val selectedTag by vm.selectedTagState.collectAsStateWithLifecycle()
    val taggedPages by vm.taggedPages.collectAsStateWithLifecycle()
    val inNotebook = notebookId != null
    val filtering = !inNotebook && selectedTag != null
    Column(Modifier.fillMaxSize()) {
        BrandWordmark(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            InkAppBar(
                title = when { inNotebook -> "Notebook"; filtering -> "#$selectedTag"; else -> "Library" },
                sub = when {
                    inNotebook -> "${pages.size} pages"
                    filtering -> "${taggedPages.size} tagged pages"
                    else -> "${notebooks.size} notebooks"
                },
            ) {
                if (inNotebook) Button(onClick = vm::back) { Text("Back") }
            }
        }
        // Tag filter bar (Library root only): tap a tag to show its pages flat; tap again to clear.
        if (!inNotebook && allTags.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allTags.forEach { tag ->
                    val sel = tag == selectedTag
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { vm.selectTag(if (sel) null else tag) },
                    ) {
                        Text(
                            "#$tag",
                            style = monoData,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
        // Drilling into a notebook (and back) uses the same shared-axis X as the tabs.
        AnimatedContent(
            targetState = notebookId,
            transitionSpec = { sharedAxisX(forward = targetState != null) },
            label = "drill",
        ) { nb ->
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (nb == null && filtering) {
                    if (taggedPages.isEmpty()) item { QuietLine("No pages with this tag.") }
                    else items(taggedPages.chunked(2)) { row ->
                        ThumbRow(row.map { it.id to "Page ${it.page}" }) { id, label, m ->
                            PageThumb(id, label, vm, m) { vm.openPage(id) }
                        }
                    }
                } else if (nb == null) {
                    if (notebooks.isEmpty()) item { QuietLine("No notebooks yet.") }
                    else items(notebooks.chunked(2)) { row ->
                        ThumbRow(row.map { it.id to it.title }) { id, label, m ->
                            NotebookThumb(id, label, vm, m) { vm.openNotebook(id) }
                        }
                    }
                } else {
                    if (pages.isEmpty()) item { QuietLine("No pages in this notebook yet.") }
                    else items(pages.chunked(2)) { row ->
                        ThumbRow(row.map { it.id to "Page ${it.page}" }) { id, label, m ->
                            PageThumb(id, label, vm, m) { vm.openPage(id) }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/** A row of up to two dot-grid thumbnails (design-system §9 library thumbnail). */
@Composable
private fun ThumbRow(
    items: List<Pair<String, String>>,
    thumb: @Composable (id: String, label: String, modifier: Modifier) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (id, label) -> thumb(id, label, Modifier.weight(1f)) }
        if (items.size == 1) Spacer(Modifier.weight(1f)) // keep a lone thumbnail left-aligned
    }
}

/** Page thumbnail showing the page's real ink (so the library isn't a wall of blank cards). */
@Composable
private fun PageThumb(pageId: String, label: String, vm: InkViewModel, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val strokes by remember(pageId) { vm.pageStrokes(pageId) }.collectAsStateWithLifecycle(emptyList())
    val hasAudio by remember(pageId) { vm.pageHasAudio(pageId) }.collectAsStateWithLifecycle(false)
    ThumbBody(label, strokes, vm, hasAudio, modifier, onOpen)
}

/** Notebook thumbnail: a cover drawn from the notebook's most-recently-inked page. */
@Composable
private fun NotebookThumb(notebookId: String, label: String, vm: InkViewModel, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val strokes by remember(notebookId) { vm.notebookCoverStrokes(notebookId) }.collectAsStateWithLifecycle(emptyList())
    val hasAudio by remember(notebookId) { vm.notebookHasAudio(notebookId) }.collectAsStateWithLifecycle(false)
    ThumbBody(label, strokes, vm, hasAudio, modifier, onOpen)
}

@Composable
private fun ThumbBody(
    label: String,
    strokes: List<StrokeEntity>,
    vm: InkViewModel,
    hasAudio: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier.aspectRatio(1.05f).clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.fillMaxSize().ncodeDotGrid(InkTokens.dotColor(cs.onBackground), spacing = 13.dp)) {
            if (strokes.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize()) {
                    drawStrokes(strokes, vm::strokesFlowOf, base = cs.onSurface, brandInk = cs.onSurface)
                }
            }
            // Soundwave badge → this page/notebook has voice notes tied to it.
            if (hasAudio) {
                Surface(
                    shape = CircleShape,
                    color = cs.primaryContainer,
                    shadowElevation = 1.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = "Has voice notes",
                        tint = cs.primary,
                        modifier = Modifier.padding(4.dp).size(15.dp),
                    )
                }
            }
            Text(
                label,
                style = monoData,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
    }
}

// ---- Activity (sync/export status) ----

@Composable
private fun ActivityScreen(vm: InkViewModel) {
    val pending by vm.pendingUploads.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { BrandWordmark(Modifier.padding(top = 14.dp, bottom = 2.dp)) }
        item { InkAppBar(title = "Activity") }
        item { Eyebrow("Sync") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cs.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // While pages are still queued, the combination dial spins (the mockup loader).
                    if (pending > 0) {
                        DialSpinner(size = 30.dp)
                        Spacer(Modifier.width(13.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (pending == 0) "All strokes saved on device" else "Securing pages",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (pending > 0) {
                            Text("$pending pending · encrypting locally", style = monoData, color = cs.onSurfaceVariant)
                        }
                    }
                    Text(
                        if (pending == 0) "✓ synced" else "$pending",
                        style = monoData,
                        color = if (pending == 0) cs.primary else cs.tertiary,
                    )
                }
            }
        }
    }
}

// ---- Page detail + ink (mock #4) ----

/**
 * Page detail (mock #4). Read-only by default; the Edit toggle reveals the floating toolbar and an
 * editable canvas. Editing is selection-based: tap strokes to toggle them, or drag a lasso to select
 * a region, then act on the whole selection (recolor / thicken / delete). Undo drops the last stroke.
 */
@Composable
private fun PageDetail(strokes: List<StrokeEntity>, vm: InkViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val exportStatus by vm.exportStatus.collectAsStateWithLifecycle()
    val pageId by vm.selectedPageId.collectAsStateWithLifecycle()
    val recordings by remember(pageId) { vm.recordingsFor(pageId) }.collectAsStateWithLifecycle(emptyList())
    val playingId by vm.playingRecordingId.collectAsStateWithLifecycle()
    val positionMs by vm.playbackPositionMs.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val notebookId by vm.selectedNotebookId.collectAsStateWithLifecycle()
    val backgrounds by vm.backgrounds.collectAsStateWithLifecycle()
    val bgUri = notebookId?.let { backgrounds[it] }
    val background = remember(bgUri) { bgUri?.let { loadImageBitmap(context, it) } }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val nb = notebookId
        if (uri != null && nb != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setBackground(nb, uri.toString())
        }
    }
    var editing by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var textView by remember { mutableStateOf(false) }
    val transcript by vm.currentTranscript.collectAsStateWithLifecycle()
    val ocrEnabled by vm.onDeviceOcrEnabled.collectAsStateWithLifecycle()
    val ocrAllowed = vm.onDeviceOcrAvailable && ocrEnabled
    var lassoMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedRecId by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<RecordingEntity?>(null) }
    var showAddEvent by remember { mutableStateOf(false) }

    // Stop audio whenever we leave the page or drop out of listen mode.
    DisposableEffect(Unit) { onDispose { vm.stopPlayback() } }

    // The recording the scrubber/highlight follow: the loaded one, else the picked one, else newest.
    val active = recordings.firstOrNull { it.id == playingId }
        ?: recordings.firstOrNull { it.id == selectedRecId }
        ?: recordings.lastOrNull()
    val loaded = active != null && active.id == playingId        // player is on this note (playing OR paused)
    val playingActive = loaded && isPlaying                       // actively playing this note

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = vm::back) { Text("Back") }
                Text(
                    if (editing && selected.isNotEmpty()) "${selected.size} SELECTED" else "${strokes.size} STROKES",
                    style = monoEyebrow,
                    color = cs.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Listen: replay the page's voice notes synced to the ink (only if any exist).
                    if (recordings.isNotEmpty()) {
                        IconButton(onClick = {
                            listening = !listening
                            if (listening) { editing = false; textView = false } else vm.stopPlayback()
                        }) {
                            Icon(
                                Icons.Outlined.Headphones,
                                contentDescription = if (listening) "Close listen" else "Listen with the ink",
                                tint = if (listening) cs.tertiary else cs.onSurfaceVariant,
                            )
                        }
                    }
                    // Typed-text view: read the page's OCR transcript in a printed font (the ink stays
                    // the source of truth — this is a parallel, non-destructive view).
                    IconButton(onClick = {
                        textView = !textView
                        if (textView) { editing = false; selected = emptySet(); listening = false; vm.stopPlayback() }
                    }) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = if (textView) "Show ink" else "Show typed text",
                            tint = if (textView) cs.tertiary else cs.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        editing = !editing; if (!editing) selected = emptySet() else { listening = false; textView = false; vm.stopPlayback() }
                    }) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = if (editing) "Done editing" else "Edit",
                            tint = if (editing) cs.tertiary else cs.onSurfaceVariant,
                        )
                    }
                    // Secondary actions collapsed into one overflow (⋯) to keep the bar uncluttered.
                    Box {
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More actions", tint = cs.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(
                                text = { Text("Add to calendar") },
                                leadingIcon = { Icon(Icons.Outlined.EditCalendar, contentDescription = null) },
                                onClick = { more = false; showAddEvent = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Print") },
                                leadingIcon = { Icon(Icons.Outlined.Print, contentDescription = null) },
                                enabled = strokes.isNotEmpty(),
                                onClick = { more = false; printPage(context, "InkVault page", strokes, vm::strokesFlowOf) },
                            )
                            if (ocrAllowed) {
                                DropdownMenuItem(
                                    text = { Text("Transcribe on device") },
                                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                    enabled = strokes.isNotEmpty(),
                                    onClick = { more = false; vm.transcribeCurrentPageOnDevice() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Export now") },
                                leadingIcon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                                onClick = { more = false; vm.exportCurrentPage() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (bgUri == null) "Set notebook background…" else "Replace notebook background…") },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                enabled = notebookId != null,
                                onClick = { more = false; pickBackground.launch(arrayOf("image/*")) },
                            )
                            if (bgUri != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove background") },
                                    onClick = { more = false; notebookId?.let { vm.clearBackground(it) } },
                                )
                            }
                        }
                    }
                }
            }
            if (exportStatus != null) {
                Text(
                    exportStatus.orEmpty(),
                    style = monoData,
                    color = cs.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            pageId?.let { TagRow(it, vm) }
            val surfaceMod = Modifier.weight(1f).fillMaxWidth().padding(8.dp)
            when {
                textView -> {
                    val tr by vm.translation.collectAsStateWithLifecycle()
                    val translating by vm.translating.collectAsStateWithLifecycle()
                    val translateErr by vm.translateError.collectAsStateWithLifecycle()
                    PageTextView(
                        transcript = transcript,
                        onTranscribe = { vm.transcribeCurrentPageOnDevice() },
                        canTranscribe = ocrAllowed && strokes.isNotEmpty(),
                        translatorAvailable = vm.translatorAvailable,
                        defaultTarget = vm.deviceLanguage,
                        translationText = tr?.text,
                        translationOnDevice = tr?.onDevice == true,
                        translating = translating,
                        translateError = translateErr,
                        onTranslate = { source, target -> vm.translateCurrentPage(target, source) },
                        onShowOriginal = { vm.clearTranslation() },
                        modifier = surfaceMod,
                    )
                }
                editing -> EditableInkCanvas(
                    strokes, vm, lassoMode, selected,
                    onToggleStroke = { s -> selected = if (s.uuid in selected) selected - s.uuid else selected + s.uuid },
                    onLasso = { hit -> selected = hit.map { it.uuid }.toSet() },
                    modifier = surfaceMod,
                    background = background,
                )
                listening -> ListenCanvas(
                    strokes, vm, recordings, active, playingActive, positionMs,
                    onTapStroke = { s ->
                        // Tap-to-play (pencast): jump the audio to what was said while writing this stroke.
                        recordingForStroke(recordings, s)?.let { (rec, offset) ->
                            selectedRecId = rec.id; vm.playRecording(rec, offset)
                        }
                    },
                    // A marker carries its own offset → play that bookmark, not the recording's start.
                    onTapMarker = { rec, offsetMs -> selectedRecId = rec.id; vm.playRecording(rec, offsetMs) },
                    modifier = surfaceMod,
                    background = background,
                )
                else -> InkSurface(strokes, vm, surfaceMod, background = background)
            }
        }
        if (editing) {
            // The collapsed pickers rest on the first selected stroke's color/size (or defaults).
            val firstSel = strokes.firstOrNull { it.uuid in selected }
            EditToolbar(
                lassoMode = lassoMode,
                hasSelection = selected.isNotEmpty(),
                currentColor = firstSel?.color ?: 0,
                currentWidth = firstSel?.width ?: 1f,
                onToggleLasso = { lassoMode = !lassoMode },
                onRecolor = { c -> vm.recolorSelection(selected.toList(), c); selected = emptySet() },
                onResize = { w -> vm.resizeSelection(selected.toList(), w); selected = emptySet() },
                onDelete = { vm.deleteSelection(selected.toList()); selected = emptySet() },
                onUndo = { vm.undoEdit() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (listening && active != null) {
            ListenBar(
                recordings = recordings,
                active = active,
                loaded = loaded,
                playing = playingActive,
                positionMs = positionMs,
                onSelect = { rec -> selectedRecId = rec.id; vm.stopPlayback() },
                onPlayPause = {
                    when {
                        playingActive -> vm.pausePlayback()          // playing → pause in place
                        loaded -> vm.resumePlayback()                // paused → resume where we left off
                        else -> vm.playRecording(active, 0)          // stopped → play from start
                    }
                },
                onSeek = { ms -> if (loaded) vm.seekPlayback(ms) else vm.playRecording(active, ms) },
                onRename = { renaming = active },
                onDelete = { vm.deleteRecording(active) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    // Leaving no notes? drop out of listen mode.
    LaunchedEffect(recordings.isEmpty()) { if (recordings.isEmpty()) listening = false }
    renaming?.let { rec ->
        val idx = recordings.indexOfFirst { it.id == rec.id }
        RenameRecordingDialog(
            initial = rec.title.ifBlank { "Note ${idx + 1}" },
            onDismiss = { renaming = null },
            onConfirm = { name -> vm.renameRecording(rec.id, name); renaming = null },
        )
    }
    if (showAddEvent) AddEventDialog(vm, defaultTitle = "", onDismiss = { showAddEvent = false })
    val showOcrDisclosure by vm.showOcrDisclosure.collectAsStateWithLifecycle()
    if (showOcrDisclosure) {
        AlertDialog(
            onDismissRequest = { vm.dismissOcrDisclosure() },
            title = { Text("Transcribe on this device?") },
            text = {
                Text(
                    "On-device transcription uses Google's ML Kit handwriting recognition. Your " +
                        "handwriting is recognized on your device and is never uploaded. The first " +
                        "time, a one-time language model is downloaded from Google; after that no " +
                        "further download is needed. (Your default OCR path stays your own NAS/OCR host.)",
                )
            },
            confirmButton = { Button(onClick = { vm.confirmOcrDisclosure() }) { Text("Download & transcribe") } },
            dismissButton = { TextButton(onClick = { vm.dismissOcrDisclosure() }) { Text("Cancel") } },
        )
    }
}

/**
 * Editable canvas: renders ink (selected strokes highlighted brass + thicker) and captures gestures.
 * Lasso mode → drag draws a brass dashed loop and selects the strokes inside it; otherwise a tap
 * toggles the nearest stroke (within a small radius) in the selection.
 */
@Composable
private fun EditableInkCanvas(
    strokes: List<StrokeEntity>,
    vm: InkViewModel,
    lassoMode: Boolean,
    selected: Set<String>,
    onToggleStroke: (StrokeEntity) -> Unit,
    onLasso: (List<StrokeEntity>) -> Unit,
    modifier: Modifier = Modifier,
    background: ImageBitmap? = null,
) {
    val cs = MaterialTheme.colorScheme
    val lassoPts = remember { mutableStateListOf<Offset>() }
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        val gesture = if (lassoMode) {
            Modifier.pointerInput(strokes) {
                detectDragGestures(
                    onDragStart = { lassoPts.clear(); lassoPts.add(it) },
                    onDragEnd = {
                        val fit = inkFit(strokes, vm::strokesFlowOf, size.width.toFloat(), size.height.toFloat())
                        val poly = lassoPts.toList()
                        // Precision: select a stroke only when MOST of it is inside the loop, so
                        // circling one letter doesn't grab neighbours that merely dip a point in.
                        val hit = if (fit == null) emptyList() else strokes.filter { s ->
                            val pts = vm.strokesFlowOf(s)
                            if (pts.isEmpty()) return@filter false
                            val inside = pts.count { p ->
                                val sp = fit.map(p.x, p.y)
                                pointInPolygon(poly, sp.x, sp.y)
                            }
                            inside >= pts.size * LASSO_INSIDE_FRACTION
                        }
                        onLasso(hit)
                        lassoPts.clear()
                    },
                    onDragCancel = { lassoPts.clear() },
                ) { change, _ -> lassoPts.add(change.position) }
            }
        } else {
            Modifier.pointerInput(strokes) {
                detectTapGestures { tap ->
                    val fit = inkFit(strokes, vm::strokesFlowOf, size.width.toFloat(), size.height.toFloat())
                        ?: return@detectTapGestures
                    var best: StrokeEntity? = null
                    var bestSq = Float.MAX_VALUE
                    strokes.forEach { s ->
                        vm.strokesFlowOf(s).forEach { p ->
                            val sp = fit.map(p.x, p.y)
                            val dx = sp.x - tap.x
                            val dy = sp.y - tap.y
                            val sq = dx * dx + dy * dy
                            if (sq < bestSq) { bestSq = sq; best = s }
                        }
                    }
                    val hit = best
                    if (hit != null && bestSq <= TAP_RADIUS_PX * TAP_RADIUS_PX) onToggleStroke(hit)
                }
            }
        }
        val grid = if (background == null) Modifier.ncodeDotGrid(InkTokens.dotColor(cs.onBackground)) else Modifier
        Canvas(Modifier.fillMaxSize().then(grid).then(gesture)) {
            background?.let { drawPageBackground(it) }
            drawStrokes(strokes, vm::strokesFlowOf, cs.primary, selected, cs.tertiary, brandInk = cs.onSurface)
            if (lassoPts.size >= 2) {
                val path = Path().apply {
                    moveTo(lassoPts[0].x, lassoPts[0].y)
                    for (i in 1 until lassoPts.size) lineTo(lassoPts[i].x, lassoPts[i].y)
                }
                drawPath(
                    path, cs.tertiary,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                )
            }
        }
    }
}

/**
 * Floating edit toolbar (design-system §9): an Ink-dark bar inset over the canvas. Left — the lasso
 * toggle, an expanding color picker (full palette), and an expanding size picker (Fine/Medium/Large).
 * Right — delete and undo. The color/size/delete actions apply to the current selection (dimmed
 * until something's selected); undo reverts the last *edit*, not the last stroke written.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditToolbar(
    lassoMode: Boolean,
    hasSelection: Boolean,
    currentColor: Int,
    currentWidth: Float,
    onToggleLasso: () -> Unit,
    onRecolor: (Int) -> Unit,
    onResize: (Float) -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    // Material 3 Expressive floating tool palette (HorizontalFloatingToolbar graduated in alpha22),
    // with the lasso as a real ToggleButton. Centered over the page; always expanded.
    Box(modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) {
            ToggleButton(checked = lassoMode, onCheckedChange = { onToggleLasso() }) {
                Icon(Icons.Outlined.Gesture, contentDescription = "Lasso select")
            }
            ExpandingColorPicker(selected = currentColor, onColor = cs.onSurface, onSelect = onRecolor, enabled = hasSelection)
            ExpandingSizePicker(selected = currentWidth, onColor = cs.onSurface, onSelect = onResize, enabled = hasSelection)
            IconButton(onClick = onDelete, enabled = hasSelection) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete selected")
            }
            IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo last edit")
            }
        }
    }
}

/**
 * Listen mode (the pencast): replay a page's voice notes synced to the ink. Tapping any stroke jumps
 * the audio to what was being said while it was written; tapping a voice marker plays that note from
 * its start; while a note plays the ink "re-writes" itself (already-spoken strokes inked, the rest
 * dimmed); the scrubber (in [ListenBar]) seeks. All four behaviours come off one shared timeline.
 */
@Composable
private fun ListenCanvas(
    strokes: List<StrokeEntity>,
    vm: InkViewModel,
    recordings: List<RecordingEntity>,
    active: RecordingEntity?,
    playing: Boolean,
    positionMs: Long,
    onTapStroke: (StrokeEntity) -> Unit,
    onTapMarker: (RecordingEntity, Long) -> Unit,
    modifier: Modifier = Modifier,
    background: ImageBitmap? = null,
) {
    val cs = MaterialTheme.colorScheme
    // All bookmark points: (recording, marker-stroke) for the start + each post-pause resume.
    val markers = remember(recordings, strokes) {
        recordings.flatMap { r -> markerStrokes(r, strokes).map { r to it } }
    }
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        val gesture = Modifier.pointerInput(strokes, recordings) {
            detectTapGestures { tap ->
                val fit = inkFit(strokes, vm::strokesFlowOf, size.width.toFloat(), size.height.toFloat())
                    ?: return@detectTapGestures
                // Markers sit on top of the ink, so hit-test them first.
                val markerHit = markers.firstOrNull { (_, s) ->
                    val p = vm.strokesFlowOf(s).firstOrNull() ?: return@firstOrNull false
                    val sp = fit.map(p.x, p.y)
                    val dx = sp.x - tap.x; val dy = sp.y - tap.y
                    dx * dx + dy * dy <= MARKER_RADIUS_PX * MARKER_RADIUS_PX
                }
                if (markerHit != null) {
                    val (rec, s) = markerHit
                    onTapMarker(rec, (s.startedAt - rec.startedAt).coerceAtLeast(0L))
                    return@detectTapGestures
                }
                // Otherwise the nearest stroke (same hit-test as the editor).
                var best: StrokeEntity? = null; var bestSq = Float.MAX_VALUE
                strokes.forEach { s ->
                    vm.strokesFlowOf(s).forEach { p ->
                        val sp = fit.map(p.x, p.y)
                        val dx = sp.x - tap.x; val dy = sp.y - tap.y
                        val sq = dx * dx + dy * dy
                        if (sq < bestSq) { bestSq = sq; best = s }
                    }
                }
                val hit = best
                if (hit != null && bestSq <= TAP_RADIUS_PX * TAP_RADIUS_PX) onTapStroke(hit)
            }
        }
        val grid = if (background == null) Modifier.ncodeDotGrid(InkTokens.dotColor(cs.onBackground)) else Modifier
        Canvas(Modifier.fillMaxSize().then(grid).then(gesture)) {
            background?.let { drawPageBackground(it) }
            if (active != null && playing) {
                drawStrokesPencast(strokes, vm::strokesFlowOf, active, positionMs, cs.onSurfaceVariant.copy(alpha = 0.22f), brandInk = cs.onSurface)
            } else {
                drawStrokes(strokes, vm::strokesFlowOf, cs.primary, brandInk = cs.onSurface)
            }
            // Voice markers: a dot at the start + every resume-after-pause point.
            val fit = inkFit(strokes, vm::strokesFlowOf, size.width, size.height)
            if (fit != null) markers.forEach { (_, s) ->
                vm.strokesFlowOf(s).firstOrNull()?.let { p ->
                    val sp = fit.map(p.x, p.y)
                    drawCircle(cs.tertiary, radius = 7.dp.toPx(), center = sp)
                    drawCircle(cs.surface, radius = 2.5.dp.toPx(), center = sp)
                }
            }
        }
    }
}

/** Like [drawStrokes] but colours by playback: spoken strokes inked, not-yet-spoken ones dimmed. */
private fun DrawScope.drawStrokesPencast(
    strokes: List<StrokeEntity>,
    points: (StrokeEntity) -> List<Point>,
    recording: RecordingEntity,
    positionMs: Long,
    future: Color,
    brandInk: Color,
) {
    val fit = inkFit(strokes, points, size.width, size.height) ?: return
    strokes.forEach { s ->
        val raw = points(s)
        if (raw.isEmpty()) return@forEach
        val pts = raw.map { fit.map(it.x, it.y) }
        val pr = raw.map { it.pressure }
        val spoken = isStrokeSpoken(recording, s, positionMs)
        val w = InkTokens.inkWidthBase.toPx() * s.width
        val color = when {
            !spoken -> future                       // not yet reached → dim
            s.color != 0 -> Color(s.color)
            else -> brandInk                        // spoken default ink
        }
        drawPath(freehandPath(pts, pr, w), color)
    }
}

/** Bottom transport for Listen mode: active-note name + rename/delete, note picker, play/pause, scrub. */
@Composable
private fun ListenBar(
    recordings: List<RecordingEntity>,
    active: RecordingEntity,
    loaded: Boolean,
    playing: Boolean,
    positionMs: Long,
    onSelect: (RecordingEntity) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    // Theme-stable dark bar (Ink) with light content — NOT cs.onSurface, which inverts to white in dark mode.
    val onBar = InkText
    val activeIndex = recordings.indexOfFirst { it.id == active.id }
    Surface(
        modifier.padding(14.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = NavyDeep,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    recordingName(active, activeIndex),
                    style = MaterialTheme.typography.titleSmall,
                    color = onBar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRename) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Rename note", tint = onBar)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete note", tint = onBar)
                }
            }
            if (recordings.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recordings.forEachIndexed { i, r ->
                        val sel = r.id == active.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) cs.tertiary else onBar.copy(alpha = 0.15f),
                            modifier = Modifier.clickable { onSelect(r) },
                        ) {
                            Text(
                                recordingName(r, i),
                                style = monoData,
                                color = if (sel) cs.onPrimary else onBar,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = onBar,
                    )
                }
                Text(formatClock(if (loaded) positionMs else 0L), style = monoData, color = onBar)
                val dur = active.durationMs.coerceAtLeast(1L)
                Slider(
                    value = if (loaded) (positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    onValueChange = { frac -> onSeek((frac * dur).toLong()) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatClock(active.durationMs), style = monoData, color = onBar)
            }
        }
    }
}

/**
 * Draws a page's strokes auto-fit to the canvas. Brand ink (color 0) renders in the signature
 * blue→violet gradient; user-picked colors render solid; selected strokes use [highlight] + thicker.
 */
private fun DrawScope.drawStrokes(
    strokes: List<StrokeEntity>,
    points: (StrokeEntity) -> List<Point>,
    base: Color,
    selected: Set<String> = emptySet(),
    highlight: Color = base,
    brandInk: Color = base,
    glowLast: Color? = null,
) {
    val fit = inkFit(strokes, points, size.width, size.height) ?: return
    // Live capture: a soft halo under the newest stroke so fresh ink reads as "drawing on". Drawn
    // first (under) and bounded to one stroke + two passes, so the crisp ink stays on top and the
    // capture canvas takes no per-frame animation cost.
    if (glowLast != null) {
        strokes.lastOrNull()?.let { s ->
            val raw = points(s)
            if (raw.isNotEmpty()) {
                val pts = raw.map { fit.map(it.x, it.y) }
                val pr = raw.map { it.pressure }
                val w = InkTokens.inkWidthBase.toPx() * s.width
                drawPath(freehandPath(pts, pr, w * 3.0f), glowLast.copy(alpha = 0.10f))
                drawPath(freehandPath(pts, pr, w * 2.0f), glowLast.copy(alpha = 0.16f))
            }
        }
    }
    strokes.forEach { s ->
        val raw = points(s)
        if (raw.isEmpty()) return@forEach
        val pts = raw.map { fit.map(it.x, it.y) }
        val pr = raw.map { it.pressure }
        val sel = s.uuid in selected
        val w = InkTokens.inkWidthBase.toPx() * s.width * (if (sel) 1.6f else 1f)
        val color = when {
            sel -> highlight
            s.color != 0 -> Color(s.color)
            else -> brandInk // default ink = theme foreground
        }
        drawPath(freehandPath(pts, pr, w), color)
    }
}

/**
 * Maps raw Ncode paper coordinates onto the canvas. We don't assume a fixed page size — the pen's
 * coordinate range varies by notebook — so we fit the page's ink to the canvas bounds (aspect kept),
 * exactly like the SVG export. [map] takes a raw point to a canvas pixel.
 */
private class InkFit(private val scale: Float, private val offX: Float, private val offY: Float, private val minX: Float, private val minY: Float) {
    fun map(x: Float, y: Float) = Offset((x - minX) * scale + offX, (y - minY) * scale + offY)
}

private fun inkFit(
    strokes: List<StrokeEntity>,
    points: (StrokeEntity) -> List<Point>,
    width: Float,
    height: Float,
): InkFit? {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    var any = false
    strokes.forEach { s ->
        points(s).forEach { p ->
            any = true
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
    }
    if (!any || width <= 0f || height <= 0f) return null
    val margin = minOf(width, height) * 0.06f
    val cw = (maxX - minX).coerceAtLeast(1f)
    val ch = (maxY - minY).coerceAtLeast(1f)
    val scale = minOf((width - 2 * margin) / cw, (height - 2 * margin) / ch)
    return InkFit(scale, (width - cw * scale) / 2f, (height - ch * scale) / 2f, minX, minY)
}

/**
 * Render a page's ink to a white A4-ratio bitmap and hand it to the system print dialog. We reuse
 * the same auto-fit math as the on-screen canvas and the SVG export, so the print matches what the
 * user sees: ink centered, aspect kept, brand ink (color 0) printed black on white paper.
 *
 * Note: PrintHelper rasterizes one page to a single bitmap — fine for a handwriting page. The
 * upgrade path (multi-page / true vector print) is a PrintDocumentAdapter, only worth it if asked.
 */
private fun printPage(
    context: android.content.Context,
    jobName: String,
    strokes: List<StrokeEntity>,
    points: (StrokeEntity) -> List<Point>,
) {
    if (strokes.isEmpty()) return
    val bmp = PageRender.renderPage(strokes, points) ?: return
    PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }.printBitmap(jobName, bmp)
}

/** Ray-casting point-in-polygon, for lasso selection (coordinates in canvas pixels). */
private fun pointInPolygon(poly: List<Offset>, x: Float, y: Float): Boolean {
    if (poly.size < 3) return false
    var inside = false
    var j = poly.lastIndex
    for (i in poly.indices) {
        val xi = poly[i].x; val yi = poly[i].y
        val xj = poly[j].x; val yj = poly[j].y
        if (((yi > y) != (yj > y)) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/**
 * Live capture (mock #2, the hero): the page being written right now, on a full Ncode dot-grid, with
 * teal ink landing as you write. Renders from the database (the source of truth) — the [InkViewModel]
 * observes the most-recently-inked page, which updates as the ingestor commits each stroke. The
 * coordinate + sample-rate readout below are computed from real captured points, not faked.
 */
@Composable
private fun LiveCaptureScreen(vm: InkViewModel, onBack: () -> Unit) {
    // Start fresh on every entry: don't show the last session's page — wait for new ink.
    LaunchedEffect(Unit) { vm.startLiveSession() }
    // Never leave the mic (or a playback) running after leaving capture.
    // Note: stops the note on navigate-away; background recording would move this to the service.
    DisposableEffect(Unit) { onDispose { vm.stopRecording(); vm.stopPlayback() } }
    val pen by vm.penState.collectAsStateWithLifecycle()
    val page by vm.livePage.collectAsStateWithLifecycle()
    val strokes by vm.liveStrokes.collectAsStateWithLifecycle()
    val inkColor by vm.inkColorState.collectAsStateWithLifecycle()
    val inkWidth by vm.inkWidthState.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val recState by vm.recordingState.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val live = pen is PenConnState.Connected
    val pageId = page?.id

    // Voice notes need the mic permission; ask on the first tap, then start once granted.
    val context = LocalContext.current
    val micGranted = remember {
        mutableStateOf(
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted.value = granted
        if (granted) pageId?.let(vm::toggleRecording)
    }
    fun onRecordTap() {
        val id = pageId ?: return
        if (micGranted.value) vm.toggleRecording(id)
        else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("Back") }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        vm.notebookTitleOf(page),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        page?.let { "PAGE ${it.page}" } ?: "WAITING FOR INK",
                        style = monoEyebrow,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceRecordButton(
                    enabled = pageId != null,
                    recording = recState is RecordingController.State.Recording,
                    startedAt = (recState as? RecordingController.State.Recording)?.startedAt,
                    onTap = ::onRecordTap,
                )
                Spacer(Modifier.width(12.dp))
                battery?.let {
                    BatteryBadge(it, showEta = true)
                    Spacer(Modifier.width(12.dp))
                }
                if (live) LiveIndicator()
            }
        }

        // Writing controls: ink color + width, each a single chip that expands to the full options.
        val cs0 = MaterialTheme.colorScheme
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpandingColorPicker(selected = inkColor, onColor = cs0.onSurface, onSelect = vm::setInkColor)
            ExpandingSizePicker(selected = inkWidth, onColor = cs0.onSurface, onSelect = vm::setInkWidth)
        }
        pageId?.let { RecordingsStrip(it, vm) }
        InkSurface(strokes, vm, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), liveGlow = true)

        val lastPoints = strokes.lastOrNull()?.let { vm.strokesFlowOf(it) }.orEmpty()
        val lastPoint = lastPoints.lastOrNull()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                lastPoint?.let { "x %.1f · y %.1f".format(it.x, it.y) } ?: "x — · y —",
                style = monoData,
                color = cs.onSurfaceVariant,
            )
            Text(
                if (live) "streaming · ${sampleRate(lastPoints)} Hz" else "paused",
                style = monoData,
                color = if (live) cs.primary else cs.onSurfaceVariant,
            )
        }
    }
}

/** Ink colors for live capture (0 = brand ink, rendered in the theme primary). Mid-tones so they
 *  read on both light and dark. Picked at will to organize notes by color. */
private val INK_PALETTE = listOf(
    0,                    // default ink — theme foreground (white in dark, black in light)
    0xFF3B82F6.toInt(),   // blue
    0xFF22A06B.toInt(),   // green
    0xFFD6453D.toInt(),   // red
    0xFF8B5CF6.toInt(),   // purple
    0xFFB5872E.toInt(),   // amber
)

/** Writing/stroke widths offered by the size picker (multiplier on the base ink width). */
private val INK_SIZES = listOf("Fine" to 0.7f, "Medium" to 1.0f, "Large" to 1.6f)

private fun nearly(a: Float, b: Float) = kotlin.math.abs(a - b) < 0.05f

/**
 * Collapsed-by-default color picker: shows the current color as one dot; tapping animates open to
 * the full palette; picking a color collapses back to the single dot in the new color. [onColor] is
 * the foreground used for the default (color 0) swatch + selection ring (so it reads on dark bars).
 */
@Composable
private fun ExpandingColorPicker(
    selected: Int,
    onColor: Color,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (open) {
            INK_PALETTE.forEach { c -> ColorDot(c, onColor, sel = c == selected, enabled = enabled) { onSelect(c); open = false } }
        } else {
            ColorDot(selected, onColor, sel = false, enabled = enabled) { if (enabled) open = true }
        }
    }
}

@Composable
private fun ColorDot(color: Int, onColor: Color, sel: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val display = if (color == 0) onColor else Color(color) // 0 = default/theme ink
    Box(
        Modifier.size(if (sel) 26.dp else 22.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(display, CircleShape)
            .then(if (sel) Modifier.border(2.dp, onColor, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/** Collapsed-by-default size picker (Fine/Medium/Large), same expand/collapse behaviour as colors. */
@Composable
private fun ExpandingSizePicker(
    selected: Float,
    onColor: Color,
    onSelect: (Float) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (open) {
            INK_SIZES.forEach { (label, w) ->
                SizeChip(label, w, onColor, sel = nearly(w, selected), enabled = enabled) { onSelect(w); open = false }
            }
        } else {
            val label = INK_SIZES.firstOrNull { nearly(it.second, selected) }?.first ?: "Medium"
            SizeChip(label, selected, onColor, sel = false, enabled = enabled) { if (enabled) open = true }
        }
    }
}

@Composable
private fun SizeChip(label: String, width: Float, onColor: Color, sel: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (sel) cs.primary.copy(alpha = 0.22f) else Color.Transparent)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size((5 + width * 6).dp).background(onColor, CircleShape)) // dot hints the weight
        Text(label, style = MaterialTheme.typography.labelLarge, color = onColor)
    }
}

/**
 * Voice-note control for live capture: a mic that turns into a red Stop with a running timer while
 * recording. Disabled until a page is selected (a recording is always bound to a specific page).
 */
@Composable
private fun VoiceRecordButton(enabled: Boolean, recording: Boolean, startedAt: Long?, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (recording && startedAt != null) {
            val elapsed by produceState(0L, startedAt) {
                while (true) { value = System.currentTimeMillis() - startedAt; kotlinx.coroutines.delay(500) }
            }
            Text(formatClock(elapsed), style = monoData, color = cs.error)
            Spacer(Modifier.width(4.dp))
        }
        val tint = when {
            recording -> cs.error
            enabled -> cs.onSurface
            else -> cs.onSurfaceVariant.copy(alpha = 0.4f)
        }
        IconButton(onClick = onTap, enabled = enabled) {
            Icon(
                if (recording) Icons.Filled.Stop else Icons.Outlined.Mic,
                contentDescription = if (recording) "Stop recording" else "Record a voice note for this page",
                tint = tint,
            )
        }
    }
}

/** Horizontally-scrolling pills of the page's voice notes; tap one to play/stop it. */
@Composable
private fun RecordingsStrip(pageId: String, vm: InkViewModel) {
    val recordings by remember(pageId) { vm.recordingsFor(pageId) }.collectAsStateWithLifecycle(emptyList())
    if (recordings.isEmpty()) return
    val playingId by vm.playingRecordingId.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        recordings.forEachIndexed { i, r: RecordingEntity ->
            val playing = r.id == playingId
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (playing) cs.primaryContainer else cs.surfaceVariant,
                modifier = Modifier.clickable { if (playing) vm.stopPlayback() else vm.playRecording(r) },
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playing) "Stop" else "Play",
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        recordingName(r, i) + r.durationMs.takeIf { it > 0 }?.let { " · ${formatClock(it)}" }.orEmpty(),
                        style = monoData,
                        color = cs.onSurface,
                    )
                }
            }
        }
    }
}

/** A note's display name: its user label, or "Note N" by position. */
private fun recordingName(r: RecordingEntity, index: Int): String =
    r.title.ifBlank { "Note ${index + 1}" }

/** A page's tag chips: tap a chip to remove it, "+ Tag" to add one (Phase E). */
@Composable
private fun TagRow(pageId: String, vm: InkViewModel) {
    val tags by remember(pageId) { vm.tagsForPage(pageId) }.collectAsStateWithLifecycle(emptyList())
    val cs = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cs.secondaryContainer,
                modifier = Modifier.clickable { vm.removeTag(pageId, tag) },
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#$tag", style = monoData, color = cs.onSecondaryContainer)
                    Spacer(Modifier.size(3.dp))
                    Icon(Icons.Outlined.Close, contentDescription = "Remove tag", tint = cs.onSecondaryContainer, modifier = Modifier.size(14.dp))
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = cs.surfaceVariant,
            modifier = Modifier.clickable { adding = true },
        ) {
            Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(3.dp))
                Text("Tag", style = monoData, color = cs.onSurfaceVariant)
            }
        }
    }
    if (adding) {
        TextInputDialog(
            title = "Add tag",
            label = "Tag",
            initial = "",
            onDismiss = { adding = false },
            onConfirm = { vm.addTag(pageId, it); adding = false },
        )
    }
}

/** Small single-field text dialog (used for adding a tag). */
@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text(label) }) },
        confirmButton = { Button(onClick = { onConfirm(text.trim()) }) { Text("Save") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Rename a voice note. */
@Composable
private fun RenameRecordingDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = { Button(onClick = { onConfirm(text.trim()) }) { Text("Save") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** m:ss for a duration in ms. */
private fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/** Brass live dot with an expanding-ring pulse (design-system §8, 1.8s loop). */
@Composable
private fun LiveIndicator() {
    val reduced = rememberReducedMotion()
    val t = rememberInfiniteTransition(label = "live")
    // Reduced motion → ring stays at scale 1 / alpha 0 (invisible); the solid dot + LIVE pill remain.
    val scale by t.animateFloat(
        initialValue = 1f, targetValue = if (reduced) 1f else 2.4f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "ring",
    )
    val alpha by t.animateFloat(
        initialValue = if (reduced) 0f else 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "ringAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(9.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .background(LiveGreen, CircleShape),
            )
            Box(Modifier.size(9.dp).background(LiveGreen, CircleShape))
        }
        Spacer(Modifier.width(7.dp))
        Text("LIVE", style = monoData, color = LiveGreen)
    }
}

/** Read-only dot-grid ink surface — shared by live capture and the non-editing page view. */
@Composable
private fun InkSurface(
    strokes: List<StrokeEntity>,
    vm: InkViewModel,
    modifier: Modifier = Modifier,
    background: ImageBitmap? = null,
    liveGlow: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        val base = Modifier.fillMaxSize()
        Canvas(if (background == null) base.ncodeDotGrid(InkTokens.dotColor(cs.onBackground)) else base) {
            background?.let { drawPageBackground(it) }
            drawStrokes(strokes, vm::strokesFlowOf, cs.primary, brandInk = cs.onSurface, glowLast = if (liveGlow) cs.primary else null)
        }
    }
}

/** Draws a notebook's template image behind the ink, aspect-fit and centred to the page canvas. */
private fun DrawScope.drawPageBackground(image: ImageBitmap) {
    val s = minOf(size.width / image.width, size.height / image.height)
    val w = image.width * s; val h = image.height * s
    drawImage(
        image,
        srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()),
        dstSize = IntSize(w.toInt(), h.toInt()),
    )
}

/** Decode a persisted content:// image URI into an ImageBitmap (null on failure). */
private fun loadImageBitmap(context: android.content.Context, uri: String): ImageBitmap? = runCatching {
    context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
        android.graphics.BitmapFactory.decodeStream(it)
    }?.asImageBitmap()
}.getOrNull()

/** Real sample rate (Hz) from a stroke's captured point timestamps; "—" if indeterminable. */
private fun sampleRate(points: List<Point>): String {
    if (points.size < 2) return "—"
    val ms = points.last().t - points.first().t
    if (ms <= 0L) return "—"
    return (((points.size - 1) * 1000.0) / ms).toInt().toString()
}

// ---- Scan / pair ----

/** Lists pens found over BLE (strongest signal first); tap one to connect. Scanning stays active
 *  through the connect attempt — a direct LE connect to a non-bonded pen is far more reliable while
 *  the system still sees it advertising (matches NeoStudio). */
@Composable
private fun ScanScreen(vm: InkViewModel, onBack: () -> Unit) {
    val pens by vm.scannedPens.collectAsStateWithLifecycle()
    val pen by vm.penState.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { vm.startScan() }
    DisposableEffect(Unit) { onDispose { vm.stopScan() } }
    LaunchedEffect(pen) {
        if (pen is PenConnState.Connected || pen is PenConnState.PasswordRequired) onBack()
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            InkAppBar(title = "Find a pen", sub = "${pens.size} found") {
                Button(onClick = { vm.startScan() }) { Text("Rescan") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = onBack) { Text("Back") }
            }
        }
        when (pen) {
            is PenConnState.Connecting, is PenConnState.Reconnecting ->
                item { QuietLine("Connecting to the pen… keep it on and nearby.") }
            else ->
                if (pens.isEmpty()) {
                    item { QuietLine("Searching… make sure the pen is on and not connected to another device.") }
                } else {
                    items(pens) { p ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.connectPicked(p) },
                            colors = CardDefaults.cardColors(containerColor = cs.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                NibBadge(live = false)
                                Spacer(Modifier.size(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${p.mac} · ${p.target.protocol}", style = monoData, color = cs.onSurfaceVariant)
                                }
                                Text("${p.rssi} dBm", style = monoData, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
        }
    }
}

/** First-connect nudge (FIX #2) to allow background capture — once, dismissible. */
@Composable
private fun BackgroundCaptureNudgeDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep capturing in the background?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "Your device may sleep InkVault when it's not on screen — dropping the pen and " +
                    "pausing capture. Allow background activity so your strokes keep saving even with " +
                    "the app in the background.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { Button(onClick = onAllow) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/** Confirm before leaving the app (the OS Back at the root), so it isn't an accidental exit. */
@Composable
private fun ExitConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exit InkVault?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "The pen stays connected in the background while you're away.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Yes") } },
        dismissButton = { Button(onClick = onDismiss) { Text("No") } },
    )
}

/** Unwrap an Activity from a (possibly wrapped) Context — for finishing the app on exit-confirm. */
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Shown when a locked pen sends PASSWORD_REQUEST. A wrong entry just re-prompts. */
@Composable
private fun PasswordDialog(onSubmit: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Enter pen password", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text("This pen is locked. Enter its password to receive ink.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = { Button(onClick = { onSubmit(pw) }) { Text("Unlock") } },
    )
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
    )
}

private const val TAP_RADIUS_PX = 48f
private const val MARKER_RADIUS_PX = 30f // tap target around a voice-note marker dot
private const val LASSO_INSIDE_FRACTION = 0.6f // a stroke is selected when ≥60% of its points are inside the loop
