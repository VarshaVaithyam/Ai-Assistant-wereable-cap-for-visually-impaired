package com.visionaid.app.data.repository

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.visionaid.app.data.local.database.FallLogDao
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.FallEvent
import com.visionaid.app.domain.models.AlertType
import com.visionaid.app.domain.models.EmergencyPhase
import com.visionaid.app.domain.models.EmergencyState
import com.visionaid.app.di.ApplicationScope
import com.visionaid.app.domain.usecases.DetectFallUseCase
import com.visionaid.app.domain.usecases.SendEmergencyAlertUseCase
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import com.visionaid.app.utils.STTManager
import com.visionaid.app.utils.TTSManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the whole fall-detection feature (spec 6.5):
 *
 *  - receives sensor samples from [com.visionaid.app.services.FallDetectionService]
 *    and runs them through the [DetectFallUseCase] state machine
 *  - combines camera-based scene-change evidence (secondary validation)
 *  - runs the full emergency response protocol (spec 6.4):
 *      vibrate + tone -> voice announcement -> STT confirmation window ->
 *      GPS fix -> SMS to all contacts -> database log -> follow-up monitor
 *  - exposes the protocol state so EmergencyScreen can render countdowns
 */
@Singleton
class FallDetectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detectFallUseCase: DetectFallUseCase,
    private val sendEmergencyAlertUseCase: SendEmergencyAlertUseCase,
    private val fallLogDao: FallLogDao,
    private val preferences: AppPreferences,
    private val ttsManager: TTSManager,
    private val sttManager: STTManager,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    private val _emergencyState = MutableStateFlow(EmergencyState())
    val emergencyState: StateFlow<EmergencyState> = _emergencyState.asStateFlow()

    private val _fallHistory = MutableStateFlow<List<FallEvent>>(emptyList())
    val fallHistory: StateFlow<List<FallEvent>> = _fallHistory.asStateFlow()

    private var emergencyJob: Job? = null
    private var followUpJob: Job? = null
    private val cancelRequested = AtomicBoolean(false)

    /** Camera validation evidence (fed by the detection pipeline). */
    @Volatile
    private var sceneChangedAt: Long = 0L

    /** Last time significant motion was seen (for the follow-up check). */
    @Volatile
    private var lastMotionAt: Long = System.currentTimeMillis()

    // ------------------------------------------------------------------
    // Sensor feed (from FallDetectionService)
    // ------------------------------------------------------------------

    /** Applies the current sensitivity setting. Call on settings change. */
    fun applySensitivity(sensitivity: String) {
        detectFallUseCase.config = DetectFallUseCase.Config.forSensitivity(sensitivity)
    }

    /** Feed an accelerometer sample; returns true when a fall is confirmed. */
    fun onAccelerometer(values: FloatArray, timestampMs: Long): Boolean {
        // Track motion for the post-alert follow-up monitor.
        val magnitude = kotlin.math.sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        )
        if (detectFallUseCase.isMoving(magnitude)) {
            lastMotionAt = System.currentTimeMillis()
        }
        val result = detectFallUseCase.onAccelerometerChange(values, timestampMs)
        return result?.phase == com.visionaid.app.domain.models.FallPhase.FALL_CONFIRMED
    }

    fun onGyroscope(values: FloatArray, timestampMs: Long) {
        detectFallUseCase.onGyroscopeChange(values, timestampMs)
    }

    /** Camera-side validation evidence (spec 6.2). */
    fun onSceneObservation(sceneChanged: Boolean) {
        if (sceneChanged) sceneChangedAt = System.currentTimeMillis()
    }

    /** True when the camera saw a scene change within the given window. */
    private fun cameraValidatesFall(): Boolean =
        System.currentTimeMillis() - sceneChangedAt < 5_000L

    // ------------------------------------------------------------------
    // Emergency protocol
    // ------------------------------------------------------------------

    /** Sensor-confirmed fall -> start the full protocol (spec 6.4). */
    fun onFallDetected() {
        startEmergencyProtocol(AlertType.FALL_DETECTED)
    }

    /** Manual SOS (Emergency screen / "help" voice command / single touch). */
    fun startManualSos() {
        startEmergencyProtocol(AlertType.MANUAL_SOS)
    }

    private fun startEmergencyProtocol(alertType: AlertType) {
        if (emergencyJob?.isActive == true) {
            AppLogger.w(TAG, "Emergency protocol already running")
            return
        }
        cancelRequested.set(false)
        emergencyJob = externalScope.launch { runEmergencyProtocol(alertType) }
    }

    private suspend fun runEmergencyProtocol(alertType: AlertType) {
        val settings = preferences.current.value
        val countdownSeconds = if (alertType == AlertType.MANUAL_SOS) {
            Constants.SOS_COUNTDOWN_SECONDS
        } else {
            settings.fallAlertCountdown
        }

        // ---- Step 1: vibrate + alert tone through Bluetooth ----
        vibrateAlert()
        playAlertTone()

        // ---- Step 2: voice announcement ----
        val announcement = when (alertType) {
            AlertType.FALL_DETECTED ->
                "Fall detected. Are you okay? Say I'm fine to cancel, " +
                    "or say help to send the alert now. " +
                    "The emergency alert will be sent in $countdownSeconds seconds."
            AlertType.MANUAL_SOS ->
                "Emergency alert in $countdownSeconds seconds. " +
                    "Say cancel to stop, or wait for the alert."
            AlertType.NO_MOVEMENT ->
                "You have not moved for a while. Say I'm fine to cancel, " +
                    "or an alert will be sent."
        }
        _emergencyState.value = EmergencyState(
            phase = EmergencyPhase.COUNTDOWN,
            secondsLeft = countdownSeconds,
            alertType = alertType,
            message = announcement
        )
        ttsManager.speakImmediately(announcement)
        ttsManager.awaitIdle()

        // ---- Step 3: countdown ticker + STT confirmation windows ----
        val ticker = launch {
            var remaining = countdownSeconds
            while (isActive && remaining > 0) {
                _emergencyState.value = _emergencyState.value.copy(secondsLeft = remaining)
                remaining--
                delay(1_000L)
            }
        }

        var cancelled = false
        var confirmed = false
        while (isActive && !cancelled && !confirmed && ticker.isActive) {
            if (cancelRequested.get()) {
                cancelled = true
                break
            }
            if (confirmRequested.get()) {
                confirmed = true
                break
            }
            val heard = sttManager.listenOnce(Constants.EMERGENCY_LISTEN_MS)
            when {
                sttManager.isCancelPhrase(heard) -> cancelled = true
                sttManager.isConfirmPhrase(heard) -> confirmed = true
            }
        }
        ticker.cancel()

        if (cancelled) {
            // ---- Cancelled: voice confirm + log ----
            _emergencyState.value = _emergencyState.value.copy(
                phase = EmergencyPhase.CANCELLED,
                message = "Emergency alert cancelled."
            )
            ttsManager.speakImmediately("Emergency alert cancelled.")
            logEvent(alertType, wasConfirmed = false, wasCancelled = true, alertSent = false)
            resetToIdleAfterDelay()
            return
        }

        // ---- Step 4-6: location + SMS + database log ----
        _emergencyState.value = _emergencyState.value.copy(
            phase = EmergencyPhase.SENDING,
            message = "Sending emergency alert."
        )
        ttsManager.speakImmediately("Sending emergency alert.")

        val event = sendEmergencyAlertUseCase(alertType)

        val sentContacts = event?.alertSent == true
        _emergencyState.value = _emergencyState.value.copy(
            phase = EmergencyPhase.SENT,
            message = if (sentContacts) "Emergency alert sent." else
                "Alert could not be sent. Check your contacts and signal."
        )
        ttsManager.speakImmediately(
            if (sentContacts) "Emergency alert sent to your contacts."
            else "I could not send the alert. Please check your phone."
        )

        // ---- Step 7: follow-up monitoring (spec 6.4 step 7) ----
        if (sentContacts) startFollowUpMonitoring()

        refreshHistory()
        resetToIdleAfterDelay()
    }

    /** External cancellation (UI button, "I'm fine", double-tap on cap). */
    fun cancelEmergency() {
        if (emergencyJob?.isActive == true) {
            cancelRequested.set(true)
        }
    }

    private fun resetToIdleAfterDelay() {
        externalScope.launch {
            delay(3_000L)
            _emergencyState.value = EmergencyState()
        }
    }

    // ------------------------------------------------------------------
    // Follow-up monitoring (no movement for 5 minutes -> new alert)
    // ------------------------------------------------------------------

    private fun startFollowUpMonitoring() {
        followUpJob?.cancel()
        followUpJob = externalScope.launch {
            delay(Constants.FOLLOW_UP_MONITORING_MS)
            val secondsSinceMotion = (System.currentTimeMillis() - lastMotionAt) / 1000
            if (secondsSinceMotion >= Constants.FOLLOW_UP_MONITORING_MS / 1000 - 30) {
                AppLogger.w(TAG, "No movement after alert -> sending follow-up")
                ttsManager.speakImmediately(
                    "No movement detected. Sending a follow-up alert.",
                    com.visionaid.app.domain.models.SpeechPriority.URGENT
                )
                sendEmergencyAlertUseCase(AlertType.NO_MOVEMENT)
                refreshHistory()
            }
        }
    }

    // ------------------------------------------------------------------
    // Logging
    // ------------------------------------------------------------------

    private suspend fun logEvent(
        alertType: AlertType,
        wasConfirmed: Boolean,
        wasCancelled: Boolean,
        alertSent: Boolean,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        address: String = ""
    ) {
        try {
            val sensorData = JSONObject().apply {
                put("alertType", alertType.name)
                put("cameraValidated", cameraValidatesFall())
                put("phaseLog", detectFallUseCase.phase.name)
            }
            fallLogDao.insert(
                FallEvent(
                    timestamp = System.currentTimeMillis(),
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    wasConfirmed = wasConfirmed,
                    wasCancelled = wasCancelled,
                    sensorData = sensorData.toString(),
                    alertSent = alertSent
                )
            )
            refreshHistory()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "logEvent failed", t)
        }
    }

    /** Loads the latest events into [fallHistory] (for the Emergency screen). */
    fun refreshHistory() {
        externalScope.launch {
            try {
                _fallHistory.value = fallLogDao.getRecent(20)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "refreshHistory failed", t)
            }
        }
    }

    // ------------------------------------------------------------------
    // Alert side effects
    // ------------------------------------------------------------------

    private fun vibrateAlert() {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            // Long-short-short pattern: unmistakable even in a pocket.
            val pattern = longArrayOf(0, 600, 200, 600, 200, 600)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Vibration failed: ${t.message}")
        }
    }

    private fun playAlertTone() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
            // ToneGenerator releases itself after use; explicit release after a delay.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tone.release()
            }, 1_000L)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Alert tone failed: ${t.message}")
        }
    }

    /** Dials the primary contact (voice command "call guardian"). */
    fun dialPrimaryContact() {
        externalScope.launch {
            val primary = sendEmergencyAlertUseCase.primaryContact()
            if (primary == null) {
                ttsManager.speak("No emergency contact is configured.")
                return@launch
            }
            ttsManager.speak("Calling ${primary.name}.")
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:${primary.phoneNumber}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Dial failed", t)
                ttsManager.speak("I could not open the dialer.")
            }
        }
    }

    private companion object {
        const val TAG = "FallRepository"
    }
}
