package com.visionaid.app.domain.usecases

import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.ml.DirectionEstimator
import javax.inject.Inject

/**
 * Converts a list of detections into a short spoken summary
 * (used by "what's ahead" and the assistant's context).
 */
class GetDirectionUseCase @Inject constructor(
    private val directionEstimator: DirectionEstimator
) {

    /** e.g. "Ahead of you: one person nearby at 12 o'clock, a car very close at 2 o'clock." */
    operator fun invoke(objects: List<DetectedObject>): String {
        if (objects.isEmpty()) return "I don't detect anything ahead of you."
        val ranked = objects.sortedWith(
            compareByDescending<DetectedObject> { it.isDangerous && it.distance == "very close" }
                .thenByDescending { it.areaFraction }
        )
        val parts = ranked.take(4).joinToString("; ") { obj ->
            "${obj.label} ${obj.distance}, ${obj.clockPosition}"
        }
        return "Ahead of you: $parts."
    }

    /** Single-object phrase helper (spec 3.3). */
    fun describe(obj: DetectedObject): String = directionEstimator.describe(obj)
}
