package com.visionaid.app.ml

import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.utils.Constants
import java.util.concurrent.atomic.AtomicLong

/**
 * Frame-level scheduling decisions for the processing pipeline (spec 12.2):
 *
 *  - counts frames and decides which analyzers run (OCR every 3rd frame),
 *  - computes a scene "signature" (set of class labels) and detects
 *    significant scene changes (drives the VLM context trigger),
 *  - throttles the rate at which frames are pushed to inference.
 */
class FrameAnalyzer {

    private val frameCounter = AtomicLong(0)
    private var lastInferenceAt = 0L
    private var lastSignature: Set<String> = emptySet()

    /** Sequence number for the next frame. */
    fun nextSequence(): Long = frameCounter.incrementAndGet()

    /** True when enough time has passed since the last inference run. */
    fun shouldRunInference(minIntervalMs: Long = DEFAULT_INFERENCE_INTERVAL_MS): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastInferenceAt < minIntervalMs) return false
        lastInferenceAt = now
        return true
    }

    /** OCR runs on every 3rd processed frame (spec 4.2, mode 1). */
    fun shouldRunOcr(sequence: Long): Boolean = sequence % Constants.OCR_FRAME_INTERVAL == 0L

    /** Scene signature = set of detected labels. */
    fun computeSceneSignature(objects: List<DetectedObject>): Set<String> =
        objects.map { it.label }.toSet()

    /**
     * Jaccard similarity between signatures; a drop below 0.5 means the
     * scene significantly changed (spec 5.1c) — e.g. the user entered a new
     * room or the camera moved drastically.
     */
    fun isSignificantSceneChange(current: Set<String>): Boolean {
        val previous = lastSignature
        lastSignature = current
        if (previous.isEmpty() || current.isEmpty()) return false
        val intersection = previous.intersect(current).size.toFloat()
        val union = previous.union(current).size.toFloat()
        return (intersection / union) < SCENE_CHANGE_JACCARD
    }

    fun reset() {
        frameCounter.set(0)
        lastInferenceAt = 0L
        lastSignature = emptySet()
    }

    private companion object {
        // ~8-10 FPS target for inference (spec: 5-10 FPS).
        const val DEFAULT_INFERENCE_INTERVAL_MS = 125L
        const val SCENE_CHANGE_JACCARD = 0.5f
    }
}
