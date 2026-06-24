package com.inkvault.ingest

import com.inkvault.data.IngestDao
import com.inkvault.data.PageDao
import com.inkvault.data.PendingDotDao
import com.inkvault.data.PendingDotEntity
import com.inkvault.data.Point
import com.inkvault.data.StrokeEntity
import com.inkvault.data.SyncState
import com.inkvault.organize.AutoOrganizer
import com.inkvault.pen.NcodeAddress
import com.inkvault.pen.PenDot
import com.inkvault.zones.ActionZone
import com.inkvault.zones.boundsOf
import com.inkvault.zones.tapCentre
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The core of complaint #1 — "the app started missing ~20% of strokes".
 *
 * Design: persist first, render from the database. Every [PenDot] is processed
 * serially through a [Channel] (preserving order, off the BLE callback thread):
 *
 *  - PEN_DOWN  → start a new in-progress buffer for the dot's page.
 *  - PEN_MOVE  → append; periodically flush the buffer to `pending_dots` so a
 *                crash mid-stroke loses at most the last few unflushed dots.
 *  - PEN_UP    → atomically commit the completed [StrokeEntity] AND enqueue it
 *                for sync, then clear the page's pending scratch — all in one
 *                transaction ([IngestDao.commitStroke]).
 *
 * On startup, [recover] promotes any `pending_dots` left by an interrupted
 * session into real strokes, so even an unfinished stroke survives a crash.
 *
 * There is exactly one copy of the ink — in the DB. Nothing waits in RAM for an
 * explicit "Sync", which is the failure mode of the official app.
 */
