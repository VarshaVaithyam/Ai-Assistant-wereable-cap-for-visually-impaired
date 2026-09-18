package com.visionaid.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.visionaid.app.R
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.ConnectionStatus
import com.visionaid.app.data.repository.CameraRepository
import com.visionaid.app.data.repository.DetectionRepository
import com.visionaid.app.data.repository.FallDetectionRepository
import com.visionaid.app.data.repository.OCRRepository
import com.visionaid.app.domain.models.OcrMode
import com.visionaid.app.domain.models.PowerMode
import com.visionaid.app.domain.models.SpeechPriority
import com.visionaid.app.domain.models.TouchEventType
import com.visionaid.app.domain.models.VoiceCommand
import com.visionaid.app.domain.usecases.AskAssistantUseCase
import com.visionaid.app.domain.usecases.DetectObjectsUseCase
import com.visionaid.app.domain.usecases.DetectTextUseCase
import com.visionaid.app.domain.usecases.ProcessFrameUseCase
import com.visionaid.app.presentation.ui.main.MainActivity
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import com.visionaid.app.utils.LocationManager
import com.visionaid.app.utils.STTManager
import com.visionaid.app.utils.TTSManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import com.visionaid.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The main foreground service (spec 10.1 + 12.1). Owns:
 *
 *  - ESP32-CAM stream reception (via CameraRepository, incl. phone fallback)
 *  - the frame-processing pipeline (ProcessFrameUseCase)
 *  - manual OCR requests from the UI
 *  - ESP32 touch-sensor gestures
 *  - continuous voice-command listening + command routing
 *  - battery-driven power management (spec 16.3)
 *  - restart-on-crash via WorkManager (best effort, ServiceRestarterWorker)
 */
@AndroidEntryPoint
class DetectionForegroundService : LifecycleService() {

