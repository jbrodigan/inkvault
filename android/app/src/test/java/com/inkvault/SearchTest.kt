package com.inkvault

import com.google.common.truth.Truth.assertThat
import com.inkvault.data.NotebookEntity
import com.inkvault.data.PageEntity
import com.inkvault.repo.ftsMatch
import com.inkvault.repo.matchesAllTerms
import com.inkvault.repo.queryTerms
import com.inkvault.ui.snippet
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchTest {

    private fun page(id: String, transcript: String?, notebookId: String = "nb") = PageEntity(
        id = id, notebookId = notebookId, addressKey = "3.27.603.1",
        section = 3, owner = 27, book = 603, page = 1, firstSeenAt = 0, lastInkAt = id.last().code.toLong(),
        transcript = transcript,
    )

    private fun notebook(id: String, title: String) = NotebookEntity(
        id = id, book = 603, instanceSeq = 0, locked = false, title = title, createdAt = 0,
    )

    /** Mirrors NoteRepository.searchPages: all words must match (any order); transcript hits first,
     *  then title-matched notebooks' pages, deduped. Uses the same pure helpers as the repo. */
    private suspend fun search(nb: FakeNotebookDao, pg: FakePageDao, q: String): List<PageEntity> {
        val terms = queryTerms(q)
        if (terms.isEmpty()) return emptyList()
        val match = ftsMatch(terms)
        val byTranscript = if (match.isBlank()) emptyList() else pg.searchByTranscriptFts(match)
        val seed = terms.maxByOrNull { it.length }!!
        val byTitle = nb.searchByTitle(seed)
            .filter { matchesAllTerms(it.title, terms) }
            .flatMap { pg.pagesInNotebook(it.id) }
        return (byTranscript + byTitle).distinctBy { it.id }
    }

    @Test
    fun `transcript search matches case-insensitively and skips untranscribed pages`() = runTest {
        val dao = FakePageDao()
        dao.byId["a"] = page("a", "Buy MILK and eggs")
        dao.byId["b"] = page("b", "call the dentist")
        dao.byId["c"] = page("c", null) // not transcribed yet

        assertThat(dao.searchByTranscript("milk").map { it.id }).containsExactly("a")
        assertThat(dao.searchByTranscript("zzz")).isEmpty()
    }

    @Test
    fun `search matches all words in any order, not just a contiguous substring`() = runTest {
        val nb = FakeNotebookDao(); val pg = FakePageDao()
        pg.byId["a"] = page("a", "eggs and milk")
        pg.byId["b"] = page("b", "just milk")

        // both words present, order-independent — the win over a single LIKE '%milk eggs%'
        assertThat(search(nb, pg, "milk eggs").map { it.id }).containsExactly("a")
        // a single word still matches broadly
        assertThat(search(nb, pg, "milk").map { it.id }).containsExactly("a", "b")
        // a word that appears nowhere rules the page out
        assertThat(search(nb, pg, "milk dentist")).isEmpty()
    }

    @Test
    fun `search finds pages by notebook title even with no transcript`() = runTest {
        val nb = FakeNotebookDao(); val pg = FakePageDao()
        nb.byId["work"] = notebook("work", "Work meetings")
        nb.byId["home"] = notebook("home", "Groceries")
        pg.byId["p1"] = page("p1", null, notebookId = "work")
        pg.byId["p2"] = page("p2", null, notebookId = "home")

        // "meeting" matches the Work notebook's title → its untranscribed page surfaces.
        assertThat(search(nb, pg, "meeting").map { it.id }).containsExactly("p1")
    }

    @Test
    fun `search unions transcript and title hits without duplicating a page`() = runTest {
        val nb = FakeNotebookDao(); val pg = FakePageDao()
        nb.byId["work"] = notebook("work", "Work notes")
        pg.byId["p1"] = page("p1", "remember the work deadline", notebookId = "work") // matches both ways
        pg.byId["p2"] = page("p2", null, notebookId = "work")                          // matches by title only

        val ids = search(nb, pg, "work").map { it.id }
        assertThat(ids).containsExactly("p1", "p2")  // p1 once, not twice
    }

    @Test
    fun `recent pages come back newest-inked first and capped at the limit`() = runTest {
        val pg = FakePageDao()
        // lastInkAt is derived from the id's last char code, so 'a' < 'b' < 'c'.
        pg.byId["a"] = page("a", null)
        pg.byId["b"] = page("b", null)
        pg.byId["c"] = page("c", null)

        assertThat(pg.recentPages(2).map { it.id }).containsExactly("c", "b").inOrder()
    }

    @Test
    fun `snippet windows around the match with ellipses`() {
        val text = "the quick brown fox jumps over the lazy dog and keeps on running forever"
        val s = snippet(text, "lazy")
        assertThat(s).contains("lazy")
        assertThat(s).doesNotContain("\n")
    }

    @Test
    fun `snippet falls back to a head slice when there is no match`() {
        assertThat(snippet("hello world", "absent")).isEqualTo("hello world")
    }
}
