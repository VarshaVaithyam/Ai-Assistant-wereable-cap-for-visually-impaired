package com.visionaid.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.visionaid.app.data.models.OCRResult
import com.visionaid.app.domain.models.OcrMode
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * On-device OCR using Google ML Kit text recognition (spec 4.1–4.2).
 *
 * Modes:
 *  - CONTINUOUS : every 3rd frame; only announces sign-sized blocks
 *                 (height >= 10% of the frame); 10 s per-text cooldown.
 *  - MANUAL_READ: one-shot, reads all visible text with position info.
 *  - DOCUMENT   : one-shot, text sorted top->bottom, left->right and
 *                 read as continuous prose.
 */
class TextRecognizer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Normalized text -> last announce time. Prevents re-reading same text.
    private val lastAnnounced = ConcurrentHashMap<String, Long>()

    /** Lightweight lock so a single recognizer instance isn't flooded. */
    private val mutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Runs ML Kit OCR on [bitmap] and returns raw OCR results.
     * @throws Exception when recognition fails or times out.
     */
    private suspend fun recognizeRaw(bitmap: Bitmap): List<OCRResult> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val mlText: Text = withTimeout(OCR_TIMEOUT_MS) {
            mutex.withLock {
                suspendCancellableCoroutine { cont ->
                    recognizer.process(inputImage)
                        .addOnSuccessListener { text ->
                            cont.resume(text)
                        }
                        .addOnFailureListener { e ->
                            cont.resumeWith(Result.failure(e))
                        }
                }
            }
        }
        return mlText.textBlocks.mapNotNull { block ->
            val box: Rect = block.boundingBox ?: return@mapNotNull null
            val text = block.text.trim()
            if (text.length < MIN_TEXT_LENGTH) return@mapNotNull null
            OCRResult(
                text = text,
                boundingBox = box,
                confidence = heuristicConfidence(text, box),
                position = verticalPosition(box, bitmap.height),
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * CONTINUOUS mode (spec 4.2, mode 1): announce large, fresh text only.
     * Returns the sentence to speak, or null.
     */
    suspend fun processContinuous(bitmap: Bitmap): String? {
        val results = try {
            recognizeRaw(bitmap)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Continuous OCR failed: ${t.message}")
            return null
        }
        val now = System.currentTimeMillis()
        val signs = results.filter {
            it.isSignBoard(bitmap.height) && it.confidence >= MIN_CONFIDENCE
        }
        val freshSigns = signs.filter { sign ->
            val key = normalize(sign.text)
            val last = lastAnnounced[key] ?: 0L
            val fresh = now - last >= Constants.OCR_REPEAT_COOLDOWN_MS
            if (fresh) lastAnnounced[key] = now
            fresh
        }
        if (freshSigns.isEmpty()) return null
        return "Sign detected: " + freshSigns.joinToString(". ") { it.text }
    }

    /**
     * MANUAL_READ mode (spec 4.2, mode 2): reads everything with position
     * context. [signsOnly] maps the "read sign" command.
     */
    suspend fun processManualRead(bitmap: Bitmap, signsOnly: Boolean = false): String {
        val results = try {
            recognizeRaw(bitmap)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Manual OCR failed", t)
            return "Sorry, I could not read any text right now."
        }
        val filtered = if (signsOnly) {
            results.filter { it.isSignBoard(bitmap.height) }
        } else {
            results
        }
        if (filtered.isEmpty()) {
            return if (signsOnly) "I don't see any signs nearby."
            else "I don't see any readable text."
        }
        return filtered.joinToString(". ") { result ->
            "Text at ${result.position}: ${result.text}"
        }
    }

    /**
     * DOCUMENT mode (spec 4.2, mode 3): sorted top->bottom, left->right,
     * read as continuous text.
     */
    suspend fun processDocument(bitmap: Bitmap): String {
        val results = try {
            recognizeRaw(bitmap)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Document OCR failed", t)
            return "Sorry, I could not read the document."
        }
        if (results.isEmpty()) return "I don't see any document text."
        val sorted = results.sortedWith(
            compareBy({ it.boundingBox.top }, { it.boundingBox.left })
        )
        return sorted.joinToString(separator = " ") { it.text }
    }

    /** Entry point used by DetectTextUseCase. */
    suspend fun process(bitmap: Bitmap, mode: OcrMode, signsOnly: Boolean = false): String =
        when (mode) {
            OcrMode.CONTINUOUS -> processContinuous(bitmap) ?: ""
            OcrMode.MANUAL_READ -> processManualRead(bitmap, signsOnly)
            OcrMode.DOCUMENT -> processDocument(bitmap)
        }

    fun close() {
        recognizer.close()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * ML Kit gives no confidence score, so we derive a rough one: longer
     * text with taller line boxes tends to be reliably recognized.
     */
    private fun heuristicConfidence(text: String, box: Rect): Float {
        val lengthScore = (text.length / 40f).coerceIn(0f, 1f)
        val wordScore = if (text.split(Regex("\\s+")).size >= 2) 0.2f else 0f
        return (0.6f * lengthScore + 0.4f + wordScore).coerceIn(0f, 1f)
    }

    private fun verticalPosition(box: Rect, frameHeight: Int): String {
        if (frameHeight <= 0) return "center"
        val centerY = box.exactCenterY() / frameHeight
        return when {
            centerY < 0.34f -> "top"
            centerY > 0.66f -> "bottom"
            else -> "center"
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()

    private companion object {
        const val TAG = "TextRecognizer"
        const val OCR_TIMEOUT_MS = 5_000L
        const val MIN_TEXT_LENGTH = 2
        const val MIN_CONFIDENCE = 0.6f
    }
}
