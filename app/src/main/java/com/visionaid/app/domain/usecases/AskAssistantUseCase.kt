package com.visionaid.app.domain.usecases

import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.repository.AssistantRepository
import com.visionaid.app.data.repository.CameraRepository
import com.visionaid.app.data.repository.DetectionRepository
import com.visionaid.app.data.repository.OCRRepository
import com.visionaid.app.domain.models.VoiceCommand
import com.visionaid.app.utils.LocationManager
import com.visionaid.app.utils.TTSManager
import javax.inject.Inject

/**
 * Routes a user question to the AI assistant (spec 8.3) with live context
 * injection (detections, location, time — spec 8.2b/8.4).
 *
 * Also implements the conversation flow niceties:
 *  - "Let me check..." acknowledgment
 *  - attaching the current camera frame for vision questions
 */
class AskAssistantUseCase @Inject constructor(
    private val assistantRepository: AssistantRepository,
    private val cameraRepository: CameraRepository,
    private val detectionRepository: DetectionRepository,
    private val ocrRepository: OCRRepository,
    private val locationManager: LocationManager,
    private val preferences: AppPreferences,
    private val ttsManager: TTSManager
) {

    /**
     * @param question the user's utterance (after wake word/command parsing).
     * @param command the parsed command (ASSISTANT_QUERY for generic questions).
     * @return the assistant's spoken answer.
     */
    suspend operator fun invoke(question: String, command: VoiceCommand = VoiceCommand.ASSISTANT_QUERY): String {
        ttsManager.speak("Let me check.")

        // Refresh live context before asking (spec 8.4).
        assistantRepository.liveContext = AssistantRepository.LiveContext(
            detections = detectionRepository.detections.value,
            locationDescription = locationManager.describeCurrentLocation(),
            lastReadText = ocrRepository.lastReadText.value
        )

        // Vision-related questions get the current frame attached.
        val includeImage = (command == VoiceCommand.ASSISTANT_QUERY && looksVisionRelated(question)) ||
            command == VoiceCommand.DESCRIBE_SCENE
        val frame = if (includeImage) cameraRepository.captureLatestFrame() else null

        val answer = assistantRepository.ask(question, frame, includeImage)
        ttsManager.speak(answer)
        return answer
    }

    /** Heuristic: does this question need "eyes"? */
    private fun looksVisionRelated(question: String): Boolean {
        val q = question.lowercase()
        return listOf(
            "what do you see", "describe", "in front", "around me", "ahead",
            "look", "see", "color", "wearing", "holding", "safe to cross", "is there"
        ).any { q.contains(it) }
    }

    /** True when a Gemini API key is configured. */
    fun isConfigured(): Boolean =
        com.visionaid.app.data.remote.gemini.GeminiContracts.isKeyConfigured(
            preferences.current.value
        )
}
