package com.visionaid.app.data.models

import android.graphics.Rect

/**
 * A recognized text block produced by the OCR engine (ML Kit).
 *
 * @param text       Recognized text content.
 * @param boundingBox Bounding box in pixels within the analyzed frame.
 * @param confidence Heuristic confidence in [0, 1] (based on block size /
 *                   recognized characters; ML Kit exposes no raw score).
 * @param position   Vertical position bucket: "top", "center" or "bottom".
 * @param timestamp  Recognition time.
 */
data class OCRResult(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float,
    val position: String,
    val timestamp: Long
) {
    /** True when the block is large enough to be considered a sign board. */
    fun isSignBoard(frameHeight: Int): Boolean =
        frameHeight > 0 && boundingBox.height() >= frameHeight * 0.10f
}
