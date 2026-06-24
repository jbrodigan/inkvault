package com.inkvault.translate

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Translates note text with a quality-first, privacy-first, tiered engine (see RESEARCH_AND_ROADMAP.md
 * and tools/TRANSLATION_SETUP.md):
 *
 *  1. **Primary** — a translation-grade LLM (EuroLLM-9B / Tower+ 9B) on the user's GPU box via an
 *     OpenAI-compatible endpoint. One model covers every language (no per-language downloads) and
 *     clearly beats Google-Translate-quality NMT; WMT24 human eval puts Tower-class LLMs at or above
 *     the commercial providers. Traffic stays on the user's own tailnet.
 *  2. **Fallback** — Google ML Kit on-device translation (downloadable ~30 MB packs). The honest
 *     "offline, basic quality" tier for when the GPU box isn't reachable.
 *
 * Source language is auto-detected on-device (ML Kit Language ID), so detection stays private even
 * when the high-quality translation runs on the box.
 */
class Translator(
    private val endpoint: suspend () -> String,   // base URL of the LLM box ("" = no LLM configured)
    private val model: suspend () -> String,
) {
    enum class Engine { LLM, ON_DEVICE }
    data class Result(val text: String, val engine: Engine, val source: String?)

    /**
     * @param target a language code (e.g. "es", "fr", "ja").
     * @param source a code, or null/[AUTO] to detect on-device.
     */
    suspend fun translate(text: String, target: String, source: String? = null): Result? =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext null
            val src = source?.takeIf { it.isNotBlank() && it != AUTO } ?: detectLanguage(text)
            if (src != null && src == target) return@withContext null  // already in the target language

            // Quality path: the user's translation LLM.
            val base = endpoint().trim()
            val mdl = model().trim()
            if (base.isNotEmpty() && mdl.isNotEmpty()) {
                runCatching { translateViaLlm(text, src, target, base, mdl) }.getOrNull()
                    ?.let { return@withContext Result(it, Engine.LLM, src) }
                // fall through to on-device when the box is unreachable
            }

            // Offline fallback: ML Kit (needs a recognizable source + target).
            val mlSource = TranslateLanguage.fromLanguageTag(src ?: return@withContext null)
            val mlTarget = TranslateLanguage.fromLanguageTag(target)
            if (mlSource == null || mlTarget == null) return@withContext null
            runCatching { translateOnDevice(text, mlSource, mlTarget) }.getOrNull()
                ?.let { return@withContext Result(it, Engine.ON_DEVICE, src) }
            null
        }

    /** On-device language code of [text] (null if undetermined). */
    suspend fun detectLanguage(text: String): String? = withContext(Dispatchers.IO) {
        val client = LanguageIdentification.getClient()
        try {
            Tasks.await(client.identifyLanguage(text)).takeIf { it != "und" }
        } catch (e: Exception) {
            null
        } finally {
            client.close()
        }
    }

    private fun translateViaLlm(text: String, source: String?, target: String, base: String, model: String): String {
        val sys = buildString {
            append("You are an expert literary translator. Translate the user's text ")
            if (source != null) append("from ").append(source).append(' ')
            append("into ").append(target)
            append(". Preserve meaning, tone, names, and the original line breaks and list structure. ")
            append("Output ONLY the translation — no notes, no quotes, no commentary, no <think>.")
        }
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", sys))
                    .put(JSONObject().put("role", "user").put("content", text)),
            )
            .toString()
        val conn = (URL(base.trimEnd('/') + "/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("translation endpoint returned $code")
            val content = JSONObject(resp).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            return THINK.replace(content, "").trim()  // strip any reasoning leakage
        } finally {
            conn.disconnect()
        }
    }

    private fun translateOnDevice(text: String, source: String, target: String): String {
        val client = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
        )
        try {
            Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            return Tasks.await(client.translate(text))
        } finally {
            client.close()
        }
    }

    companion object {
        const val AUTO = "auto"
        private val THINK = Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    }
}

/** The selectable languages: ML Kit's supported set (both engines handle these), labelled for the UI. */
object Languages {
    /** (code, display name) sorted by name. */
    val all: List<Pair<String, String>> by lazy {
        TranslateLanguage.getAllLanguages()
            .map { code -> code to java.util.Locale.forLanguageTag(code).displayLanguage.ifBlank { code } }
            .sortedBy { it.second.lowercase() }
    }

    fun nameOf(code: String?): String =
        if (code == null) "Auto-detect"
        else all.firstOrNull { it.first == code }?.second
            ?: java.util.Locale.forLanguageTag(code).displayLanguage.ifBlank { code }
}
