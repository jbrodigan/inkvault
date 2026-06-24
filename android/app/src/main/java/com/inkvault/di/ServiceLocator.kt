package com.inkvault.di

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.inkvault.BuildConfig
import com.inkvault.audio.RecordingController
import com.inkvault.data.InkDatabase
import com.inkvault.data.MIGRATION_4_5
import com.inkvault.data.MIGRATION_5_6
import com.inkvault.data.MIGRATION_6_7
import com.inkvault.data.MIGRATION_7_8
import com.inkvault.data.MIGRATION_8_9
import com.inkvault.data.Point
import com.inkvault.export.ExportEngine
import com.inkvault.export.ExportWorker
import com.inkvault.export.LocalFolderProvider
import com.inkvault.export.LocalOnlyProvider
import com.inkvault.export.SettingsStore
import com.inkvault.export.StorageProvider
import com.inkvault.export.SyncMethod
import com.inkvault.export.TailscalePushProvider
import com.inkvault.ingest.OfflineSync
import com.inkvault.ingest.StrokeIngestor
import com.inkvault.organize.AutoOrganizer
import com.inkvault.pen.CaptureSignals
import com.inkvault.pen.FakeNeoPenSdk
import com.inkvault.pen.NeoPenSdk
import com.inkvault.pen.PenConnectionManager
import com.inkvault.pen.PenScanner
import com.inkvault.pen.SharedPrefsPenPrefs
import com.inkvault.repo.NoteRepository
import com.inkvault.security.SecretStore
import com.inkvault.edit.StrokeEditor
import com.inkvault.export.ExportOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Tiny manual DI. Hand-wiring keeps the dependency graph visible in one place
 * and avoids kapt/Hilt build complexity. Swap [FakeNeoPenSdk] for
 * the `:neosdk` module's NeoSdkAdapter (real pen) to go live — see android/STRANGLER.md.
 *
 * NOTE (post-brief): there is intentionally NO cloud sync here. This app is
 * local-first/self-hosted — export → NAS (Syncthing/Tailscale) is Phase 3, and
 * OCR hand-off is Phase 4. See android/DESIGN.md.
 */
