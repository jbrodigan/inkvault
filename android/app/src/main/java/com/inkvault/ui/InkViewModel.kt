package com.inkvault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inkvault.audio.RecordingController
import com.inkvault.data.NotebookEntity
import com.inkvault.data.PageEntity
import com.inkvault.data.RecordingEntity
import com.inkvault.data.StrokeEntity
import com.inkvault.edit.NoOpPageEditor
import com.inkvault.edit.PageEditor
import com.inkvault.export.ExportOutcome
import com.inkvault.export.SettingsStore
import com.inkvault.export.SyncMethod
import com.inkvault.export.ThemeMode
import com.inkvault.pen.CaptureSignals
import com.inkvault.pen.PenConnState
import com.inkvault.pen.PenScanner
import com.inkvault.pen.ScannedPen
import com.inkvault.repo.NoteRepository
import com.inkvault.pen.PenConnectionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class InkViewModel(
    private val repo: NoteRepository,
    private val penManager: PenConnectionManager,
    private val settings: SettingsStore,
    private val scanner: PenScanner,
    private val exportPageNow: suspend (pageId: String) -> ExportOutcome = { ExportOutcome.NO_TARGET },
    private val editor: PageEditor = NoOpPageEditor,
    private val inkColor: MutableStateFlow<Int> = MutableStateFlow(0),
    private val inkWidth: MutableStateFlow<Float> = MutableStateFlow(1f),
    private val recorder: RecordingController? = null,
    private val signals: CaptureSignals? = null,
    /** Whether the pen unlock password is currently stored (encrypted) for auto-unlock. */
    private val hasStoredPassword: () -> Boolean = { false },
    private val calendar: com.inkvault.calendar.CalendarGateway? = null,
    private val actionZones: com.inkvault.zones.ActionZoneStore? = null,
    private val captureTrace: ((onTrace: (Float, Float, Float, Float) -> Unit) -> Unit)? = null,
    private val cancelTrace: (() -> Unit)? = null,
    private val captureLog: com.inkvault.capture.CaptureLog? = null,
    private val backgroundStore: com.inkvault.background.BackgroundStore? = null,
    private val transcripts: com.inkvault.ocr.TranscriptImporter? = null,
    /** On-device handwriting OCR of a page → stored transcript (or null). ML Kit Digital Ink. */
    private val transcribeOnDevice: (suspend (pageId: String) -> String?)? = null,
    private val translator: com.inkvault.translate.Translator? = null,
) : ViewModel() {

    /** Live-ink probes for the connection diagnostic (Phase A). "Receiving ink" means ink has flowed
     *  at all since connecting (not just in the last few seconds), so it isn't a false ✗ when idle. */
    fun receivingInk(): Boolean =
        (signals?.receivedSinceConnect?.value ?: false) || (signals?.recentlyReceiving() ?: false)
    fun paperRecognized(): Boolean = signals?.lastAddressValid?.value ?: false

    /** The live ink color (0 = brand ink) for new strokes; set from the capture color picker. */
    val inkColorState: StateFlow<Int> = inkColor
    fun setInkColor(color: Int) { inkColor.value = color }

    /** The live writing width multiplier (Fine/Medium/Large) for new strokes. */
    val inkWidthState: StateFlow<Float> = inkWidth
    fun setInkWidth(width: Float) { inkWidth.value = width }

    /** Pens discovered by the current BLE scan, strongest signal first. */
    val scannedPens: StateFlow<List<ScannedPen>> = scanner.results

    fun startScan() = scanner.start()
    fun stopScan() = scanner.stop()
    /**
     * Connect to a picked pen, passing its full [com.inkvault.pen.PenTarget] (spp identity + LE
     * advertising address + protocol) — all three are required for the connect to succeed. We KEEP
     * scanning during the attempt: a direct LE connect to this non-bonded, randomly-addressed pen is
     * far more reliable while the system still sees it advertising (that's how NeoStudio connects).
     * ScanScreen stops the scan once the link is up.
     */
    fun connectPicked(pen: ScannedPen) {
        penManager.connect(pen.target)
    }

    val syncMethod: StateFlow<SyncMethod> =
        settings.syncMethod.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncMethod.DEFAULT)
    val localFolderUri: StateFlow<String> =
        settings.localFolderUri.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val tailscaleEndpoint: StateFlow<String> =
        settings.tailscaleEndpoint.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val translateEndpoint: StateFlow<String> =
        settings.translateEndpoint.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val translateModel: StateFlow<String> =
        settings.translateModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setTranslateEndpoint(endpoint: String) = viewModelScope.launch { settings.setTranslateEndpoint(endpoint) }
    fun setTranslateModel(model: String) = viewModelScope.launch { settings.setTranslateModel(model) }

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.DEFAULT)
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setSyncMethod(method: SyncMethod) = viewModelScope.launch { settings.setSyncMethod(method) }
    fun setLocalFolderUri(uri: String) = viewModelScope.launch { settings.setLocalFolderUri(uri) }
    fun setTailscaleEndpoint(endpoint: String) = viewModelScope.launch { settings.setTailscaleEndpoint(endpoint) }

    val notebooks: StateFlow<List<NotebookEntity>> =
        repo.notebooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val penState: StateFlow<PenConnState> = penManager.state

    /** Pen battery + charge estimate (null when no pen / unknown). */
    val battery: StateFlow<com.inkvault.pen.BatteryStatus?> = penManager.battery

    /**
     * Live capture starts FRESH each time the screen is opened: we ignore whatever page was inked
     * in a past session and only bind to a page once ink lands at-or-after [startLiveSession] (a
     * fresh page or one previously written into — the pen touches its page on the first stroke).
     * Until then the screen shows "waiting for ink". Long.MAX_VALUE ⇒ nothing matches before open.
     */
    private val liveSince = MutableStateFlow(Long.MAX_VALUE)
    fun startLiveSession() { liveSince.value = System.currentTimeMillis() }

    private val gatedLivePage: Flow<PageEntity?> =
        combine(liveSince, repo.livePage()) { since, page -> page?.takeIf { it.lastInkAt >= since } }

    /** The page currently receiving ink, and its strokes — for the live-capture screen. */
    val livePage: StateFlow<PageEntity?> =
        gatedLivePage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val liveStrokes: StateFlow<List<StrokeEntity>> =
        gatedLivePage
            .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.strokes(p.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The notebook currently being written in that the user hasn't set up yet — drives the one-time
     * new-notebook dialog (pick its product type + label this copy). Reactive: acknowledging it (Save
     * or Skip) makes this go null, so the prompt asks once per physical notebook.
     */
    val notebookNeedingSetup: StateFlow<NotebookEntity?> =
        combine(livePage, notebooks, settings.acknowledgedNotebooks) { page, books, done ->
            books.firstOrNull { it.id == page?.notebookId && it.id !in done }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The built-in type id for a book, to pre-select in the setup dialog (null = unmeasured product). */
    fun resolvedTypeId(book: Int): String? = com.inkvault.export.NotebookType.forBook(book)?.id

    /** Apply the new-notebook dialog: assign the product type (by book id) + label this copy, once. */
    fun setUpNotebook(notebookId: String, book: Int, typeId: String, label: String) {
        viewModelScope.launch {
            settings.assignNotebookType(book, typeId)
            if (label.isNotBlank()) repo.rename(notebookId, label)
            settings.acknowledgeNotebook(notebookId)
        }
    }

    /** Dismiss the new-notebook dialog without changes (still acknowledged so it won't nag). */
    fun skipNotebookSetup(notebookId: String) =
        viewModelScope.launch { settings.acknowledgeNotebook(notebookId) }

    // --- voice notes (tied to a page; phone mic, local-only) ---

    /** Recording state: Idle, or Recording(pageId, startedAt) — startedAt aligns with stroke times. */
    val recordingState: StateFlow<RecordingController.State> =
        recorder?.state ?: MutableStateFlow(RecordingController.State.Idle)

    /** Which stored recording is currently playing back (id), or null. */
    val playingRecordingId: StateFlow<String?> =
        recorder?.playingId ?: MutableStateFlow<String?>(null)

    /** Playback head of the active recording (ms from its start) — drives highlight + scrubber. */
    val playbackPositionMs: StateFlow<Long> =
        recorder?.positionMs ?: MutableStateFlow(0L)

    /** True while a note is actively playing (false when paused/stopped). */
    val isPlaying: StateFlow<Boolean> =
        recorder?.isPlaying ?: MutableStateFlow(false)

    fun recordingsFor(pageId: String?): Flow<List<RecordingEntity>> =
        if (pageId == null) flowOf(emptyList()) else repo.recordings(pageId)

    /** Start a recording bound to [pageId], or stop the one in progress. */
    fun toggleRecording(pageId: String) {
        val r = recorder ?: return
        if (r.isRecording) r.stop() else r.start(pageId)
    }

    fun stopRecording() { recorder?.stop() }
    fun playRecording(recording: RecordingEntity, startMs: Long = 0L) { recorder?.play(recording, startMs) }
    fun pausePlayback() { recorder?.pause() }
    fun resumePlayback() { recorder?.resume() }
    fun seekPlayback(ms: Long) { recorder?.seekTo(ms) }
    fun stopPlayback() { recorder?.stopPlayback() }
    fun deleteRecording(recording: RecordingEntity) { recorder?.delete(recording) }
    fun renameRecording(id: String, title: String) { recorder?.rename(id, title) }

    /** Title of the notebook a page belongs to (for the live/page app bar). */
    fun notebookTitleOf(page: PageEntity?): String =
        notebooks.value.firstOrNull { it.id == page?.notebookId }?.title ?: "Capture"

    val pendingUploads: StateFlow<Int> =
        repo.pendingUploads().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val selectedNotebook = MutableStateFlow<String?>(null)
    private val selectedPage = MutableStateFlow<String?>(null)

    /** Observable drill-down state so the UI can animate transitions (shared-axis / container transform). */
    val selectedNotebookId: StateFlow<String?> = selectedNotebook
    val selectedPageId: StateFlow<String?> = selectedPage

    val pages: StateFlow<List<PageEntity>> =
        selectedNotebook
            .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.pages(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val strokes: StateFlow<List<StrokeEntity>> =
        selectedPage
            .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.strokes(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The open page's stored OCR transcript, reactive — refreshes after on-device/server transcription. */
    val currentTranscript: StateFlow<String?> =
        combine(selectedPage, pages) { id, list -> list.firstOrNull { it.id == id }?.transcript }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openNotebook(id: String) { selectedNotebook.value = id; selectedPage.value = null }
    fun openPage(id: String) { selectedPage.value = id; clearTranslation() }
    fun back() { if (selectedPage.value != null) selectedPage.value = null else selectedNotebook.value = null }

    fun currentNotebook(): String? = selectedNotebook.value
    fun currentPage(): String? = selectedPage.value

    fun strokesFlowOf(stroke: StrokeEntity) = repo.decodePoints(stroke)

    /** Strokes of one page — for drawing real ink into a library page thumbnail. */
    fun pageStrokes(pageId: String): Flow<List<StrokeEntity>> = repo.strokes(pageId)

    /** Whether a page / notebook has voice notes — for the library thumbnail soundwave badge. */
    fun pageHasAudio(pageId: String): Flow<Boolean> = repo.pageHasAudio(pageId)
    fun notebookHasAudio(notebookId: String): Flow<Boolean> = repo.notebookHasAudio(notebookId)

    // --- tags (Phase E) ---
    fun tagsForPage(pageId: String?): Flow<List<String>> =
        if (pageId == null) flowOf(emptyList()) else repo.tagsForPage(pageId)
    val allTags: StateFlow<List<String>> =
        repo.allTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun addTag(pageId: String, tag: String) {
        if (tag.isBlank()) return
        viewModelScope.launch { repo.addTag(pageId, tag) }
    }
    fun removeTag(pageId: String, tag: String) = viewModelScope.launch { repo.removeTag(pageId, tag) }

    /** Library tag filter: when a tag is selected, the Library shows that tag's pages flat. */
    private val selectedTag = MutableStateFlow<String?>(null)
    val selectedTagState: StateFlow<String?> = selectedTag
    fun selectTag(tag: String?) { selectedTag.value = tag }
    val taggedPages: StateFlow<List<PageEntity>> =
        selectedTag
            .flatMapLatest { t -> if (t == null) flowOf(emptyList()) else repo.pagesWithTag(t) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A notebook's cover ink = the strokes of its most-recently-inked page (empty if none). */
    fun notebookCoverStrokes(notebookId: String): Flow<List<StrokeEntity>> =
        repo.pages(notebookId).flatMapLatest { pages ->
            val cover = pages.maxByOrNull { it.lastInkAt }
            if (cover == null) flowOf(emptyList()) else repo.strokes(cover.id)
        }

    fun takeOver(mac: String) = penManager.takeOver(mac)
    fun submitPassword(password: String) = penManager.submitPassword(password)

    /** Drop the current pen link (and stop auto-reconnect). Only meaningful once connected. */
    fun disconnect() = penManager.disconnect()

    // --- Pen password management (Settings) ---

    /** Whether an unlock password is stored for auto-unlock (drives the Settings "auto-unlock" line). */
    fun hasStoredPassword(): Boolean = hasStoredPassword.invoke()

    /** "Remember the pen password for 30 days" toggle (off = ask every connect). */
    val rememberPassword: StateFlow<Boolean> =
        settings.rememberPassword.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setRememberPassword(on: Boolean) = viewModelScope.launch { settings.setRememberPassword(on) }

    /** First-connect "allow background capture" nudge — true once shown/dismissed, so it asks once. */
    val bgCaptureNudgeDismissed: StateFlow<Boolean> =
        settings.bgCaptureNudgeDismissed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun dismissBgCaptureNudge() = viewModelScope.launch { settings.dismissBgCaptureNudge() }

    /** Result of an in-flight change/disable-password request (drives the Settings feedback). */
    val passwordOp: StateFlow<com.inkvault.pen.PasswordOpState> = penManager.passwordOp
    fun changePenPassword(old: String, new: String) = penManager.changePassword(old, new)
    fun disablePenPassword(current: String) = penManager.disablePassword(current)
    fun acknowledgePasswordOp() = penManager.acknowledgePasswordOp()

    // --- Firmware (Settings) ---

    /** The connected pen's firmware version, or null until it reports one. */
    val firmwareVersion: StateFlow<String?> = penManager.firmwareVersion
    /** Progress/result of an in-flight firmware update. */
    val firmwareUpdate: StateFlow<com.inkvault.pen.FirmwareUpdateState> = penManager.firmwareUpdate
    fun updateFirmware(file: java.io.File) = penManager.updateFirmware(file)
    fun acknowledgeFirmwareUpdate() = penManager.acknowledgeFirmwareUpdate()

    /** Transient status line for page-detail actions (export / on-device OCR); shown briefly. */
    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus

    /** Whether on-device OCR is wired (drives the menu item's visibility). */
    val onDeviceOcrAvailable: Boolean = transcribeOnDevice != null

    /**
     * Transcribe the open page on-device (ML Kit Digital Ink) and index it. The first run downloads
     * the language model (one-time, ~few MB); after that it's fully offline.
     */
    fun transcribeCurrentPageOnDevice() {
        val pageId = selectedPage.value ?: return
        val run = transcribeOnDevice ?: return
        viewModelScope.launch {
            _exportStatus.value = "Transcribing on device…"
            val text = runCatching { run(pageId) }.getOrNull()
            _exportStatus.value =
                if (text == null) "Couldn't transcribe (no recognizable handwriting / model)"
                else "Transcribed ${text.length} chars — now searchable"
            delay(3_000)
            _exportStatus.value = null
        }
    }

    /** Export the page currently open in page detail to the user's selected sync target. */
    fun exportCurrentPage() {
        val pageId = selectedPage.value ?: return
        viewModelScope.launch {
            _exportStatus.value = "Exporting…"
            _exportStatus.value = when (exportPageNow(pageId)) {
                ExportOutcome.DONE -> "Exported to your sync target"
                ExportOutcome.NO_TARGET -> "Pick a sync target in Settings first"
                ExportOutcome.FAILED -> "Export failed — it stays queued and retries"
            }
            delay(3_000)
            _exportStatus.value = null
        }
    }

    // --- Phase 5 editing (page-detail edit toolbar) ---
    private fun edit(op: suspend (pageId: String) -> Unit) {
        val pageId = selectedPage.value ?: return
        viewModelScope.launch { op(pageId) }
    }

    // Selection edits — each is one undoable step (see PageEditor/StrokeEditor).
    fun deleteSelection(uuids: List<String>) = edit { editor.delete(uuids, it) }
    fun recolorSelection(uuids: List<String>, color: Int) = edit { editor.recolor(uuids, color, it) }
    fun resizeSelection(uuids: List<String>, width: Float) = edit { editor.setThickness(uuids, width, it) }
    /** Revert the last edit (recolor / resize / delete) — NOT the last stroke written. */
    fun undoEdit() = edit { editor.undo(it) }

    // --- Calendar (system CalendarContract; no OAuth) ---

    /** The device calendar new events go to (-1 = not chosen). */
    val calendarTargetId: StateFlow<Long> =
        settings.calendarTargetId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1L)
    fun setCalendarTarget(id: Long) = viewModelScope.launch { settings.setCalendarTargetId(id) }

    /** Writable device calendars (needs READ_CALENDAR); off the main thread. */
    suspend fun writableCalendars(): List<com.inkvault.calendar.CalendarTarget> =
        withContext(Dispatchers.IO) { calendar?.writableCalendars() ?: emptyList() }

    /** Add an event to the chosen calendar (needs WRITE_CALENDAR). Returns true on success. */
    suspend fun addCalendarEvent(
        title: String, startMs: Long, endMs: Long, allDay: Boolean, notes: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val target = calendarTargetId.value
        if (target < 0 || calendar == null) return@withContext false
        calendar.insertEvent(target, title.ifBlank { "Untitled" }, startMs, endMs, allDay, notes) != null
    }

    // --- Physical action zones (tap-to-teach printed icons) ---

    val zones: StateFlow<List<com.inkvault.zones.ActionZone>> =
        actionZones?.zones ?: MutableStateFlow(emptyList())

    /** Capture the next traced outline (raw Ncode bbox) while the user circles a printed icon. */
    fun calibrateNextTrace(onTrace: (Float, Float, Float, Float) -> Unit) = captureTrace?.invoke(onTrace)
    /** Abort a pending [calibrateNextTrace] (the user cancelled). */
    fun cancelCalibration() = cancelTrace?.invoke()

    /** Store a zone from a traced box, padded slightly so taps near the edge still register. */
    fun addZone(action: com.inkvault.zones.ZoneAction, left: Float, top: Float, right: Float, bottom: Float) {
        val padX = (right - left) * ZONE_PAD + ZONE_MIN_PAD
        val padY = (bottom - top) * ZONE_PAD + ZONE_MIN_PAD
        actionZones?.add(
            com.inkvault.zones.ActionZone(
                java.util.UUID.randomUUID().toString(), action,
                left - padX, top - padY, right + padX, bottom + padY,
            ),
        )
    }

    fun removeZone(id: String) = actionZones?.remove(id)

    // --- Capture Lab (record the pen's own output for measurements) ---

    val captureRecording: StateFlow<Boolean> =
        captureLog?.recording ?: MutableStateFlow(false)

    fun startCapture() = captureLog?.start()
    fun stopCapture(): List<com.inkvault.capture.CapturedDot> = captureLog?.stop() ?: emptyList()

    // --- Background templates (per notebook, rendered behind the ink) ---
    val backgrounds: StateFlow<Map<String, String>> =
        backgroundStore?.map ?: MutableStateFlow(emptyMap())
    fun setBackground(notebookId: String, uri: String) = backgroundStore?.set(notebookId, uri)
    fun clearBackground(notebookId: String) = backgroundStore?.clear(notebookId)

    // --- Search (notebook titles now; OCR transcripts once imported from the sync folder) ---

    /** Pull any `<pageId>.txt` the watcher wrote back into the folder. @return pages updated. */
    suspend fun importTranscripts(): Int = transcripts?.importPending() ?: 0

    /** Pages matching [query] by notebook title or imported transcript. */
    suspend fun searchPages(query: String): List<PageEntity> = repo.searchPages(query)

    /** Most recently inked pages, shown when the search box is empty. */
    suspend fun recentPages(): List<PageEntity> = repo.recentPages()

    // --- Translation (quality LLM on the user's box; ML Kit on-device fallback) ---

    val translatorAvailable: Boolean = translator != null

    /** Default translation target = the device language (the user can pick another in the Text view). */
    val deviceLanguage: String = java.util.Locale.getDefault().language

    /** The current page's translation, with which engine produced it (null = showing the original). */
    data class TranslationUi(val text: String, val onDevice: Boolean)
    private val _translation = MutableStateFlow<TranslationUi?>(null)
    val translation: StateFlow<TranslationUi?> = _translation
    private val _translating = MutableStateFlow(false)
    val translating: StateFlow<Boolean> = _translating
    private val _translateError = MutableStateFlow<String?>(null)
    val translateError: StateFlow<String?> = _translateError

    /** Translate the open page's transcript into [target] (source null = auto-detect on-device). */
    fun translateCurrentPage(target: String, source: String?) {
        val text = currentTranscript.value?.takeIf { it.isNotBlank() } ?: return
        val t = translator ?: return
        viewModelScope.launch {
            _translating.value = true; _translateError.value = null
            val r = runCatching { t.translate(text, target, source) }.getOrNull()
            if (r == null) {
                _translateError.value = "Couldn't translate. Set a translation endpoint in Settings for best quality, or pick languages available offline."
            } else {
                _translation.value = TranslationUi(r.text, r.engine == com.inkvault.translate.Translator.Engine.ON_DEVICE)
            }
            _translating.value = false
        }
    }

    /** Drop the translation and show the original transcript again. */
    fun clearTranslation() { _translation.value = null; _translateError.value = null }

    /** Open a specific page (from a search hit): select its notebook, then the page. */
    fun openSearchHit(page: PageEntity) { openNotebook(page.notebookId); openPage(page.id) }

    private companion object {
        const val ZONE_PAD = 0.15f   // grow the traced box 15% each side …
        const val ZONE_MIN_PAD = 1f  // … plus a small minimum, in Ncode units
    }
}
