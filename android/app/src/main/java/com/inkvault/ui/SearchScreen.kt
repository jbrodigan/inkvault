package com.inkvault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvault.data.PageEntity
import kotlinx.coroutines.delay

/**
 * Search across notebook names and the OCR transcripts imported from the sync folder. Notebook-name
 * matching works immediately (no OCR needed); transcript matching lights up once the watcher writes
 * `<pageId>.txt` back. Opening the screen pulls any new transcripts, then matches live as you type.
 */
@Composable
fun SearchScreen(vm: InkViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val notebooks by vm.notebooks.collectAsStateWithLifecycle()
    val titles = remember(notebooks) { notebooks.associate { it.id to it.title } }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    var imported by remember { mutableStateOf<Int?>(null) }

    // Pull transcripts the watcher wrote back, once, on open.
    LaunchedEffect(Unit) { imported = vm.importTranscripts() }

    // Empty box shows recent pages; otherwise debounced search as the query changes.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = vm.recentPages()
        } else {
            delay(250)
            results = vm.searchPages(query)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Search", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onBack) { Text("Back") }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search notebooks and transcribed pages") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val hint = when {
            query.isBlank() && results.isEmpty() -> "Nothing captured yet. Pages appear here as you write."
            query.isBlank() -> imported?.takeIf { it > 0 }?.let { "Imported $it new transcript(s). Recent pages:" } ?: "Recent pages:"
            results.isEmpty() -> "No matches. (Notebook names match now; page text becomes searchable after OCR.)"
            else -> "${results.size} match(es)"
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { page ->
                Card(
                    Modifier.fillMaxWidth().clickable { vm.openSearchHit(page); onBack() },
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "${titles[page.notebookId] ?: "Notebook"} · page ${page.page}",
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        )
                        val sub = when {
                            !page.transcript.isNullOrBlank() -> snippet(page.transcript!!, query)
                            query.isNotBlank() -> "Matches notebook name"
                            else -> "Recently inked"
                        }
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A short context window around the first match of [q] in [text], single-lined with ellipses. */
internal fun snippet(text: String, q: String): String {
    if (q.isBlank()) return text.take(120).replace("\n", " ")
    val i = text.indexOf(q, ignoreCase = true)
    if (i < 0) return text.take(120).replace("\n", " ")
    val start = (i - 40).coerceAtLeast(0)
    val end = (i + q.length + 60).coerceAtMost(text.length)
    val body = text.substring(start, end).replace("\n", " ")
    return (if (start > 0) "…" else "") + body + (if (end < text.length) "…" else "")
}
