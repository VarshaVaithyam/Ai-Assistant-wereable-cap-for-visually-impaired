package com.visionaid.app.ml

import android.graphics.RectF
import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.utils.Constants

/**
 * Converts bounding boxes into spatial-aware, spoken-language context
 * (spec 3.3):
 *
 *  - Horizontal: the frame is split into thirds -> left / center / right.
 *  - Vertical "clock position" for the AI assistant (9 o'clock .. 3 o'clock).
 *  - Distance: estimated from the bounding-box area fraction.
 *  - Danger: flags collision-hazard classes so warnings can be prefixed.
 */
class DirectionEstimator {

    /** Horizontal direction bucket for a normalized bounding box. */
    fun directionFor(box: RectF): String {
        val centerX = box.centerX()
        return when {
            centerX < 1f / 3f -> "left"
            centerX > 2f / 3f -> "right"
            else -> "center"
        }
    }

    /** Human phrase for a direction bucket. */
    fun directionPhrase(direction: String): String = when (direction) {
        "left" -> "to your left"
        "right" -> "to your right"
        else -> "ahead of you"
    }

    /**
     * Clock position (used by the AI assistant): left edge = 9 o'clock,
     * center = 12, right edge = 3.
     */
    fun clockPositionFor(box: RectF): String {
        val raw = 9 + (box.centerX() * 6f).toInt() // 9..15
        val hour = when {
            raw > 12 -> raw - 12
            raw == 0 -> 12
            else -> raw
        }
        return "$hour o'clock"
    }

    /** Distance band from the bounding-box area fraction (spec 3.3). */
    fun distanceBandFor(areaFraction: Float): String = when {
        areaFraction > Constants.DISTANCE_VERY_CLOSE_AREA -> "very close"
        areaFraction > Constants.DISTANCE_NEARBY_AREA -> "nearby"
        else -> "far"
    }

    /** True for objects that can hurt the user (vehicles, knives...). */
    fun isDangerous(label: String): Boolean =
        label.lowercase() in Constants.DANGEROUS_OBJECT_CLASSES

    /**
     * Enriches a raw detection (from [YoloV11Processor]) with direction,
     * distance, clock position and danger flag.
     */
    fun enrich(
        label: String,
        confidence: Float,
        box: RectF,
        timestamp: Long
    ): DetectedObject {
        val area = (box.width() * box.height()).coerceIn(0f, 1f)
        return DetectedObject(
            label = label,
            confidence = confidence,
            boundingBox = box,
            direction = directionFor(box),
            distance = distanceBandFor(area),
            clockPosition = clockPositionFor(box),
            isDangerous = isDangerous(label),
            timestamp = timestamp
        )
    }

    /** Short natural-language phrase for one object, e.g. "Person nearby, to your left". */
    fun describe(obj: DetectedObject): String {
        val distance = if (obj.distance == "far") "in the distance" else obj.distance
        return "${obj.label} $distance, ${directionPhrase(obj.direction)}"
    }
}
