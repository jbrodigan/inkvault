package com.inkvault.ocr

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionContext
import com.google.mlkit.vision.digitalink.WritingArea
import com.inkvault.data.Point
import com.inkvault.data.StrokeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device handwriting transcription straight from the captured strokes — offline (after a one-time
 * model download), no GPU and no NAS. ML Kit Digital Ink consumes exactly our X/Y/time pen data, and
 * stroke order beats image-only OCR for cursive. This complements the heavier OCR-host/Ollama pipeline:
 * it's the instant, fully-local path; the server pipeline stays the best-quality one.
 *
 * The model for [languageTag] is downloaded from Google once on first use, then everything is local.
 *
 * Note: one language, downloaded on demand. A multi-language picker is the upgrade path; the
 * recognizer is created/closed per call so nothing is held open.
 */
class OnDeviceInk(languageTag: String = java.util.Locale.getDefault().toLanguageTag()) {

    private val modelManager = RemoteModelManager.getInstance()

    // Recognize in the device's language when ML Kit ships a digital-ink model for it, else fall back
    // (region-less tag, then en-US) so a non-English device still gets a working recognizer.
    private val model: DigitalInkRecognitionModel? =
        listOf(languageTag, languageTag.substringBefore('-'), "en-US")
            .firstNotNullOfOrNull { tag ->
                runCatching {
                    DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
                        ?.let { DigitalInkRecognitionModel.builder(it).build() }
                }.getOrNull()
            }

    /**
     * Recognize a page's strokes → text, or null if there's nothing usable (no strokes, or the model
     * couldn't be resolved/downloaded). ML Kit's Task API is awaited off the main thread.
     *
     * Coordinates are **scaled** first: ML Kit was trained on screen-pixel-scale ink, but our points
     * are in Ncode paper units (a whole page spans only ~0..100, characters a few units tall). Fed
     * raw, recognition is poor/empty — so we map the page's bounding box up to a pixel-scale writing
     * area (aspect preserved) and pass that area as context.
     */
    suspend fun transcribe(
        strokes: List<StrokeEntity>,
        decode: (StrokeEntity) -> List<Point>,
    ): String? = withContext(Dispatchers.IO) {
        val model = model ?: return@withContext null
        val decoded = strokes.map(decode).filter { it.isNotEmpty() }
        if (decoded.isEmpty()) return@withContext null

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (pts in decoded) for (p in pts) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        val scale = TARGET / maxOf(maxX - minX, maxY - minY, 1e-3f)
        fun sx(x: Float) = (x - minX) * scale + MARGIN
        fun sy(y: Float) = (y - minY) * scale + MARGIN

        // ML Kit's native lib (libdigitalink.so) isn't 16 KB page-aligned, so on a device booted
        // with 16 KB pages the recognizer fails to load (UnsatisfiedLinkError). Google ships no
        // aligned build yet (mlkit#938), so we can't fix the .so — but we can fail soft: treat any
        // ML Kit failure as "no on-device transcript" so the caller falls back to the server OCR
        // path (the higher-quality one anyway) instead of crashing. Note: drop the wrapper once
        // Google publishes a 16 KB-aligned digital-ink artifact.
        runCatching {
            if (!Tasks.await(modelManager.isModelDownloaded(model))) {
                Tasks.await(modelManager.download(model, DownloadConditions.Builder().build()))
            }

            val inkBuilder = Ink.builder()
            for (pts in decoded) {
                val strokeBuilder = Ink.Stroke.builder()
                for (p in pts) strokeBuilder.addPoint(Ink.Point.create(sx(p.x), sy(p.y), p.t))
                inkBuilder.addStroke(strokeBuilder.build())
            }

            val context = RecognitionContext.builder()
                .setWritingArea(WritingArea((maxX - minX) * scale + 2 * MARGIN, (maxY - minY) * scale + 2 * MARGIN))
                .build()
            val recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build(),
            )
            try {
                Tasks.await(recognizer.recognize(inkBuilder.build(), context))
                    .candidates.firstOrNull()?.text?.takeIf { it.isNotBlank() }
            } finally {
                recognizer.close()
            }
        }.getOrNull()
    }

    private companion object {
        const val TARGET = 1000f  // map the page's longer side to ~this many px (ML Kit's scale)
        const val MARGIN = 16f
    }
}
