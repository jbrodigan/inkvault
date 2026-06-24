package com.inkvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notebook: NotebookEntity)

    /** The current physical notebook for a book model = the highest-numbered instance. */
    @Query("SELECT * FROM notebooks WHERE book = :book ORDER BY instanceSeq DESC LIMIT 1")
    suspend fun activeForBook(book: Int): NotebookEntity?

    /** A specific notebook by id — the export path needs its label (title) + instanceSeq. */
    @Query("SELECT * FROM notebooks WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): NotebookEntity?

    /** Mark a notebook finished; the next ink on this book id starts a fresh instance. */
    @Query("UPDATE notebooks SET locked = 1 WHERE id = :id")
    suspend fun lock(id: String)

    @Query("UPDATE notebooks SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("SELECT * FROM notebooks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotebookEntity>>

    /** Notebooks whose title contains [q] (case-insensitive) — so search works before any OCR. */
    @Query("SELECT * FROM notebooks WHERE title LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    suspend fun searchByTitle(q: String): List<NotebookEntity>
}

@Dao
interface PageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(page: PageEntity)

    /** A page is identified within its notebook instance by Ncode page number. */
    @Query("SELECT * FROM pages WHERE notebookId = :notebookId AND page = :page LIMIT 1")
    suspend fun findInNotebook(notebookId: String, page: Int): PageEntity?

    @Query("UPDATE pages SET lastInkAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)

    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY page ASC")
    fun observeByNotebook(notebookId: String): Flow<List<PageEntity>>

    /** The page being written right now = the most recently inked one (drives the live-capture view). */
    @Query("SELECT * FROM pages ORDER BY lastInkAt DESC LIMIT 1")
    fun observeLatest(): Flow<PageEntity?>

    /** Total pages captured (for "N pages safe" in the disconnect alert). */
    @Query("SELECT COUNT(*) FROM pages")
    fun observeCount(): Flow<Int>

    /** Page metadata for an export sidecar. */
    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): PageEntity?

    /** Store the OCR transcript imported from the sync folder. */
    @Query("UPDATE pages SET transcript = :text WHERE id = :id")
    suspend fun setTranscript(id: String, text: String)

    /** Pages whose transcript contains [q] (case-insensitive), newest first — handwriting search. */
    @Query("SELECT * FROM pages WHERE transcript LIKE '%' || :q || '%' ORDER BY lastInkAt DESC")
    suspend fun searchByTranscript(q: String): List<PageEntity>

    // --- full-text transcript search (FTS4) ---

    /**
     * Pages whose transcript matches the FTS [match] expression (e.g. "milk* eggs*" = both words,
     * any order, prefix). Joined back to `pages` and ordered newest-inked first. The porter
     * tokenizer means a query stem matches inflections ("run" ↔ "running").
     */
    @Query(
        "SELECT p.* FROM pages p JOIN page_fts f ON p.id = f.pageId " +
            "WHERE f.transcript MATCH :match ORDER BY p.lastInkAt DESC",
    )
    suspend fun searchByTranscriptFts(match: String): List<PageEntity>

    @Query("DELETE FROM page_fts WHERE pageId = :id")
    suspend fun deleteFtsForPage(id: String)

    @Query("INSERT INTO page_fts(pageId, transcript) VALUES (:id, :text)")
    suspend fun insertFts(id: String, text: String)

    /**
     * The single funnel for transcript writes: persist the text on the page (source of truth) and
     * refresh its FTS row. Delete-then-insert keeps the index exact across re-imports. Sequenced
     * (not a transaction) deliberately — the FTS index is rebuildable from `pages`, so a crash
     * between the two only risks a momentarily stale search hit, never lost transcript text.
     */
    suspend fun setTranscriptIndexed(id: String, text: String) {
        setTranscript(id, text)
        deleteFtsForPage(id)
        insertFts(id, text)
    }

    /** All pages in a notebook, newest-inked first — to surface a notebook-title search match. */
    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY lastInkAt DESC")
    suspend fun pagesInNotebook(notebookId: String): List<PageEntity>

    /** Most recently inked pages — the empty-query "recents" list in Search. */
    @Query("SELECT * FROM pages ORDER BY lastInkAt DESC LIMIT :limit")
    suspend fun recentPages(limit: Int): List<PageEntity>
}

@Dao
interface StrokeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stroke: StrokeEntity)

    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY startedAt ASC")
    fun observeByPage(pageId: String): Flow<List<StrokeEntity>>

    /** All strokes on a page, for rendering an export artifact (one-shot, not observed). */
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY startedAt ASC")
    suspend fun strokesForPage(pageId: String): List<StrokeEntity>

    @Query("SELECT * FROM strokes WHERE uuid IN (:uuids)")
    suspend fun byUuids(uuids: List<String>): List<StrokeEntity>

    @Query("UPDATE strokes SET syncState = :state WHERE uuid IN (:uuids)")
    suspend fun markSync(uuids: List<String>, state: SyncState)

    @Query("SELECT COUNT(*) FROM strokes WHERE pageId = :pageId")
    suspend fun countForPage(pageId: String): Int

    // --- editing (Phase 5) ---
    @Query("DELETE FROM strokes WHERE uuid = :uuid")
    suspend fun delete(uuid: String)

    @Query("UPDATE strokes SET color = :color WHERE uuid = :uuid")
    suspend fun setColor(uuid: String, color: Int)

    @Query("UPDATE strokes SET width = :width WHERE uuid = :uuid")
    suspend fun setWidth(uuid: String, width: Float)

    /** Most recently started stroke on a page — the target of "undo". */
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestOnPage(pageId: String): StrokeEntity?
}

