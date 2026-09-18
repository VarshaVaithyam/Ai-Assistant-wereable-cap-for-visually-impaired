package com.visionaid.app.domain.models

import android.graphics.Bitmap

/**
 * Domain-level models shared between use cases, services and ViewModels.
 * (Room entities and raw detection DTOs live in `data/models`.)
 */

/** Where a frame came from. */
enum class FrameSource { ESP32_CAM, PHONE_CAMERA }

/** A camera frame moving through the processing pipeline. */
data class FrameData(
    val bitmap: Bitmap,
    val sequence: Long,
    val source: FrameSource
)

/** Result of one full detection pass over a frame. */
data class DetectionResult(
    val objects: List<com.visionaid.app.data.models.DetectedObject>,
    val voiceMessage: String,
    val timestamp: Long,
    val priority: SpeechPriority
)

/** Priority levels for the TTS queue (URGENT interrupts current speech). */
enum class SpeechPriority { URGENT, HIGH, NORMAL, LOW }

/** OCR operating modes (spec 4.2). */
enum class OcrMode { CONTINUOUS, MANUAL_READ, DOCUMENT }

/** VLM scene description + its origin. */
data class SceneDescription(
    val text: String,
    val timestamp: Long,
    val fromCloud: Boolean
)

/** Voice commands routed from the STT manager (spec 7.2c). */
enum class VoiceCommand {
    WHATS_AHEAD,      // "what's ahead"
    DESCRIBE_SCENE,   // "describe scene" / "what do you see"
    READ_TEXT,        // "read text"
    READ_SIGN,        // "read sign"
    READ_DOCUMENT,    // "read document"
    WHERE_AM_I,       // "where am I"
    HELP,             // "help"
    CALL_GUARDIAN,    // "call guardian"
    STOP_SPEAKING,    // "stop" / "quiet"
    ASSISTANT_QUERY   // anything else -> route to AI assistant
}

/** Gestures reported by the capacitive touch sensor on the cap (spec 6.3). */
enum class TouchEventType { SINGLE_TAP, DOUBLE_TAP, LONG_PRESS }

/** A decoded touch gesture with the ESP32-side timestamp (ms since boot). */
data class TouchEvent(
    val type: TouchEventType,
    val esp32Timestamp: Long,
    val receivedAt: Long = System.currentTimeMillis()
)

/** Phases of the fall-detection state machine (spec 6.1). */
enum class FallPhase { NORMAL, FREE_FALL, IMPACT, LYING, FALL_CONFIRMED }

/** Battery-driven performance mode (spec 16.3). */
enum class PowerMode { NORMAL, SAVER, MINIMAL, CRITICAL }

/** Type of an emergency alert. */
enum class AlertType { FALL_DETECTED, MANUAL_SOS, NO_MOVEMENT }

/** Phases of the emergency protocol state machine. */
enum class EmergencyPhase { IDLE, COUNTDOWN, SENDING, SENT, CANCELLED }

/** Observable state of the emergency protocol (drives UI + voice). */
data class EmergencyState(
    val phase: EmergencyPhase = EmergencyPhase.IDLE,
    val secondsLeft: Int = 0,
    val alertType: AlertType = AlertType.FALL_DETECTED,
    val message: String = ""
)

/** Simplified location snapshot. */
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val accuracyMeters: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromLocation(location: android.location.Location): LocationInfo = LocationInfo(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            timestamp = System.currentTimeMillis()
        )
    }

    /** Google Maps deep link used in emergency SMS. */
    fun mapsLink(): String = "https://maps.google.com/?q=$latitude,$longitude"
}
