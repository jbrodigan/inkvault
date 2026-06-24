package com.inkvault.repo

import com.inkvault.data.NotebookDao
import com.inkvault.data.NotebookEntity
import com.inkvault.data.OutboxDao
import com.inkvault.data.PageDao
import com.inkvault.data.PageEntity
import com.inkvault.data.Point
import com.inkvault.data.RecordingDao
import com.inkvault.data.RecordingEntity
import com.inkvault.data.PageTag
import com.inkvault.data.StrokeDao
import com.inkvault.data.StrokeEntity
import com.inkvault.data.TagDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Snapshot for the capture notification (notebook + page + live stroke count). */
data class LiveCaptureStatus(val notebookTitle: String?, val page: Int?, val strokeCount: Int)

/** Split a search query into lower-cased words (whitespace-separated), dropping blanks. */
internal fun queryTerms(query: String): List<String> =
    query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

/** True if [text] contains every term (case-insensitive); null text (untranscribed) never matches. */
internal fun matchesAllTerms(text: String?, terms: List<String>): Boolean {
    val haystack = (text ?: return false).lowercase()
    return terms.all { haystack.contains(it) }
}

/**
 * Build a safe SQLite-FTS MATCH expression from query [terms]: each word is stripped to
 * alphanumerics (so stray punctuation/quotes can't break MATCH syntax or inject operators) and
 * given a `*` prefix wildcard; words are space-joined, which FTS treats as AND. Returns "" when
 * nothing usable remains, so the caller can skip the FTS query rather than run an invalid match.
 */
internal fun ftsMatch(terms: List<String>): String =
    terms.mapNotNull { term -> term.filter(Char::isLetterOrDigit).ifEmpty { null }?.let { "$it*" } }
        .joinToString(" ")

/**
 * Read API for the UI. Everything the screens show comes from the database —
 * the single source of truth — so what's on screen always equals what's stored
 * and what's queued for sync.
 */
class NoteRepository(
    private val notebookDao: NotebookDao,
    private val pageDao: PageDao,
    private val strokeDao: StrokeDao,
    private val outboxDao: OutboxDao,
    private val recordingDao: RecordingDao,
    private val tagDao: TagDao,
) {
    private val json = Json

    // --- tags (Phase E) ---
    fun tagsForPage(pageId: String): Flow<List<String>> = tagDao.observeForPage(pageId)
    fun allTags(): Flow<List<String>> = tagDao.observeAllTags()
    fun pagesWithTag(tag: String): Flow<List<PageEntity>> = tagDao.observePagesWithTag(tag)
    suspend fun addTag(pageId: String, tag: String) = tagDao.add(PageTag(pageId, tag.trim()))
    suspend fun removeTag(pageId: String, tag: String) = tagDao.remove(pageId, tag)

    fun notebooks(): Flow<List<NotebookEntity>> = notebookDao.observeAll()
    fun pages(notebookId: String): Flow<List<PageEntity>> = pageDao.observeByNotebook(notebookId)
    fun strokes(pageId: String): Flow<List<StrokeEntity>> = strokeDao.observeByPage(pageId)

    /**
     * Search: pages whose imported transcript contains **all** query words (in any order), plus
     * every page of any notebook whose title contains all the words. Transcript hits come first
     * (more specific); a page matching both ways appears once. Multi-word, order-independent
     * matching is the practical win of full-text search ("milk eggs" finds a page with "eggs and
     * milk") delivered without a schema change: we let SQL `LIKE` on the most selective word do the
     * coarse DB scan, then [matchesAllTerms] enforces the rest in memory — fine at one person's
     * notebook scale. Transcript search runs through the SQLite FTS4 index ([PageFts]) — porter
     * stemming and word/prefix matching ("run" finds "running"); title search stays in memory since
     * titles are short and not FTS-indexed. The instrumented FtsMigrationTest validates the index +
     * its 8→9 migration on a real Android image.
     */
    suspend fun searchPages(query: String): List<PageEntity> {
        val terms = queryTerms(query)
        if (terms.isEmpty()) return emptyList()
        val match = ftsMatch(terms)
        val byTranscript = if (match.isBlank()) emptyList() else pageDao.searchByTranscriptFts(match)
        val seed = terms.maxByOrNull { it.length }!! // the longest word is usually the most selective
        val byTitle = notebookDao.searchByTitle(seed)
            .filter { matchesAllTerms(it.title, terms) }
            .flatMap { pageDao.pagesInNotebook(it.id) }
        return (byTranscript + byTitle).distinctBy { it.id }
    }

    /** Most recently inked pages, for the Search screen's empty-query recents list. */
    suspend fun recentPages(limit: Int = 20): List<PageEntity> = pageDao.recentPages(limit)

    /** Voice recordings tied to a page (oldest first). */
    fun recordings(pageId: String): Flow<List<RecordingEntity>> = recordingDao.observeByPage(pageId)

    /** Whether a page / notebook has any voice notes (library thumbnail badge). */
    fun pageHasAudio(pageId: String): Flow<Boolean> = recordingDao.observeHasForPage(pageId)
    fun notebookHasAudio(notebookId: String): Flow<Boolean> = recordingDao.observeHasForNotebook(notebookId)

    suspend fun setRecordingTitle(id: String, title: String) = recordingDao.setTitle(id, title)

    /** The page currently receiving ink (most recently touched), or null — for the live-capture view. */
    fun livePage(): Flow<PageEntity?> = pageDao.observeLatest()

    /** Total captured pages (the "pages safe" figure in the disconnect alert). */
    fun pageCount(): Flow<Int> = pageDao.observeCount()

    /** Live capture status for the persistent notification: notebook title, page #, stroke count. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun liveCaptureStatus(): Flow<LiveCaptureStatus> =
        pageDao.observeLatest().flatMapLatest { page ->
            if (page == null) {
                kotlinx.coroutines.flow.flowOf(LiveCaptureStatus(null, null, 0))
            } else {
                combine(strokeDao.observeByPage(page.id), notebookDao.observeAll()) { strokes, notebooks ->
                    LiveCaptureStatus(
                        notebookTitle = notebooks.firstOrNull { it.id == page.notebookId }?.title,
                        page = page.page,
                        strokeCount = strokes.size,
                    )
                }
            }
        }

    /** Live count of strokes captured but not yet exported to the NAS (Phase 3). */
    fun pendingUploads(): Flow<Int> = outboxDao.observeBacklog()

    suspend fun rename(notebookId: String, title: String) = notebookDao.rename(notebookId, title)

    /** Mark a notebook finished so reusing the same Ncode model starts a fresh notebook. */
    suspend fun finishNotebook(notebookId: String) = notebookDao.lock(notebookId)

    fun decodePoints(stroke: StrokeEntity): List<Point> =
        json.decodeFromString(ListSerializer(Point.serializer()), stroke.pointsJson)
}
