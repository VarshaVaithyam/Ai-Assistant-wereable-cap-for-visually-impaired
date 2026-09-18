package com.visionaid.app.domain.usecases

import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.data.repository.CameraRepository
import com.visionaid.app.ml.ObjectDetector
import com.visionaid.app.utils.AppLogger
import javax.inject.Inject

/**
 * On-demand object detection (spec 3.5): powers the "what's ahead" voice
 * command and button. The continuous pipeline is handled by
 * [ProcessFrameUseCase] inside the foreground service.
 */
class DetectObjectsUseCase @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val objectDetector: ObjectDetector,
    private val getDirectionUseCase: GetDirectionUseCase,
    private val preferences: AppPreferences
) {

    data class Result(val objects: List<DetectedObject>, val spokenSummary: String)

    /**
     * Captures the latest frame, runs YOLO and returns detections + a
     * spoken summary. Fails gracefully when no camera or model is available.
     */
    suspend operator fun invoke(): Result {
        val frame = cameraRepository.captureLatestFrame()
        if (frame == null) {
            return Result(emptyList(), "The camera is not active yet.")
        }
        if (!objectDetector.isModelAvailable) {
            return Result(
                emptyList(),
                "The object detection model is missing. Please add it using the setup guide."
            )
        }
        return try {
            val objects = objectDetector.detect(frame, preferences.current.value)
            Result(objects, getDirectionUseCase(objects))
        } catch (t: Throwable) {
            AppLogger.e(TAG, "On-demand detection failed", t)
            Result(emptyList(), "Detection hit a problem. Please try again.")
        }
    }

    private companion object {
        const val TAG = "DetectObjects"
    }
}
