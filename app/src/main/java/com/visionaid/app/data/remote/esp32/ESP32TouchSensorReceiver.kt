package com.visionaid.app.data.remote.esp32

import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.remote.api.ApiClient
import com.visionaid.app.domain.models.TouchEvent
import com.visionaid.app.domain.models.TouchEventType
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls the ESP32's touch-sensor endpoint and turns raw touch state into
 * gesture events (spec 2.3 + 6.3).
 *
 * The ESP32 serves:  GET /touch_status -> {"touched": true/false, "timestamp": 123}
 *
 * Gesture decoding on the phone side:
 *  - SINGLE_TAP  : short tap           -> manual SOS trigger (configurable)
 *  - DOUBLE_TAP  : two taps within 400 ms -> cancel a false alarm
 *  - LONG_PRESS  : touch held >= 1 s   -> activate the AI assistant
 *
 * Polling runs every [Constants.TOUCH_POLL_INTERVAL_MS]; a hardware push
 * alternative (ESP32 POSTing to a local server on the phone) is described
 * in docs/HARDWARE_SETUP.md.
 */
@Singleton
class ESP32TouchSensorReceiver @Inject constructor(
    private val apiClient: ApiClient,
    private val preferences: AppPreferences
) {

    private val _touchEvents = MutableSharedFlow<TouchEvent>(
        replay = 0,
        extraBufferCapacity = 8
    )

    /** Decoded touch gestures — collected by [com.visionaid.app.services.DetectionForegroundService]. */
    val touchEvents: SharedFlow<TouchEvent> = _touchEvents.asSharedFlow()

    private var pollingJob: Job? = null

    // --- gesture state ---
    private var lastTouched = false
    private var pressStartedAt = 0L
    private var lastTapReleasedAt = 0L
    private var pendingSingleTap = false
    private var consecutiveFailures = 0

    /** True while the polling loop is running. */
    val isRunning: Boolean get() = pollingJob?.isActive == true

    /**
     * Starts polling. Call from any scope; the loop lives until [stop].
     */
    fun start(scope: CoroutineScope) {
        if (pollingJob?.isActive == true) return
        resetGestureState()
        pollingJob = scope.launch {
            AppLogger.i(TAG, "Touch sensor polling started")
            while (isActive) {
                val settings = preferences.current.value
                val url = "http://${settings.esp32IpAddress}:" +
                    "${settings.esp32HttpPort}${Constants.ESP32_TOUCH_STATUS_PATH}"

                val json: JSONObject? = apiClient.getJson(url, timeoutMs = 1_500L)
                if (json == null) {
                    consecutiveFailures++
                    // Avoid log spam: report every 20th failure (10 s).
                    if (consecutiveFailures % 20 == 1) {
                        AppLogger.w(TAG, "Touch endpoint unreachable (fail #$consecutiveFailures)")
                    }
                } else {
                    consecutiveFailures = 0
                    val touched = json.optBoolean("touched", false)
                    val esp32Ts = json.optLong("timestamp", 0L)
                    onTouchState(touched, esp32Ts)
                }

                // Slightly slower polling when the ESP32 keeps failing.
                val delayMs = if (consecutiveFailures > 10) 2_000L
                else Constants.TOUCH_POLL_INTERVAL_MS
                delay(delayMs)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        resetGestureState()
        AppLogger.i(TAG, "Touch sensor polling stopped")
    }

    // ------------------------------------------------------------------
    // Gesture decoding
    // ------------------------------------------------------------------

    private fun onTouchState(touched: Boolean, esp32Timestamp: Long) {
        val now = System.currentTimeMillis()
        when {
            touched && !lastTouched -> {
                // Finger just went down.
                pressStartedAt = now
            }
            !touched && lastTouched -> {
                // Finger just came up.
                val pressDuration = now - pressStartedAt
                if (pressDuration >= Constants.TOUCH_LONG_PRESS_MS) {
                    emit(TouchEvent(TouchEventType.LONG_PRESS, esp32Timestamp))
                    pendingSingleTap = false
                } else {
                    if (now - lastTapReleasedAt <= Constants.TOUCH_DOUBLE_TAP_WINDOW_MS) {
                        // Second tap of a double tap.
                        emit(TouchEvent(TouchEventType.DOUBLE_TAP, esp32Timestamp))
                        pendingSingleTap = false
                        lastTapReleasedAt = 0L
                    } else {
                        // Candidate single tap — confirm after the window.
                        lastTapReleasedAt = now
                        pendingSingleTap = true
                    }
                }
            }
        }
        if (pendingSingleTap &&
            now - lastTapReleasedAt > Constants.TOUCH_SINGLE_TAP_CONFIRM_MS
        ) {
            pendingSingleTap = false
            lastTapReleasedAt = 0L
            emit(TouchEvent(TouchEventType.SINGLE_TAP, esp32Timestamp))
        }
        lastTouched = touched
    }

    private fun emit(event: TouchEvent) {
        AppLogger.d(TAG, "Touch gesture: ${event.type}")
        _touchEvents.tryEmit(event)
    }

    private fun resetGestureState() {
        lastTouched = false
        pressStartedAt = 0L
        lastTapReleasedAt = 0L
        pendingSingleTap = false
        consecutiveFailures = 0
    }

    private companion object {
        const val TAG = "TouchReceiver"
    }
}