class ServiceLocator private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: InkDatabase = Room.databaseBuilder(
        appContext, InkDatabase::class.java, "inkvault.db",
    )
        // Real captures now live on devices, so preserve them across schema bumps with explicit
        // migrations. Destructive fallback stays only as a last resort for un-migrated jumps.
        .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    /**
     * The pen unlock password, encrypted at rest (Keystore-backed). By default the app asks every
     * connect; if the user turns on "remember for 30 days" we store the typed password here and
     * auto-unlock until it expires. It never leaves the device. The build-time `-PpenPassword=…`
     * flag is only a dev fallback.
     */
    val penPassword = SecretStore(appContext, "pen_password")

    /** Synchronous mirror of the "remember password" setting, for the BLE-thread password provider. */
    @Volatile private var rememberPassword = false

    /** The stored password to auto-answer with, honoring the toggle + 30-day expiry (else null). */
    private fun rememberedPassword(): String? =
        if (rememberPassword && penPassword.savedWithinDays(REMEMBER_DAYS)) penPassword.get() else null

    // --- pen driver ---
    // Real NeoLAB SDK when the :neosdk AAR is present (STRANGLER.md), else the in-memory fake. The
    // app never imports kr.neolab.sdk; we resolve the adapter reflectively so :app + CI compile
    // without it. The adapter asks [penPassword] for the stored unlock password (build flag fallback).
    val penSdk: NeoPenSdk = loadRealPenSdk() ?: FakeNeoPenSdk()
    // ------------------

    private val penPrefs = SharedPrefsPenPrefs(appContext)

    private fun loadRealPenSdk(): NeoPenSdk? = runCatching {
        val cls = Class.forName("com.inkvault.neosdk.NeoSdkAdapter")
        val ctor = cls.getConstructor(Context::class.java, Function1::class.java)
        ctor.newInstance(
            appContext,
            { _: String -> rememberedPassword() ?: BuildConfig.PEN_PASSWORD.ifBlank { null } },
        ) as NeoPenSdk
    }.getOrNull()

    /** Runtime sync/OCR settings (DataStore). The Settings screen reads/writes this. */
    val settings = SettingsStore(appContext)

    init {
        // Mirror the "remember password" toggle into a synchronous field for the BLE-thread provider.
        // Turning it off forgets any stored password so the next connect prompts again.
        appScope.launch {
            settings.rememberPassword.collect { on ->
                rememberPassword = on
                if (!on) penPassword.clear()
            }
        }
    }

    /** BLE discovery for the scan/pick UI. */
    val penScanner = PenScanner(appContext)

    /** Creates events in the user's device calendars (Google/Outlook) via CalendarContract. */
    val calendarGateway = com.inkvault.calendar.CalendarGateway(appContext)

    /** Calibrated physical action zones (printed Share/Email icons → tap triggers the action). */
    val actionZones = com.inkvault.zones.ActionZoneStore(appContext)

    /** Capture Lab dot recorder (scale/icon/planner measurements from the pen's own output). */
    val captureLog = com.inkvault.capture.CaptureLog()

    /** Per-notebook background template images, rendered behind the ink. */
    val backgrounds = com.inkvault.background.BackgroundStore(appContext)

    /** Imports watcher-written `<pageId>.txt` transcripts from the sync folder, for in-app search. */
    val transcriptImporter = com.inkvault.ocr.TranscriptImporter(
        appContext, db.pageDao(),
        folderUri = { settings.localFolderUri.first() },
        // A new transcript makes the page's Markdown note stale → re-queue it for export.
        onImported = { pageId -> requeuePageForExport(pageId) },
    )

    /** On-device handwriting OCR (ML Kit Digital Ink) — instant, offline transcription from strokes. */
    val onDeviceInk = com.inkvault.ocr.OnDeviceInk()

    /** Tiered translator: the user's GPU-box translation LLM (quality), ML Kit on-device (offline). */
    val translator = com.inkvault.translate.Translator(
        endpoint = { settings.translateEndpoint.first() },
        model = { settings.translateModel.first() },
    )

    /**
     * Transcribe a page on-device, store the result, and re-queue it for export (so it becomes
     * searchable and its `.md` note refreshes). @return the text, or null if nothing was recognized.
     */
    suspend fun transcribeOnDevice(pageId: String): String? = withContext(Dispatchers.IO) {
        val strokes = db.strokeDao().strokesForPage(pageId)
        val text = onDeviceInk.transcribe(strokes) {
            exportJson.decodeFromString(ListSerializer(Point.serializer()), it.pointsJson)
        } ?: return@withContext null
        db.pageDao().setTranscriptIndexed(pageId, text)
        requeuePageForExport(pageId)
        text
    }

    /** Re-queue a page's strokes for export (e.g. after a transcript import refreshes its `.md`). */
    private suspend fun requeuePageForExport(pageId: String) {
        val strokes = db.strokeDao().strokesForPage(pageId)
        if (strokes.isEmpty()) return
        db.strokeDao().markSync(strokes.map { it.uuid }, com.inkvault.data.SyncState.PENDING)
        strokes.forEach { db.outboxDao().enqueue(com.inkvault.data.OutboxEntry(it.uuid, System.currentTimeMillis())) }
        ExportWorker.enqueue(appContext)
    }

    val organizer = AutoOrganizer(db.notebookDao(), db.pageDao())

    private val exportJson = Json

    /** Phase 3 export core; the worker drives it with whatever target [currentStorageProvider] yields. */
    val exportEngine = ExportEngine(
        strokeDao = db.strokeDao(),
        pageDao = db.pageDao(),
        notebookDao = db.notebookDao(),
        outboxDao = db.outboxDao(),
        exportDao = db.exportDao(),
        decode = { exportJson.decodeFromString(ListSerializer(Point.serializer()), it.pointsJson) },
        penId = { penPrefs.lastPenMac ?: "unknown" },
        // Page rasters alongside the vectors (one bitmap pass): PNG so the OCR hand-off gets
        // image + strokes, and PDF as a portable/printable copy in the sync folder.
        renderRasters = { strokes, dec ->
            val bmp = com.inkvault.share.PageRender.renderPage(strokes, dec)
            if (bmp == null) emptyList() else buildList {
                java.io.ByteArrayOutputStream().use { out ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    add("png" to out.toByteArray())
                }
                com.inkvault.share.PageShare.pdfBytes(bmp)?.let { add("pdf" to it) }
            }
        },
        // Resolve each page's notebook type through the user's saved assignments, so a typed-but-
        // unmeasured notebook (e.g. a planner the user designated) files under the right path/geometry.
        typeForBook = { settings.notebookType(it) },
        tagsFor = { db.tagDao().tagsForPage(it) },
    )

    /** Live ink color the user picks in capture (0 = brand ink). Stamped onto each new stroke. */
    val inkColor = MutableStateFlow(0)

    /** Live writing width (Fine/Medium/Large → multiplier). Stamped onto each new stroke. */
    val inkWidth = MutableStateFlow(1f)

    val ingestor = StrokeIngestor(
        ingestDao = db.ingestDao(),
        pendingDao = db.pendingDotDao(),
        pageDao = db.pageDao(),
        organizer = organizer,
        scope = appScope,
        inkColor = { inkColor.value },
        inkWidth = { inkWidth.value },
        // Each committed stroke kicks a (coalesced) export drain. Idempotent + durable, so a no-op
        // when nothing's configured and a catch-up when the target is set later.
        onCommitted = { ExportWorker.enqueue(appContext) },
        actionZones = { actionZones.current() },
        onZoneTap = { zone, pageId -> handleZoneTap(zone, pageId) },
    )

    /** A tap on a calibrated printed icon → render that page and Share/Email it. */
    private fun handleZoneTap(zone: com.inkvault.zones.ActionZone, pageId: String) {
        appScope.launch {
            val strokes = db.strokeDao().strokesForPage(pageId)
            val bmp = com.inkvault.share.PageRender.renderPage(
                strokes,
                points = { exportJson.decodeFromString(ListSerializer(Point.serializer()), it.pointsJson) },
            ) ?: return@launch
            val png = zone.action == com.inkvault.zones.ZoneAction.SHARE_PNG ||
                zone.action == com.inkvault.zones.ZoneAction.EMAIL_PNG
            val fmt = if (png) com.inkvault.share.PageShare.Format.PNG else com.inkvault.share.PageShare.Format.PDF
            val uri = com.inkvault.share.PageShare.fileUri(appContext, bmp, fmt) ?: return@launch
            when (zone.action) {
                com.inkvault.zones.ZoneAction.SHARE_PNG, com.inkvault.zones.ZoneAction.SHARE_PDF ->
                    com.inkvault.share.PageShare.share(appContext, uri, fmt.mime)
                com.inkvault.zones.ZoneAction.EMAIL_PNG, com.inkvault.zones.ZoneAction.EMAIL_PDF ->
                    com.inkvault.share.PageShare.email(appContext, uri, fmt.mime)
            }
        }
    }

    /** Calibration: capture the next traced outline as [left,top,right,bottom] (then auto-clears). */
    fun captureNextTrace(onTrace: (Float, Float, Float, Float) -> Unit) { ingestor.onCalibrationTrace = onTrace }

    /** Cancel a pending [captureNextTrace] so no stroke is consumed. */
    fun cancelTraceCapture() { ingestor.onCalibrationTrace = null }

    /** Live ink signals (last dot time, page validity, pen-down) for the diagnostic + stall alerts. */
    val captureSignals = CaptureSignals()

    val penManager = PenConnectionManager(
        sdk = penSdk,
        prefs = penPrefs,
        scope = appScope,
        // While the Capture Lab is recording, dots go only to the log (consumed, not inked); otherwise
        // feed the diagnostic/stall signals, then persist the dot (never blocks the BLE thread).
        onDot = { dot -> if (!captureLog.onDot(dot)) { captureSignals.onDot(dot); ingestor.onDot(dot) } },
        // A password the user typed that the pen accepted → remember it (encrypted) only if the user
        // opted into "remember for 30 days"; otherwise we keep asking every connect.
        onPasswordAccepted = { if (rememberPassword) penPassword.set(it) },
        // The user disabled the pen password → forget the stored secret (nothing to auto-unlock).
        onPasswordCleared = { penPassword.clear() },
    )

    /** User-initiated export of one page (the page-detail action). Runs off the main thread. */
    suspend fun exportPageNow(pageId: String): ExportOutcome = withContext(Dispatchers.IO) {
        val provider = currentStorageProvider() ?: return@withContext ExportOutcome.NO_TARGET
        if (exportEngine.exportSingle(pageId, provider)) ExportOutcome.DONE else ExportOutcome.FAILED
    }

    /** Resolve the user's selected target at export time, or null if it isn't usable yet. */
    suspend fun currentStorageProvider(): StorageProvider? {
        val (method, folderUri, endpoint) = settings.snapshot()
        return when (method) {
            SyncMethod.LOCAL_FOLDER ->
                folderUri.takeIf { it.isNotEmpty() }?.let { LocalFolderProvider(appContext, Uri.parse(it)) }
            SyncMethod.TAILSCALE_PUSH ->
                endpoint.takeIf { it.isNotEmpty() }?.let { TailscalePushProvider(it) }
            SyncMethod.LOCAL_ONLY -> LocalOnlyProvider(appContext)
        }
    }

    /** Pulls pages the pen stored offline on (re)connect; idempotent. Phase 2. */
    val offlineSync = OfflineSync(
        sdk = penSdk,
        organizer = organizer,
        ingestDao = db.ingestDao(),
        scope = appScope,
    )

    /** Phase 5 stroke editing (delete/recolor/undo); re-queues edited pages for export. */
    val strokeEditor = StrokeEditor(
        strokeDao = db.strokeDao(),
        outboxDao = db.outboxDao(),
        onChanged = { ExportWorker.enqueue(appContext) },
    )

    val repository = NoteRepository(
        notebookDao = db.notebookDao(),
        pageDao = db.pageDao(),
        strokeDao = db.strokeDao(),
        outboxDao = db.outboxDao(),
        recordingDao = db.recordingDao(),
        tagDao = db.tagDao(),
    )

    /** Voice notes tied to a page (phone mic; local-only). The Live screen drives start/stop/play. */
    val recordingController = RecordingController(
        context = appContext,
        dao = db.recordingDao(),
        scope = appScope,
    )

    /**
     * Recover any stroke interrupted by a prior crash. The pen connection itself is owned by
     * [com.inkvault.pen.PenForegroundService] (started from the Activity once BLE permissions are
     * granted), not auto-connected here — connecting from Application.onCreate would run before
     * permissions and couldn't survive backgrounding.
     */
    fun onStartup() {
        appScope.launch { ingestor.recover() }
    }

    companion object {
        /** How long a remembered pen password stays valid before we ask again. */
        private const val REMEMBER_DAYS = 30

        @Volatile private var instance: ServiceLocator? = null

        fun from(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context).also { instance = it }
            }
    }
}
