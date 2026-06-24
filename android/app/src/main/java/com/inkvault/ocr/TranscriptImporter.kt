package com.inkvault.ocr

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.inkvault.data.PageDao
import com.inkvault.export.ExportSidecar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Imports the OCR transcripts the external NAS watcher writes back into the sync folder. The watcher
 * drops a `<base>.txt` next to each page's `<base>.png`; since export now files pages under
 * type/label sub-folders (e.g. `pnb/Work/PNB_Work_Pg038.*`), we walk the tree recursively and map
 * each `.txt` back to its page:
 *  - flat `<pageId>.txt` → the base *is* the page id (back-compat with the old naming);
 *  - human path → read the page id from the sibling `<base>.json` sidecar.
 *
 * Idempotent: a page whose transcript already matches the file is skipped. Reads only the user's
 * chosen local folder — no network, nothing outside the granted tree.
 */
class TranscriptImporter(
    private val context: Context,
    private val pageDao: PageDao,
    private val folderUri: suspend () -> String,
    /** Called after a page's transcript changes, so it can be re-queued for export (refreshes its .md). */
    private val onImported: suspend (String) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** @return how many pages had their transcript updated. */
    suspend fun importPending(): Int = withContext(Dispatchers.IO) {
        val uriStr = folderUri()
        if (uriStr.isBlank()) return@withContext 0
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) }.getOrNull()
            ?: return@withContext 0

        var updated = 0
        val stack = ArrayDeque<DocumentFile>().apply { addLast(root) }
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            for (file in dir.listFiles()) {
                if (file.isDirectory) { stack.addLast(file); continue }
                val name = file.name ?: continue
                if (!name.endsWith(".txt")) continue
                val base = name.removeSuffix(".txt")
                val page = (pageDao.byId(base) ?: sidecarPageId(dir, base)?.let { pageDao.byId(it) })
                    ?: continue
                val text = runCatching {
                    context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull() ?: continue
                if (page.transcript != text) {
                    pageDao.setTranscriptIndexed(page.id, text)
                    onImported(page.id)
                    updated++
                }
            }
        }
        updated
    }

    /** Page id from the sibling `<base>.json` export sidecar, or null if absent/unparseable. */
    private fun sidecarPageId(dir: DocumentFile, base: String): String? {
        val sidecar = dir.findFile("$base.json") ?: return null
        val bytes = runCatching {
            context.contentResolver.openInputStream(sidecar.uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        return runCatching {
            json.decodeFromString(ExportSidecar.serializer(), bytes.decodeToString()).pageId
        }.getOrNull()
    }
}