    @Inject lateinit var cameraRepository: CameraRepository
    @Inject lateinit var detectionRepository: DetectionRepository
    @Inject lateinit var fallDetectionRepository: FallDetectionRepository
    @Inject lateinit var ocrRepository: OCRRepository
    @Inject lateinit var processFrameUseCase: ProcessFrameUseCase
    @Inject lateinit var detectObjectsUseCase: DetectObjectsUseCase
    @Inject lateinit var detectTextUseCase: DetectTextUseCase
    @Inject lateinit var askAssistantUseCase: AskAssistantUseCase
    @Inject lateinit var sttManager: STTManager
    @Inject lateinit var ttsManager: TTSManager
    @Inject lateinit var locationManager: LocationManager
    @Inject lateinit var preferences: AppPreferences
    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private val commandListener = object : STTManager.CommandListener {
        override fun onWakeWord() {
            ttsManager.speak("Yes?", SpeechPriority.HIGH)
        }

        override fun onCommand(command: VoiceCommand, utterance: String) {
            lifecycleScope.launch(Dispatchers.Default) { routeCommand(command, utterance) }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) {
                    applyPowerPolicy(level * 100 / scale)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "Detection service creating")

        createNotificationChannel()
        startForegroundWithType()

        // Apply current voice settings to the engine.
        val settings = preferences.current.value
        ttsManager.applyVoiceSettings(settings.voiceSpeed, settings.voicePitch, settings.voiceLanguage)
        if (settings.bluetoothAudioEnabled) {
            ttsManager.setBluetoothOutput(true)
        }

        startPipeline()
        startTouchSensor()
        startVoiceCommands()
        observeManualRequests()
        observeConnectionStatus()
        registerBatteryReceiver()
        observePowerMode()

        // Greet the user (spec 11.2).
        ttsManager.speak(
            "VisionAid is active.",
            SpeechPriority.NORMAL
        )

        // Remember that the service should run (WorkManager restarter).
        applicationScope.launch { preferences.setServiceShouldRun(true) }
        ServiceRestarterWorker.schedule(applicationContext)

        detectionRepository.setPipelineActive(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AppLogger.i(TAG, "Detection service destroying")
        detectionRepository.setPipelineActive(false)
        detectionRepository.updateDisplayFrame(null)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        sttManager.commandListener = null
        sttManager.stopContinuousListening()
        cameraRepository.stop()
        fallDetectionRepository.cancelEmergency()
        applicationScope.launch { preferences.setServiceShouldRun(false) }
        ServiceRestarterWorker.cancel(applicationContext)
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Pipeline
    // ------------------------------------------------------------------

    private fun startPipeline() {
        // CameraX (phone fallback) needs a LifecycleOwner: this service is one.
        cameraRepository.provideLifecycleOwner(this)
        cameraRepository.start()

        // collectLatest => frame skipping: while one frame is processed,
        // newer frames cancel the stale work (spec 12.2).
        lifecycleScope.launch(Dispatchers.Default) {
            cameraRepository.frames.collectLatest { frame ->
                if (detectionRepository.isPaused.value) return@collectLatest
                try {
                    processFrameUseCase(frame, cameraRepository.activeSource.value
                        ?: com.visionaid.app.domain.models.FrameSource.ESP32_CAM)
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "Frame processing error", t)
                }
            }
        }
    }

    private fun observeManualRequests() {
        lifecycleScope.launch {
            ocrRepository.manualReadRequests.collect { request ->
                val text = detectTextUseCase(request.mode, request.signsOnly)
                if (text.isNotBlank()) {
                    ttsManager.speak(text, SpeechPriority.HIGH)
                    detectionRepository.appendLog(text)
                }
            }
        }
    }

    private fun observeConnectionStatus() {
        lifecycleScope.launch {
            cameraRepository.connectionStatus.collect { status ->
                updateNotification(status)
            }
        }
    }

    // ------------------------------------------------------------------
    // Touch sensor gestures (spec 6.3)
    // ------------------------------------------------------------------

    @Inject lateinit var touchSensorReceiver:
        com.visionaid.app.data.remote.esp32.ESP32TouchSensorReceiver

    private fun startTouchSensor() {
        touchSensorReceiver.start(lifecycleScope)
        lifecycleScope.launch {
            touchSensorReceiver.touchEvents.collect { event ->
                when (event.type) {
                    TouchEventType.SINGLE_TAP -> {
                        // Manual signal from the cap: start SOS protocol.
                        ttsManager.speakImmediately("Emergency alert requested.")
                        fallDetectionRepository.startManualSos()
                    }
                    TouchEventType.DOUBLE_TAP ->
                        // Cancel a false alarm.
                        fallDetectionRepository.cancelEmergency()
                    TouchEventType.LONG_PRESS ->
                        // Activate the AI assistant.
                        lifecycleScope.launch(Dispatchers.Default) {
                            val utterance = sttManager.listenOnce(8_000L)
                            if (!utterance.isNullOrBlank()) {
                                val command = sttManager.parseCommand(utterance)
                                routeCommand(command, utterance)
                            } else {
                                ttsManager.speak("I did not catch that.")
                            }
                        }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Voice commands
    // ------------------------------------------------------------------

    private fun startVoiceCommands() {
        val settings = preferences.current.value
        if (!settings.continuousListening) return
        sttManager.commandListener = commandListener
        sttManager.startContinuousListening()
    }

    /** Routes one recognized command (spec 7.2c + 12.1). */
    private suspend fun routeCommand(command: VoiceCommand, utterance: String) {
        when (command) {
            VoiceCommand.WHATS_AHEAD -> {
                val result = detectObjectsUseCase()
                ttsManager.speak(result.spokenSummary, SpeechPriority.HIGH)
                detectionRepository.appendLog(result.spokenSummary)
            }
            VoiceCommand.DESCRIBE_SCENE -> {
                val description = processFrameUseCase.describeSceneNow()
                detectionRepository.appendLog("Scene: $description")
            }
            VoiceCommand.READ_TEXT -> detectTextUseCase(OcrMode.MANUAL_READ).let {
                ttsManager.speak(it, SpeechPriority.HIGH)
            }
            VoiceCommand.READ_SIGN -> detectTextUseCase(OcrMode.MANUAL_READ, signsOnly = true).let {
                ttsManager.speak(it, SpeechPriority.HIGH)
            }
            VoiceCommand.READ_DOCUMENT -> detectTextUseCase(OcrMode.DOCUMENT).let {
                ttsManager.speak(it, SpeechPriority.HIGH)
            }
            VoiceCommand.WHERE_AM_I -> {
                locationManager.fetchCurrentLocation()
                ttsManager.speak(locationManager.describeCurrentLocation(), SpeechPriority.HIGH)
            }
            VoiceCommand.HELP -> fallDetectionRepository.startManualSos()
            VoiceCommand.CALL_GUARDIAN -> fallDetectionRepository.dialPrimaryContact()
            VoiceCommand.STOP_SPEAKING -> ttsManager.stop()
            VoiceCommand.ASSISTANT_QUERY -> askAssistantUseCase(utterance)
        }
    }

    // ------------------------------------------------------------------
    // Battery / power management (spec 16.3)
    // ------------------------------------------------------------------

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(
            this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Also seed with the current sticky value.
        val sticky = registerReceiver(null, filter)
        sticky?.let { batteryReceiver.onReceive(this, it) }
    }

    private fun applyPowerPolicy(batteryPercent: Int) {
        val newMode = when {
            batteryPercent <= Constants.BATTERY_CRITICAL_THRESHOLD -> PowerMode.CRITICAL
            batteryPercent <= Constants.BATTERY_MINIMAL_THRESHOLD -> PowerMode.MINIMAL
            batteryPercent <= Constants.BATTERY_SAVER_THRESHOLD -> PowerMode.SAVER
            else -> PowerMode.NORMAL
        }
        if (newMode != detectionRepository.powerMode.value) {
            AppLogger.i(TAG, "Power mode -> $newMode (battery $batteryPercent%)")
            detectionRepository.setPowerMode(newMode)
        }
    }

    private fun observePowerMode() {
        lifecycleScope.launch {
            detectionRepository.powerMode.collect { mode ->
                when (mode) {
                    PowerMode.CRITICAL -> {
                        // Emergency features only (spec 16.3).
                        ttsManager.speakImmediately(
                            "Battery very low. Only emergency features active.",
                            SpeechPriority.URGENT
                        )
                        cameraRepository.stop()
                        sttManager.stopContinuousListening()
                    }
                    PowerMode.MINIMAL -> {
                        ttsManager.speakImmediately(
                            "Battery low. Switching to minimal mode.",
                            SpeechPriority.URGENT
                        )
                        // Keep the camera running; pipeline rules already
                        // suppress non-critical announcements.
                        cameraRepository.start()
                    }
                    else -> Unit
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_SERVICE,
            getString(R.string.notif_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_service_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Starts foreground with only the FGS types whose runtime permissions
     * are actually granted (Android 14 throws otherwise).
     */
    private fun startForegroundWithType() {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        ServiceCompat.startForeground(this, Constants.NOTIFICATION_ID_MAIN, buildNotification(), type)
    }

    private fun buildNotification(statusText: String? = null): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DetectionForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = statusText ?: getString(R.string.notif_service_status_off)
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_visionaid)
            .setContentTitle(getString(R.string.notif_service_active))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: ConnectionStatus) {
        val text = when (status) {
            ConnectionStatus.CONNECTED ->
                if (cameraRepository.activeSource.value ==
                    com.visionaid.app.domain.models.FrameSource.PHONE_CAMERA
                ) getString(R.string.notif_service_status_phone)
                else getString(R.string.notif_service_status_esp32)
            else -> getString(R.string.notif_service_status_off)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            Constants.NOTIFICATION_ID_MAIN,
            buildNotification(text)
        )
    }

    companion object {
        private const val TAG = "DetectionService"
        const val ACTION_START = "com.visionaid.app.action.START"
        const val ACTION_STOP = "com.visionaid.app.action.STOP"

        /** Convenience for ViewModels/UI. */
        fun start(context: Context) {
            val intent = Intent(context, DetectionForegroundService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, DetectionForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
