package com.visionaid.app.data.models

/**
 * Immutable snapshot of all user preferences. Persisted via DataStore
 * (see [com.visionaid.app.data.local.preferences.AppPreferences]).
 */
data class AppSettings(
    // --- ESP32-CAM ---
    val esp32IpAddress: String = "192.168.4.1",
    val esp32Port: Int = 81,
    val esp32HttpPort: Int = 80,

    // --- Voice ---
    val voiceSpeed: Float = 1.2f,
    val voicePitch: Float = 1.0f,
    val voiceLanguage: String = "en-US",

    // --- Detection ---
    val detectionConfidenceThreshold: Float = 0.45f,
    val announcementCooldown: Int = 2000, // ms
    val ocrEnabled: Boolean = true,
    val vlmEnabled: Boolean = true,
    val vlmPeriodicEnabled: Boolean = true,
    val continuousListening: Boolean = true,

    // --- Fall detection ---
    val fallDetectionSensitivity: String = "medium", // low | medium | high
    val fallAlertCountdown: Int = 30,                // seconds

    // --- AI assistant ---
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",

    // --- User ---
    val userName: String = "",

    // --- Audio output ---
    val bluetoothAudioEnabled: Boolean = true,

    // --- App state ---
    val setupCompleted: Boolean = false,
    /** Set by the service so the WorkManager restarter knows to revive it. */
    val serviceShouldRun: Boolean = false
)
