package com.visionaid.app.presentation.ui.detection

import androidx.lifecycle.ViewModel
import com.visionaid.app.data.models.DetectionMode
import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.data.repository.DetectionRepository
import com.visionaid.app.ml.ObjectDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Detection screen state (spec 11.4): live preview + overlays for sighted
 * helpers/developers, detection log, mode selector, pause switch.
 */
@HiltViewModel
class DetectionViewModel @Inject constructor(
    private val detectionRepository: DetectionRepository,
    objectDetector: ObjectDetector
) : ViewModel() {

    val displayFrame: StateFlow<android.graphics.Bitmap?> = detectionRepository.displayFrame
    val detections: StateFlow<List<DetectedObject>> = detectionRepository.detections
    val detectionLog: StateFlow<List<String>> = detectionRepository.detectionLog
    val mode: StateFlow<DetectionMode> = detectionRepository.mode
    val isPaused: StateFlow<Boolean> = detectionRepository.isPaused
    val isPipelineActive: StateFlow<Boolean> = detectionRepository.isPipelineActive

    /** True when the YOLO model file exists in assets. */
    val isModelAvailable: Boolean = objectDetector.isModelAvailable

    fun setMode(mode: DetectionMode) = detectionRepository.setMode(mode)
    fun togglePause() = detectionRepository.setPaused(!detectionRepository.isPaused.value)
}