class StrokeIngestor(
    private val ingestDao: IngestDao,
    private val pendingDao: PendingDotDao,
    private val pageDao: PageDao,
    private val organizer: AutoOrganizer,
    private val scope: CoroutineScope,
    private val flushEveryNDots: Int = 8,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Current ink color for new strokes (the live color picker). 0 = brand ink (theme primary). */
    private val inkColor: () -> Int = { 0 },
    /** Current writing width multiplier for new strokes (the live size picker). 1 = base. */
    private val inkWidth: () -> Float = { 1f },
    /** Invoked after a stroke is durably committed; the app wires this to enqueue sync. */
    private val onCommitted: () -> Unit = {},
    /** The user's calibrated physical action zones (printed icons), synchronous. */
    private val actionZones: () -> List<ActionZone> = { emptyList() },
    /** Fired when a tap lands in an action zone (instead of becoming ink); carries the page tapped. */
    private val onZoneTap: (ActionZone, pageId: String) -> Unit = { _, _ -> },
) {
    /**
     * When set, the NEXT pen stroke is captured as a bounding box [left, top, right, bottom] in raw
     * Ncode coords and suppressed — the calibration "trace the printed icon" flow, which gives the
     * icon's exact extent. Cleared after one capture.
     */
    @Volatile var onCalibrationTrace: ((Float, Float, Float, Float) -> Unit)? = null

    private val json = Json
    private val channel = Channel<PenDot>(capacity = Channel.UNLIMITED)

    /** In-progress strokes keyed by page address. Survives only crash via pending_dots. */
    private val active = HashMap<String, ActiveStroke>()

    /** Pen-clock time of the last committed stroke per page, to spot the isolated wake-up tap. */
    private val lastStrokeEndByKey = HashMap<String, Long>()

    init {
        scope.launch {
            for (dot in channel) process(dot)
        }
    }

    /** Called synchronously from the pen layer for every dot. Never blocks the caller. */
    fun onDot(dot: PenDot) {
        channel.trySend(dot)
    }

    private suspend fun process(dot: PenDot) {
        val key = dot.address.key
        when (dot.phase) {
            PenDot.Phase.DOWN -> {
                active[key] = ActiveStroke(dot.address, dot.color, now()).also { it.add(dot) }
                flushPending(key) // persist the first dot immediately
            }
            PenDot.Phase.MOVE -> {
                val s = active.getOrPut(key) { ActiveStroke(dot.address, dot.color, now()) }
                s.add(dot)
                if (s.unflushedSince >= flushEveryNDots) flushPending(key)
            }
            PenDot.Phase.UP -> {
                val s = active.remove(key) ?: ActiveStroke(dot.address, dot.color, now())
                s.add(dot)
                commit(s)
            }
        }
    }

    /** Persist not-yet-committed dots of the active stroke for crash recovery. */
    private suspend fun flushPending(key: String) {
        val s = active[key] ?: return
        val toFlush = s.drainUnflushed()
        if (toFlush.isEmpty()) return
        pendingDao.insertAll(
            toFlush.mapIndexed { i, p ->
                PendingDotEntity(
                    pageKey = key,
                    seq = s.flushedCount + i,
                    color = s.color,
                    x = p.x, y = p.y, pressure = p.pressure, t = p.t,
                )
            },
        )
        s.markFlushed(toFlush.size)
    }

    /** Finalize a completed stroke atomically (stroke + outbox + clear pending). */
    private suspend fun commit(s: ActiveStroke) {
        if (s.points.isEmpty()) return
        val raw = s.points.map { it.x to it.y }
        val key = s.address.key
        // Calibration: the user traced a printed icon — capture its bounding box and suppress the ink.
        // (Clear the crash-scratch dot the press already flushed, so recover() can't resurrect it.)
        onCalibrationTrace?.let { cb ->
            boundsOf(raw)?.let { (l, t, r, b) ->
                onCalibrationTrace = null; pendingDao.clearPage(key); cb(l, t, r, b); return
            }
        }
        // A near-stationary press is a "tap", not writing.
        tapCentre(raw, TAP_EPS)?.let { (cx, cy) ->
            // 1) A tap on a printed icon fires that action instead of leaving ink.
            actionZones().firstOrNull { it.contains(cx, cy) }?.let { zone ->
                onZoneTap(zone, organizer.ensurePage(s.address).id)
                pendingDao.clearPage(key) // a tap leaves no ink — drop its crash-scratch dot too
                return
            }
            // 2) A *near-zero-spread* press (tighter than the zone tap) that is also isolated — the
            // first mark on the page, or one after a long idle gap (the pen had slept) — is the wake-up
            // tap the pen records with no ink on paper. Drop it. The tighter WAKE_EPS keeps a small but
            // real mark (a short diagonal, a tiny letter) safe; and a tap that closely follows writing
            // is kept regardless — that's real punctuation (a period, an i-dot). Losing real ink is the
            // one thing we won't do. Note: tune WAKE_EPS / WAKE_IDLE_MS once a wake-tap's spread and
            // the pen's sleep timeout are measured on hardware; the "first mark on the page" needs none.
            if (tapCentre(raw, WAKE_EPS) != null) {
                val lastEnd = lastStrokeEndByKey[key]
                if (lastEnd == null || s.points.first().t - lastEnd > WAKE_IDLE_MS) {
                    pendingDao.clearPage(key) // the wake tap leaves no ink — drop its crash-scratch dot
                    return
                }
            }
        }
        val page = organizer.ensurePage(s.address)
        val stroke = StrokeEntity(
            uuid = newId(),
            pageId = page.id,
            color = inkColor(),
            width = inkWidth(),
            startedAt = s.startedAt,
            endedAt = now(),
            pointsJson = json.encodeToString(ListSerializer(Point.serializer()), s.points),
            syncState = SyncState.PENDING,
        )
        ingestDao.commitStroke(stroke, s.address.key)
        pageDao.touch(page.id, now())
        lastStrokeEndByKey[key] = s.points.last().t
        onCommitted()
    }

    /**
     * Rebuild strokes interrupted by a crash. Each page key with leftover
     * pending dots becomes one recovered stroke. Call once on app start.
     */
    suspend fun recover() {
        for (key in pendingDao.pageKeysWithPending()) {
            val pending = pendingDao.forPage(key).sortedBy { it.seq }
            if (pending.isEmpty()) continue
            val parts = key.split(".").map { it.toInt() }
            val address = NcodeAddress(parts[0], parts[1], parts[2], parts[3])
            val page = organizer.ensurePage(address)
            val points = pending.map { Point(it.x, it.y, it.pressure, it.t) }
            val stroke = StrokeEntity(
                uuid = newId(),
                pageId = page.id,
                color = inkColor(),
                width = inkWidth(),
                startedAt = pending.first().t,
                endedAt = pending.last().t,
                pointsJson = json.encodeToString(ListSerializer(Point.serializer()), points),
                syncState = SyncState.PENDING,
            )
            ingestDao.commitStroke(stroke, key)
        }
    }

    private companion object {
        // Max Ncode-unit spread for a stroke to count as a "tap" (zone/calibration). May need tuning
        // on hardware once the Ncode coordinate scale is confirmed on the target pens.
        const val TAP_EPS = 2.0f

        // Tighter spread for the *wake-up tap*: that press is essentially a single point, much smaller
        // than a deliberate tap on a printed icon. Kept well under TAP_EPS so a small but real mark
        // (e.g. a 2-unit diagonal) is never mistaken for a wake tap and dropped.
        const val WAKE_EPS = 1.0f

        // A tap separated from the previous stroke on the page by more than this is treated as the
        // pen's wake-up tap (phantom dot) and dropped. Set to roughly the pen's sleep timeout: long
        // enough that a deliberate punctuation mark after a normal writing pause is never eaten.
        const val WAKE_IDLE_MS = 60_000L
    }

    private class ActiveStroke(
        val address: NcodeAddress,
        val color: Int,
        val startedAt: Long,
    ) {
        val points = ArrayList<Point>()
        var flushedCount = 0; private set
        var unflushedSince = 0; private set

        fun add(dot: PenDot) {
            points.add(Point(dot.x, dot.y, dot.pressure, dot.timestamp))
            unflushedSince++
        }

        fun drainUnflushed(): List<Point> =
            if (unflushedSince == 0) emptyList()
            else points.subList(points.size - unflushedSince, points.size).toList()

        fun markFlushed(n: Int) {
            flushedCount += n
            unflushedSince -= n
        }
    }
}
