package com.visionaid.app.presentation.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.AppSettings
import com.visionaid.app.data.models.ConnectionStatus
import com.visionaid.app.data.repository.CameraRepository
import com.visionaid.app.data.repository.DetectionRepository
import com.visionaid.app.data.repository.OCRRepository
import com.visionaid.app.domain.models.OcrMode
import com.visionaid.app.domain.models.SpeechPriority
import com.visionaid.app.services.DetectionForegroundService
import com.visionaid.app.services.FallDetectionService
import com.visionaid.app.utils.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the home screen: service start/stop, status and the
 * one-tap "Read Text" action (spec 11.2).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val detectionRepository: DetectionRepository,
    private val ocrRepository: OCRRepository,
    private val preferences: AppPreferences,
    private val ttsManager: TTSManager
) : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = cameraRepository.connectionStatus
    val isPipelineActive: StateFlow<Boolean> = detectionRepository.isPipelineActive

    val settings: StateFlow<AppSettings?> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Battery percentage shown in the bottom bar. */
    private val _batteryPercent = MutableStateFlow<Int?>(null)
    val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()

    private var greeted = false

    /** Voice greeting once per app session (spec 11.2). */
    fun greetOnLaunch() {
        if (greeted) return
        greeted = true
        ttsManager.speak(
            "VisionAid is ready. Tap anywhere and say a command, or press a button.",
            SpeechPriority.NORMAL
        )
    }

    /** Starts / stops both detection services (spec 11.2 button a). */
    fun toggleDetection(context: Context) {
        if (detectionRepository.isPipelineActive.value) {
            DetectionForegroundService.stop(context)
            FallDetectionService.stop(context)
            ttsManager.speak("Detection stopped.", SpeechPriority.NORMAL)
        } else {
            DetectionForegroundService.start(context)
            FallDetectionService.start(context)
            ttsManager.speak("Starting detection.", SpeechPriority.NORMAL)
        }
    }

    /** One-tap manual OCR (spec 11.2 button c). */
    fun readTextNow() {
        if (!cameraRepository.isFrameAvailable) {
            ttsManager.speak("The camera is not active. Start detection first.")
            return
        }
        ttsManager.speak("Reading text.", SpeechPriority.NORMAL)
        ocrRepository.requestManualRead(OcrMode.MANUAL_READ)
    }

    /** Reads the battery level (sticky broadcast query must run off-main). */
    fun refreshBattery(context: Context) {
        viewModelScope.launch {
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE)
                        as android.os.BatteryManager
                val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (level > 0) _batteryPercent.value = level
            } catch (_: Exception) {
            }
        }
    }
}
