package com.visionaid.app.data.repository

import android.graphics.Bitmap
import com.visionaid.app.data.models.DetectionMode
import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.domain.models.PowerMode
import com.visionaid.app.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared, observable state of the detection pipeline (spec 10.2):
 * acts as the bridge between the foreground service (producer) and the
 * ViewModels / UI (consumers).
 */
@Singleton
class DetectionRepository @Inject constructor() {

    /** Latest detections (with direction/distance), newest frame wins. */
    private val _detections = MutableStateFlow<List<DetectedObject>>(emptyList())
    val detections: StateFlow<List<DetectedObject>> = _detections.asStateFlow()

    /**
     * Latest frame annotated for the Detection screen preview, throttled to
     * UI-friendly rates by the producer.
     */
    private val _displayFrame = MutableStateFlow<Bitmap?>(null)
    val displayFrame: StateFlow<Bitmap?> = _displayFrame.asStateFlow()

    /** Scrolling log of announcements (max [Constants.DETECTION_LOG_MAX]). */
    private val _detectionLog = MutableStateFlow<List<String>>(emptyList())
    val detectionLog: StateFlow<List<String>> = _detectionLog.asStateFlow()

    /** True while the pipeline service is running. */
    private val _isPipelineActive = MutableStateFlow(false)
    val isPipelineActive: StateFlow<Boolean> = _isPipelineActive.asStateFlow()

    /** Pause switch from the Detection screen. */
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    /** Active detection mode (objects / OCR / scene / all). */
    private val _mode = MutableStateFlow(DetectionMode.ALL)
    val mode: StateFlow<DetectionMode> = _mode.asStateFlow()

    /** Battery-driven power mode (spec 16.3), set by the service. */
    private val _powerMode = MutableStateFlow(PowerMode.NORMAL)
    val powerMode: StateFlow<PowerMode> = _powerMode.asStateFlow()

    // ------------------------------------------------------------------
    // Producer API (called by the service / pipeline)
    // ------------------------------------------------------------------

    fun updateDetections(objects: List<DetectedObject>) {
        _detections.value = objects
    }

    fun updateDisplayFrame(frame: Bitmap?) {
        _displayFrame.value = frame
    }

    fun appendLog(entry: String) {
        val timestamped = entry
        val current = _detectionLog.value
        _detectionLog.value = (listOf(timestamped) + current).take(Constants.DETECTION_LOG_MAX)
    }

    fun setPipelineActive(active: Boolean) {
        _isPipelineActive.value = active
    }

    fun setPowerMode(mode: PowerMode) {
        _powerMode.value = mode
    }

    // ------------------------------------------------------------------
    // Consumer API (UI)
    // ------------------------------------------------------------------

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun setMode(mode: DetectionMode) {
        _mode.value = mode
    }
}
