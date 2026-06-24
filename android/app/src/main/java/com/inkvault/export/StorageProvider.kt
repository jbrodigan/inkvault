package com.inkvault.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * One sync target. Phase 3's pluggable abstraction (DESIGN.md): exactly one implementation per
 * backend, chosen at export time from the "Sync method" setting — nothing about the target leaks
 * into the export pipeline. [write] overwrites by name, which is what makes re-export idempotent.
 *
 * [id] identifies *which* concrete target this is (folder uri / endpoint), so the idempotency
 * ledger re-exports when the user switches targets instead of stranding files on the old one.
 *
 * Note: cloud targets (Drive/Dropbox/OneDrive/WebDAV/S3) are intentionally NOT here yet — the
 * per-provider auth/scope/secret spec is still pending (DESIGN.md "Phase 3-4 spec" gap). They drop
 * in as new StorageProvider impls without touching the engine.
 */
interface StorageProvider {
    val id: String

    /** [name] may contain `/` — a type-driven sub-path like `pnb/Work/PNB_Work_Pg038.svg`. Each impl
     *  creates the intermediate folders as needed and overwrites the leaf by name. */
    suspend fun write(name: String, bytes: ByteArray)
}

/** Advisory content type from the file extension — covers every artifact the engine emits. */
internal fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "pdf" -> "application/pdf"
    "inkml" -> "application/inkml+xml"
    "md" -> "text/markdown"
    "txt" -> "text/plain"
    else -> "application/json"
}

/**
 * DEFAULT target. Writes into a SAF-granted folder; a folder-sync app you run (Syncthing,
 * Nextcloud, …) watches it. We never assume a filesystem path — only the persisted tree uri.
 */
class LocalFolderProvider(private val context: Context, private val treeUri: Uri) : StorageProvider {
    override val id: String = "local_folder:$treeUri"

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("SAF folder not accessible: $treeUri")
        val parts = name.split('/')
        // Find-or-create each sub-folder segment (findFile first so we never duplicate a dir).
        var dir = root
        for (seg in parts.dropLast(1)) {
            dir = dir.findFile(seg)?.takeIf { it.isDirectory }
                ?: dir.createDirectory(seg)
                ?: error("could not create folder '$seg' under $treeUri")
        }
        val fileName = parts.last()
        // Overwrite: SAF has no truncate, so delete-then-create keeps it idempotent (no dupes).
        dir.findFile(fileName)?.delete()
        val file = dir.createFile(mimeOf(fileName), fileName) ?: error("could not create $name in $treeUri")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) }
            ?: error("could not open $name for writing")
    }
}

/** Exports stay on-device (app files dir). For testing / "I'll grab them over adb". */
class LocalOnlyProvider(context: Context) : StorageProvider {
    override val id: String = "local_only"
    private val dir = File(context.filesDir, "exports").apply { mkdirs() }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val f = File(dir, name)
        f.parentFile?.mkdirs() // name may include sub-folders (pnb/Work/…)
        f.writeBytes(bytes) // overwrite
    }
}

/**
 * PUTs each artifact to a NAS endpoint on your tailnet. Unreachable / non-2xx → throws, so the
 * WorkManager job retries with backoff and the export is queued, never lost (DESIGN.md edge case).
 * Note: plain HttpURLConnection — no HTTP-client dependency needed for a one-shot PUT.
 */
class TailscalePushProvider(endpoint: String) : StorageProvider {
    private val base = endpoint.trimEnd('/')
    override val id: String = "tailscale:$base"

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // name may be a sub-path (pnb/Work/…); the segments are sanitised to URL-safe chars upstream,
        // so they pass through as nested path. The endpoint is expected to create parent dirs (mkdir -p).
        val conn = (URL("$base/$name").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", mimeOf(name))
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            if (code !in 200..299) error("export PUT $name -> HTTP $code")
        } finally {
            conn.disconnect()
        }
    }
}
