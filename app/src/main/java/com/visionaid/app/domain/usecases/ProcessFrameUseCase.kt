package com.visionaid.app.domain.usecases

import android.graphics.Bitmap
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.data.repository.DetectionRepository
import com.visionaid.app.data.repository.FallDetectionRepository
import com.visionaid.app.data.remote.gemini.VLMService
import com.visionaid.app.domain.models.FrameSource
import com.visionaid.app.domain.models.OcrMode
import com.visionaid.app.domain.models.PowerMode
import com.visionaid.app.domain.models.SpeechPriority
import com.visionaid.app.ml.FrameAnalyzer
import com.visionaid.app.ml.ObjectDetector
import com.visionaid.app.ml.TextRecognizer
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * THE main pipeline orchestrator (spec 12.3). For every frame:
 *
 *  1. YOLO object detection + direction estimation -> priority voice queue
 *  2. OCR (every 3rd frame, continuous mode)         -> priority voice queue
 *  3. VLM scene description (on demand / every 30 s) -> priority voice queue
 *  4. Scene-change observation -> fall camera validation
 *
 * Battery-aware (spec 16.3):
 *  - SAVER  : half the frames, no VLM
 *  - MINIMAL: only dangerous-object warnings + fall detection
 *  - CRITICAL: pipeline stopped by the service (emergency features only)
 */
class ProcessFrameUseCase @Inject constructor(
    private val objectDetector: ObjectDetector,
    private val textRecognizer: TextRecognizer,
    private val frameAnalyzer: FrameAnalyzer,
    private val detectionRepository: DetectionRepository,
    private val fallDetectionRepository: FallDetectionRepository,
    private val vlmService: VLMService,
    private val ttsManager: TTSManager,
    private val preferences: AppPreferences
) {

    @Volatile
    private var lastVlmAttemptAt = 0L

    suspend operator fun invoke(bitmap: Bitmap, @Suppress("UNUSED_PARAMETER") source: FrameSource) {
        val settings = preferences.current.value
        val powerMode = detectionRepository.powerMode.value
        val mode = detectionRepository.mode.value

        // CRITICAL power: nothing runs (service should have stopped anyway).
        if (powerMode == PowerMode.CRITICAL) return

        val sequence = frameAnalyzer.nextSequence()

        // SAVER power: process every other frame.
        if (powerMode == PowerMode.SAVER && sequence % 2L == 0L) return

        withContext(Dispatchers.Default) {
            var detections: List<DetectedObject> = emptyList()

            // ----------------------------------------------------------
            // 1. Object detection + direction estimation
            // ----------------------------------------------------------
            val detectionEnabled = mode == com.visionaid.app.data.models.DetectionMode.ALL ||
                mode == com.visionaid.app.data.models.DetectionMode.OBJECT_DETECTION
            if (detectionEnabled && objectDetector.isModelAvailable) {
                try {
                    detections = objectDetector.detect(bitmap, settings)
                    detectionRepository.updateDetections(detections)

                    // Scene signature -> change detection (VLM trigger + fall validation).
                    val signature = frameAnalyzer.computeSceneSignature(detections)
                    val sceneChanged = frameAnalyzer.isSignificantSceneChange(signature)
                    fallDetectionRepository.onSceneObservation(sceneChanged)

                    announceDetections(detections, settings, powerMode)
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "Object detection failed on frame $sequence", t)
                }
            }

            // ----------------------------------------------------------
            // 2. OCR — continuous scanning mode (every 3rd frame)
            // ----------------------------------------------------------
            val ocrEnabled = settings.ocrEnabled && powerMode == PowerMode.NORMAL &&
                (mode == com.visionaid.app.data.models.DetectionMode.ALL ||
                    mode == com.visionaid.app.data.models.DetectionMode.OCR)
            if (ocrEnabled && frameAnalyzer.shouldRunOcr(sequence)) {
                try {
                    val message = textRecognizer.processContinuous(bitmap)
                    if (message.isNotBlank()) {
                        ttsManager.speak(message, SpeechPriority.NORMAL)
                        detectionRepository.appendLog(message)
                    }
                } catch (t: Throwable) {
                    AppLogger.w(TAG, "Continuous OCR failed: ${t.message}")
                }
            }

            // ----------------------------------------------------------
            // 3. VLM scene description — periodic (every 30 s)
            // ----------------------------------------------------------
            val now = System.currentTimeMillis()
            val vlmWanted = settings.vlmEnabled && settings.vlmPeriodicEnabled &&
                powerMode == PowerMode.NORMAL &&
                (mode == com.visionaid.app.data.models.DetectionMode.ALL ||
                    mode == com.visionaid.app.data.models.DetectionMode.SCENE_DESCRIPTION)
            if (vlmWanted && now - lastVlmAttemptAt >= Constants.VLM_PERIODIC_INTERVAL_MS) {
                lastVlmAttemptAt = now
                try {
                    val description = vlmService.describeScene(bitmap, detections)
                    if (description.text.isNotBlank()) {
                        ttsManager.speak(description.text, SpeechPriority.NORMAL)
                        detectionRepository.appendLog("Scene: ${description.text}")
                    }
                } catch (t: Throwable) {
                    AppLogger.w(TAG, "Periodic VLM failed: ${t.message}")
                }
            }

            // ----------------------------------------------------------
            // 4. UI preview frame (throttled — full rate would jank Compose)
            // ----------------------------------------------------------
            if (sequence % UI_FRAME_EVERY == 0L) {
                detectionRepository.updateDisplayFrame(bitmap)
            }
        }
    }

    private suspend fun announceDetections(
        detections: List<DetectedObject>,
        settings: com.visionaid.app.data.models.AppSettings,
        powerMode: PowerMode
    ) {
        when (powerMode) {
            PowerMode.MINIMAL -> {
                // Only dangerous, very-close objects break the silence (spec 16.3).
                val danger = detections.filter {
                    it.isDangerous && it.distance == "very close"
                }
                if (danger.isNotEmpty()) {
                    objectDetector.buildAnnouncement(danger, settings)?.let { announcement ->
                        ttsManager.speak(announcement.text, announcement.priority)
                        detectionRepository.appendLog(announcement.text)
                    }
                }
            }
            else -> {
                objectDetector.buildAnnouncement(detections, settings)?.let { announcement ->
                    ttsManager.speak(announcement.text, announcement.priority)
                    detectionRepository.appendLog(announcement.text)
                }
            }
        }
    }

    /** Fires a manual VLM description ("describe scene"). */
    suspend fun describeSceneNow(): String {
        lastVlmAttemptAt = System.currentTimeMillis() // reset periodic timer
        val detections = detectionRepository.detections.value
        val description = vlmService.describeScene(null, detections)
        ttsManager.speak(description.text, SpeechPriority.HIGH)
        detectionRepository.appendLog("Scene: ${description.text}")
        return description.text
    }

    private companion object {
        const val TAG = "ProcessFrame"
        const val UI_FRAME_EVERY = 5L
    }
}
