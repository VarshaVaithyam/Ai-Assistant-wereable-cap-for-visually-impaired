package com.visionaid.app.data.repository

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.AppSettings
import com.visionaid.app.data.models.ConnectionStatus
import com.visionaid.app.data.remote.esp32.ESP32CamService
import com.visionaid.app.data.remote.phone.PhoneCameraSource
import com.visionaid.app.di.ApplicationScope
import com.visionaid.app.domain.models.FrameSource
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import com.visionaid.app.utils.TTSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the camera connection lifecycle (spec 2.2):
 *
 *  - connects to the ESP32-CAM MJPEG stream with exponential-backoff retries
 *  - re-broadcasts frames to many consumers through a SharedFlow<Bitmap>
 *  - tracks connection status (Connected / Connecting / Disconnected / Error)
 *  - automatically falls back to the phone's rear camera (CameraX) when the
 *    ESP32-CAM is unreachable, and switches back when it recovers (spec 16.2)
 *  - keeps the latest frame for one-shot captures (VLM / OCR)
 */
@Singleton
class CameraRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val appContext: android.content.Context,
    private val esp32CamService: ESP32CamService,
    private val phoneCameraSource: PhoneCameraSource,
    private val preferences: AppPreferences,
    private val ttsManager: TTSManager,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    /**
     * Latest frames for all consumers (detection, OCR, VLM).
     * DROP_OLDEST overflow: consumers process the freshest frame only —
     * this is the "skip old frames" rule from spec 12.2.
     */
    private val _frames = MutableSharedFlow<Bitmap>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val frames: SharedFlow<Bitmap> = _frames.asSharedFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activeSource = MutableStateFlow<FrameSource?>(null)
    val activeSource: StateFlow<FrameSource?> = _activeSource.asStateFlow()

    private var streamJob: Job? = null

    /** LifecycleOwner (the foreground service) needed for the CameraX fallback. */
    @Volatile
    private var lifecycleOwner: LifecycleOwner? = null

    @Volatile
    private var usingPhoneCamera = false

    private val frameLock = Any()
    private var latestFrame: Bitmap? = null

    /** Phone fallback callback wiring (set once). */
    private var phoneFallbackWired = false

    /**
     * Starts the ESP32 stream (and keeps retrying). Call [provideLifecycleOwner]
     * first so the phone-camera fallback can bind.
     */
    fun start() {
        if (streamJob?.isActive == true) return
        streamJob = externalScope.launch { streamLoop() }
    }

    /** Needed to enable the phone-camera fallback (CameraX needs a lifecycle). */
    fun provideLifecycleOwner(owner: LifecycleOwner) {
        lifecycleOwner = owner
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        stopPhoneCamera(notify = false)
        synchronized(frameLock) { latestFrame = null }
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _activeSource.value = null
    }

    // ------------------------------------------------------------------
    // ESP32 stream + fallback loop
    // ------------------------------------------------------------------

    private suspend fun streamLoop() {
        var backoffMs = Constants.RECONNECT_BASE_DELAY_MS
        var announcedFallback = false

        while (kotlin.coroutines.coroutineContext.isActive) {
            val settings: AppSettings = preferences.current.value
            _connectionStatus.value = ConnectionStatus.CONNECTING
            try {
                esp32CamService
                    .streamFrames(settings.esp32IpAddress, settings.esp32Port)
                    .collect { bitmap ->
                        // ESP32 came back: give the phone camera back (spec 16.2).
                        if (usingPhoneCamera) {
                            stopPhoneCamera(notify = true, switchingBack = true)
                        }
                        synchronized(frameLock) { latestFrame = bitmap }
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        _activeSource.value = FrameSource.ESP32_CAM
                        _frames.tryEmit(bitmap)
                        backoffMs = Constants.RECONNECT_BASE_DELAY_MS
                    }
                // collect returned normally => server closed the stream.
                throw java.io.IOException("Stream ended by server")
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                AppLogger.w(TAG, "ESP32 stream error: ${t.message}")
                _connectionStatus.value = ConnectionStatus.ERROR
                if (!usingPhoneCamera) {
                    startPhoneFallback(alreadyAnnounced = announcedFallback)
                    announcedFallback = true
                }
                // Exponential backoff, capped (spec 2.1).
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(Constants.RECONNECT_MAX_DELAY_MS)
            }
        }
    }

    // ------------------------------------------------------------------
    // Phone camera fallback
    // ------------------------------------------------------------------

    private fun startPhoneFallback(alreadyAnnounced: Boolean) {
        val owner = lifecycleOwner
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (owner == null || !hasCameraPermission) {
            if (!alreadyAnnounced) {
                ttsManager.speak(
                    "ESP32 camera disconnected.", com.visionaid.app.domain.models.SpeechPriority.HIGH
                )
            }
            return
        }
        if (!phoneFallbackWired) {
            phoneFallbackWired = true
            phoneCameraSource.onFrame = { bitmap ->
                synchronized(frameLock) { latestFrame = bitmap }
                _activeSource.value = FrameSource.PHONE_CAMERA
                _frames.tryEmit(bitmap)
            }
        }
        phoneCameraSource.start(owner) { error ->
            AppLogger.e(TAG, "Phone camera fallback failed: ${error.message}", null)
            _connectionStatus.value = ConnectionStatus.ERROR
        }
        usingPhoneCamera = true
        _connectionStatus.value = ConnectionStatus.CONNECTED
        ttsManager.speak(
            "ESP32 camera disconnected. Switching to phone camera.",
            com.visionaid.app.domain.models.SpeechPriority.HIGH
        )
    }

    private fun stopPhoneCamera(notify: Boolean, switchingBack: Boolean = false) {
        if (!usingPhoneCamera) return
        phoneCameraSource.stop()
        usingPhoneCamera = false
        if (notify && switchingBack) {
            ttsManager.speak(
                "ESP32 camera reconnected.",
                com.visionaid.app.domain.models.SpeechPriority.NORMAL
            )
        }
    }

    // ------------------------------------------------------------------
    // Frame access
    // ------------------------------------------------------------------

    /**
     * Latest frame for one-shot analysis (high-res VLM / OCR captures,
     * spec 2.2). Returns null when no camera is active.
     */
    fun captureLatestFrame(): Bitmap? = synchronized(frameLock) { latestFrame }

    /** True when any camera source is producing frames. */
    val isFrameAvailable: Boolean
        get() = synchronized(frameLock) { latestFrame != null }

    private companion object {
        const val TAG = "CameraRepository"
    }
}
