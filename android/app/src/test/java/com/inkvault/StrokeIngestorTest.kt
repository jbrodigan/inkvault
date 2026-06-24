package com.inkvault

import com.google.common.truth.Truth.assertThat
import com.inkvault.data.SyncState
import com.inkvault.ingest.StrokeIngestor
import com.inkvault.organize.AutoOrganizer
import com.inkvault.pen.PenDot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class StrokeIngestorTest {

    private val ids = AtomicInteger(0)
    private var clock = 1000L

    private fun dot(phase: PenDot.Phase, x: Float, page: Int = 1) = PenDot(
        section = 3, owner = 27, book = 603, page = page,
        x = x, y = x, pressure = 0.5f, phase = phase, timestamp = clock++, color = 0xFF000000.toInt(),
    )

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope): Fix {
        val stroke = FakeStrokeDao(); val outbox = FakeOutboxDao()
        val pending = FakePendingDotDao(); val page = FakePageDao(); val nb = FakeNotebookDao()
        val ingest = FakeIngestDao(stroke, outbox, pending)
        val organizer = AutoOrganizer(nb, page, now = { clock }, newId = { "id-${ids.incrementAndGet()}" })
        val ingestor = StrokeIngestor(
            ingestDao = ingest, pendingDao = pending, pageDao = page, organizer = organizer,
            scope = scope, flushEveryNDots = 2, now = { clock }, newId = { "s-${ids.incrementAndGet()}" },
        )
        return Fix(ingestor, stroke, outbox, pending, page)
    }

    private class Fix(
        val ingestor: StrokeIngestor,
        val strokeDao: FakeStrokeDao,
        val outboxDao: FakeOutboxDao,
        val pendingDao: FakePendingDotDao,
        val pageDao: FakePageDao,
    )

    @Test
    fun `a completed stroke is persisted AND enqueued for sync in one go`() = runTest(UnconfinedTestDispatcher()) {
        val f = fixture(backgroundScope)
        f.ingestor.onDot(dot(PenDot.Phase.DOWN, 0f))
        f.ingestor.onDot(dot(PenDot.Phase.MOVE, 1f))
        f.ingestor.onDot(dot(PenDot.Phase.MOVE, 2f))
        f.ingestor.onDot(dot(PenDot.Phase.UP, 3f))
        advanceUntilIdle()

        // Source-of-truth invariant: 1 stroke stored == 1 stroke queued for upload.
        assertThat(f.strokeDao.byId).hasSize(1)
        assertThat(f.outboxDao.rows).hasSize(1)
        val stroke = f.strokeDao.byId.values.first()
        assertThat(stroke.syncState).isEqualTo(SyncState.PENDING)
        assertThat(f.outboxDao.rows.keys).containsExactly(stroke.uuid)
        // Pending scratch is cleared once committed.
        assertThat(f.pendingDao.rows).isEmpty()
        // The page was auto-created (organization happened with no user action).
        assertThat(f.pageDao.byId).hasSize(1)
    }

    @Test
    fun `dots are flushed to pending scratch mid-stroke so a crash loses nothing`() = runTest(UnconfinedTestDispatcher()) {
        val f = fixture(backgroundScope)
        f.ingestor.onDot(dot(PenDot.Phase.DOWN, 0f))   // flushes immediately
        f.ingestor.onDot(dot(PenDot.Phase.MOVE, 1f))
        f.ingestor.onDot(dot(PenDot.Phase.MOVE, 2f))   // hits flushEveryNDots=2 → flush
        advanceUntilIdle()

        // No PEN_UP yet, but dots are already durable in pending_dots.
        assertThat(f.pendingDao.rows.size).isAtLeast(2)
        assertThat(f.strokeDao.byId).isEmpty()
    }

    @Test
    fun `the wake-up tap (first isolated dot on a page) leaves no ink`() = runTest(UnconfinedTestDispatcher()) {
        val f = fixture(backgroundScope)
        // A bare tap to wake the pen: down + up at essentially one spot, the first mark on the page.
        f.ingestor.onDot(dot(PenDot.Phase.DOWN, 10f))
        f.ingestor.onDot(dot(PenDot.Phase.UP, 10.4f))
        advanceUntilIdle()

        // No phantom stroke, nothing queued for sync, and no empty page conjured.
        assertThat(f.strokeDao.byId).isEmpty()
        assertThat(f.outboxDao.rows).isEmpty()
        assertThat(f.pageDao.byId).isEmpty()
        // The press's crash-scratch dot was cleared, so a restart can't resurrect it as a stroke.
        assertThat(f.pendingDao.rows).isEmpty()
        f.ingestor.recover()
        advanceUntilIdle()
        assertThat(f.strokeDao.byId).isEmpty()
    }

    @Test
    fun `a period right after writing is kept, not mistaken for a wake tap`() = runTest(UnconfinedTestDispatcher()) {
        val f = fixture(backgroundScope)
        // Real writing first (a line — spread exceeds the tap threshold).
        f.ingestor.onDot(dot(PenDot.Phase.DOWN, 0f))
        f.ingestor.onDot(dot(PenDot.Phase.MOVE, 2f))
        f.ingestor.onDot(dot(PenDot.Phase.UP, 4f))
        // Then a tiny tap a few ms later — a real period, NOT isolated, so it must survive.
        f.ingestor.onDot(dot(PenDot.Phase.DOWN, 6f))
        f.ingestor.onDot(dot(PenDot.Phase.UP, 6.3f))
        advanceUntilIdle()

        assertThat(f.strokeDao.byId).hasSize(2)
    }

    @Test
    fun `a small but real isolated mark (wider than the wake threshold) is kept`() = runTest(UnconfinedTestDispatcher()) {
        val f = fixture(backgroundScope)
        // First mark on the page, so "isolated", but it travels ~1.5 units — wider than WAKE_EPS,
        // so it's a genuine tiny stroke (a short diagonal), not the pen's near-zero wake tap.
        f.ingestor.onDot(dot(PenDot.Phase.DOWN, 10f))
        f.ingestor.onDot(dot(PenDot.Phase.UP, 11.5f))
        advanceUntilIdle()

        assertThat(f.strokeDao.byId).hasSize(1)
    }

    @Test
    fun `a tap after a long idle (the pen slept) is dropped as a re-wake`() = runTest(UnconfinedTestDispatcher()) {
        val f = fixture(backgroundScope)
        f.ingestor.onDot(dot(PenDot.Phase.DOWN, 0f))
        f.ingestor.onDot(dot(PenDot.Phase.UP, 4f))   // real writing → 1 stroke
        advanceUntilIdle()
        assertThat(f.strokeDao.byId).hasSize(1)

        clock += 120_000L                            // pen sleeps for two minutes
        f.ingestor.onDot(dot(PenDot.Phase.DOWN, 20f))
        f.ingestor.onDot(dot(PenDot.Phase.UP, 20.4f)) // wake tap on resume → dropped
        advanceUntilIdle()

        assertThat(f.strokeDao.byId).hasSize(1)       // still just the one real stroke
    }

    @Test
    fun `a tap on an action zone fires the zone, never ink, even as the first mark`() = runTest(UnconfinedTestDispatcher()) {
        val stroke = FakeStrokeDao(); val outbox = FakeOutboxDao(); val pending = FakePendingDotDao()
        val pageDao = FakePageDao(); val nb = FakeNotebookDao(); val ingest = FakeIngestDao(stroke, outbox, pending)
        val organizer = AutoOrganizer(nb, pageDao, now = { clock }, newId = { "id-${ids.incrementAndGet()}" })
        var fired: com.inkvault.zones.ZoneAction? = null
        val zone = com.inkvault.zones.ActionZone("z", com.inkvault.zones.ZoneAction.SHARE_PNG, 9f, 9f, 12f, 12f)
        val ingestor = StrokeIngestor(
            ingestDao = ingest, pendingDao = pending, pageDao = pageDao, organizer = organizer,
            scope = backgroundScope, now = { clock }, newId = { "s-${ids.incrementAndGet()}" },
            actionZones = { listOf(zone) },
            onZoneTap = { z, _ -> fired = z.action },
        )
        // First mark on the page is a tap inside the zone: the zone wins over both ink and the wake-drop.
        ingestor.onDot(dot(PenDot.Phase.DOWN, 10f))
        ingestor.onDot(dot(PenDot.Phase.UP, 10.4f))
        advanceUntilIdle()

        assertThat(fired).isEqualTo(com.inkvault.zones.ZoneAction.SHARE_PNG)
        assertThat(stroke.byId).isEmpty()    // no ink left
        assertThat(pending.rows).isEmpty()   // crash-scratch cleared
    }

    @Test
    fun `recover() promotes an interrupted stroke into a real stroke on next launch`() = runTest(UnconfinedTestDispatcher()) {
        val f = fixture(backgroundScope)
        f.ingestor.onDot(dot(PenDot.Phase.DOWN, 0f))
        f.ingestor.onDot(dot(PenDot.Phase.MOVE, 1f))
        f.ingestor.onDot(dot(PenDot.Phase.MOVE, 2f))   // flushed to pending, then "crash" (no UP)
        advanceUntilIdle()
        assertThat(f.strokeDao.byId).isEmpty()

        // Simulate next app launch.
        f.ingestor.recover()
        advanceUntilIdle()

        assertThat(f.strokeDao.byId).hasSize(1)
        assertThat(f.outboxDao.rows).hasSize(1)
        assertThat(f.pendingDao.rows).isEmpty()
    }
}
