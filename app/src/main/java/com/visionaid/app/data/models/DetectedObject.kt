package com.visionaid.app.data.models

import android.graphics.RectF

/**
 * A single object recognized by the YOLOv11n detector, enriched with
 * spatial context (direction + distance) computed by DirectionEstimator.
 *
 * @param label        Human-readable COCO class name, e.g. "person".
 * @param confidence   Model confidence in [0, 1].
 * @param boundingBox  Normalized (0–1) coordinates relative to the camera frame.
 * @param direction    Coarse horizontal position: "left", "center" or "right".
 * @param distance     Coarse distance band: "very close", "nearby" or "far".
 * @param clockPosition Clock-style position for the AI assistant, e.g. "2 o'clock".
 * @param isDangerous  True for collision-hazard classes (car, truck, bus, ...).
 * @param timestamp    Detection time (System.currentTimeMillis()).
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val direction: String,
    val distance: String,
    val clockPosition: String,
    val isDangerous: Boolean,
    val timestamp: Long
) {
    /** Fraction of the frame covered by the bounding box (0–1). */
    val areaFraction: Float get() = boundingBox.width() * boundingBox.height()
}
