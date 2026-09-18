package com.visionaid.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.visionaid.app.data.models.AppSettings
import com.visionaid.app.di.ApplicationScope
import com.visionaid.app.utils.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "visionaid_settings"
)

/**
 * DataStore-backed settings repository.
 *
 * Exposes:
 *  - [settingsFlow]: cold Flow<AppSettings> straight from DataStore (null-safe
 *    loading, used by the NavGraph to decide the start destination).
 *  - [current]: warm StateFlow updated automatically — used by the detection
 *    pipeline to read fresh settings every frame without suspending.
 *
 * All setters are suspend functions and update both DataStore and [current].
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    // Application-scoped coroutine scope used only to keep the warm cache fresh.
    @ApplicationScope private val externalScope: CoroutineScope
) {

    private object Keys {
        val ESP32_IP = stringPreferencesKey("esp32_ip")
        val ESP32_PORT = intPreferencesKey("esp32_port")
        val ESP32_HTTP_PORT = intPreferencesKey("esp32_http_port")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val CONFIDENCE = floatPreferencesKey("detection_confidence")
        val ANNOUNCE_COOLDOWN = intPreferencesKey("announcement_cooldown")
        val OCR_ENABLED = booleanPreferencesKey("ocr_enabled")
        val VLM_ENABLED = booleanPreferencesKey("vlm_enabled")
        val VLM_PERIODIC = booleanPreferencesKey("vlm_periodic")
        val CONTINUOUS_LISTENING = booleanPreferencesKey("continuous_listening")
        val FALL_SENSITIVITY = stringPreferencesKey("fall_sensitivity")
        val FALL_COUNTDOWN = intPreferencesKey("fall_countdown")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val USER_NAME = stringPreferencesKey("user_name")
        val BLUETOOTH_AUDIO = booleanPreferencesKey("bluetooth_audio")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val SERVICE_SHOULD_RUN = booleanPreferencesKey("service_should_run")
    }

    private val _current = MutableStateFlow(AppSettings())

    /** Warm cache of the latest settings — safe to read from any thread. */
    val current: StateFlow<AppSettings> = _current.asStateFlow()

    init {
        // Keep the warm cache in sync with DataStore.
        externalScope.launch {
            try {
                context.dataStore.data.collect { prefs ->
                    _current.value = mapSettings(prefs)
                }
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed reading settings", t)
            }
        }
    }

    /** Cold flow straight from DataStore (emits on every change). */
    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { prefs -> mapSettings(prefs) }

    /** Suspends until the first real value (not the default) is loaded. */
    suspend fun awaitFirst(): AppSettings = settingsFlow.first()

    private fun mapSettings(p: Preferences): AppSettings = AppSettings(
        esp32IpAddress = p[Keys.ESP32_IP] ?: AppSettings().esp32IpAddress,
        esp32Port = p[Keys.ESP32_PORT] ?: AppSettings().esp32Port,
        esp32HttpPort = p[Keys.ESP32_HTTP_PORT] ?: AppSettings().esp32HttpPort,
        voiceSpeed = p[Keys.VOICE_SPEED] ?: AppSettings().voiceSpeed,
        voicePitch = p[Keys.VOICE_PITCH] ?: AppSettings().voicePitch,
        voiceLanguage = p[Keys.VOICE_LANGUAGE] ?: AppSettings().voiceLanguage,
        detectionConfidenceThreshold =
            p[Keys.CONFIDENCE] ?: AppSettings().detectionConfidenceThreshold,
        announcementCooldown = p[Keys.ANNOUNCE_COOLDOWN] ?: AppSettings().announcementCooldown,
        ocrEnabled = p[Keys.OCR_ENABLED] ?: AppSettings().ocrEnabled,
        vlmEnabled = p[Keys.VLM_ENABLED] ?: AppSettings().vlmEnabled,
        vlmPeriodicEnabled = p[Keys.VLM_PERIODIC] ?: AppSettings().vlmPeriodicEnabled,
        continuousListening = p[Keys.CONTINUOUS_LISTENING] ?: AppSettings().continuousListening,
        fallDetectionSensitivity = p[Keys.FALL_SENSITIVITY] ?: AppSettings().fallDetectionSensitivity,
        fallAlertCountdown = p[Keys.FALL_COUNTDOWN] ?: AppSettings().fallAlertCountdown,
        geminiApiKey = p[Keys.GEMINI_API_KEY] ?: "",
        geminiModel = p[Keys.GEMINI_MODEL] ?: AppSettings().geminiModel,
        userName = p[Keys.USER_NAME] ?: "",
        bluetoothAudioEnabled = p[Keys.BLUETOOTH_AUDIO] ?: AppSettings().bluetoothAudioEnabled,
        setupCompleted = p[Keys.SETUP_COMPLETED] ?: false,
        serviceShouldRun = p[Keys.SERVICE_SHOULD_RUN] ?: false
    )

    private suspend fun update(transform: (MutablePreferences) -> Unit) {
        context.dataStore.edit { transform(it) }
    }

    // ------------------------------------------------------------------
    // ESP32 settings
    // ------------------------------------------------------------------
    suspend fun setEsp32Config(ip: String, streamPort: Int, httpPort: Int) = update {
        it[Keys.ESP32_IP] = ip
        it[Keys.ESP32_PORT] = streamPort
        it[Keys.ESP32_HTTP_PORT] = httpPort
    }

    // ------------------------------------------------------------------
    // Voice settings
    // ------------------------------------------------------------------
    suspend fun setVoiceConfig(speed: Float, pitch: Float, language: String) = update {
        it[Keys.VOICE_SPEED] = speed
        it[Keys.VOICE_PITCH] = pitch
        it[Keys.VOICE_LANGUAGE] = language
    }

    // ------------------------------------------------------------------
    // Detection settings
    // ------------------------------------------------------------------
    suspend fun setDetectionConfig(
        confidence: Float,
        cooldownMs: Int,
        ocrEnabled: Boolean,
        vlmEnabled: Boolean,
        vlmPeriodicEnabled: Boolean
    ) = update {
        it[Keys.CONFIDENCE] = confidence
        it[Keys.ANNOUNCE_COOLDOWN] = cooldownMs
        it[Keys.OCR_ENABLED] = ocrEnabled
        it[Keys.VLM_ENABLED] = vlmEnabled
        it[Keys.VLM_PERIODIC] = vlmPeriodicEnabled
    }

    suspend fun setContinuousListening(enabled: Boolean) = update {
        it[Keys.CONTINUOUS_LISTENING] = enabled
    }

    // ------------------------------------------------------------------
    // Fall detection settings
    // ------------------------------------------------------------------
    suspend fun setFallConfig(sensitivity: String, countdownSeconds: Int) = update {
        it[Keys.FALL_SENSITIVITY] = sensitivity
        it[Keys.FALL_COUNTDOWN] = countdownSeconds
    }

    // ------------------------------------------------------------------
    // Assistant settings
    // ------------------------------------------------------------------
    suspend fun setGeminiApiKey(key: String) = update { it[Keys.GEMINI_API_KEY] = key.trim() }
    suspend fun setGeminiModel(model: String) = update { it[Keys.GEMINI_MODEL] = model.trim() }

    // ------------------------------------------------------------------
    // User / audio
    // ------------------------------------------------------------------
    suspend fun setUserName(name: String) = update { it[Keys.USER_NAME] = name.trim() }
    suspend fun setBluetoothAudioEnabled(enabled: Boolean) = update {
        it[Keys.BLUETOOTH_AUDIO] = enabled
    }

    // ------------------------------------------------------------------
    // App state flags
    // ------------------------------------------------------------------
    suspend fun setSetupCompleted(done: Boolean) = update { it[Keys.SETUP_COMPLETED] = done }
    suspend fun setServiceShouldRun(shouldRun: Boolean) = update {
        it[Keys.SERVICE_SHOULD_RUN] = shouldRun
    }

    private companion object {
        const val TAG = "AppPreferences"
    }
}