@Dao
interface PendingDotDao {
    @Insert suspend fun insertAll(dots: List<PendingDotEntity>)

    @Query("SELECT * FROM pending_dots WHERE pageKey = :pageKey ORDER BY seq ASC")
    suspend fun forPage(pageKey: String): List<PendingDotEntity>

    @Query("SELECT DISTINCT pageKey FROM pending_dots")
    suspend fun pageKeysWithPending(): List<String>

    @Query("DELETE FROM pending_dots WHERE pageKey = :pageKey")
    suspend fun clearPage(pageKey: String)
}

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(recording: RecordingEntity)

    @Query("UPDATE recordings SET durationMs = :durationMs WHERE id = :id")
    suspend fun setDuration(id: String, durationMs: Long)

    @Query("UPDATE recordings SET title = :title WHERE id = :id")
    suspend fun setTitle(id: String, title: String)

    @Query("SELECT * FROM recordings WHERE pageId = :pageId ORDER BY startedAt ASC")
    fun observeByPage(pageId: String): Flow<List<RecordingEntity>>

    /** Does this page have any voice notes? (drives the library thumbnail badge) */
    @Query("SELECT EXISTS(SELECT 1 FROM recordings WHERE pageId = :pageId)")
    fun observeHasForPage(pageId: String): Flow<Boolean>

    /** Does any page in this notebook have voice notes? */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM recordings r INNER JOIN pages p ON r.pageId = p.id " +
            "WHERE p.notebookId = :notebookId)",
    )
    fun observeHasForNotebook(notebookId: String): Flow<Boolean>

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(tag: PageTag)

    @Query("DELETE FROM page_tags WHERE pageId = :pageId AND tag = :tag")
    suspend fun remove(pageId: String, tag: String)

    @Query("SELECT tag FROM page_tags WHERE pageId = :pageId ORDER BY tag ASC")
    fun observeForPage(pageId: String): Flow<List<String>>

    /** One-shot tags for a page — used by export to stamp the Markdown frontmatter. */
    @Query("SELECT tag FROM page_tags WHERE pageId = :pageId ORDER BY tag ASC")
    suspend fun tagsForPage(pageId: String): List<String>

    /** All distinct tags in use, for the Library filter bar. */
    @Query("SELECT DISTINCT tag FROM page_tags ORDER BY tag ASC")
    fun observeAllTags(): Flow<List<String>>

    /** Page ids carrying a tag (newest-inked first), for the filtered Library view. */
    @Query(
        "SELECT p.* FROM pages p INNER JOIN page_tags t ON t.pageId = p.id " +
            "WHERE t.tag = :tag ORDER BY p.lastInkAt DESC",
    )
    fun observePagesWithTag(tag: String): Flow<List<PageEntity>>
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(entry: OutboxEntry)

    @Query("SELECT * FROM outbox ORDER BY enqueuedAt ASC LIMIT :limit")
    suspend fun peek(limit: Int): List<OutboxEntry>

    @Query("DELETE FROM outbox WHERE strokeUuid IN (:uuids)")
    suspend fun remove(uuids: List<String>)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE strokeUuid IN (:uuids)")
    suspend fun bumpAttempts(uuids: List<String>)

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeBacklog(): Flow<Int>
}

@Dao
interface ExportDao {
    @Query("SELECT * FROM export_records WHERE pageId = :pageId LIMIT 1")
    suspend fun find(pageId: String): ExportRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ExportRecord)
}

/**
 * The atomic write that guarantees an ingested stroke is never lost AND is
 * always queued for upload — both in one transaction (complaints #1 + #2).
 */
@Dao
abstract class IngestDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertStroke(stroke: StrokeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun enqueueOutbox(entry: OutboxEntry)

    @Query("DELETE FROM pending_dots WHERE pageKey = :pageKey")
    abstract suspend fun clearPending(pageKey: String)

    @Transaction
    open suspend fun commitStroke(stroke: StrokeEntity, pageKey: String) {
        insertStroke(stroke)
        enqueueOutbox(OutboxEntry(stroke.uuid, stroke.endedAt))
        clearPending(pageKey)
    }
}
