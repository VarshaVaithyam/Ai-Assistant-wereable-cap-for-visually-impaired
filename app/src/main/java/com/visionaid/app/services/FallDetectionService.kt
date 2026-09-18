package com.visionaid.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.visionaid.app.R
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.repository.FallDetectionRepository
import com.visionaid.app.presentation.ui.main.MainActivity
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that continuously monitors accelerometer + gyroscope
 * (spec 6.1) and runs the emergency protocol when a fall is confirmed
 * (delegated to [FallDetectionRepository]).
 *
 * Sampling: SENSOR_DELAY_GAME (~50 Hz) — a good balance between latency
 * and battery; declare HIGH_SAMPLING_RATE_SENSORS for >200 Hz if needed.
 */
@AndroidEntryPoint
class FallDetectionService : LifecycleService(), SensorEventListener {

    @Inject lateinit var fallDetectionRepository: FallDetectionRepository
    @Inject lateinit var preferences: AppPreferences

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "Fall detection service creating")

        createNotificationChannel()
        startForegroundWithTypes()

        // Apply the configured sensitivity.
        fallDetectionRepository.applySensitivity(preferences.current.value.fallDetectionSensitivity)
        fallDetectionRepository.refreshHistory()

        registerSensors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    // ------------------------------------------------------------------
    // Sensors
    // ------------------------------------------------------------------

    private fun registerSensors() {
        val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = manager
        accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (accelerometer == null) {
            AppLogger.e(TAG, "No accelerometer on this device — fall detection disabled", null)
            return
        }
        manager.registerListener(this, accelerometer, Constants.SENSOR_SAMPLING_DELAY_US)
        gyroscope?.let { manager.registerListener(this, it, Constants.SENSOR_SAMPLING_DELAY_US) }
        AppLogger.i(TAG, "Sensors registered (accel=${accelerometer != null}, gyro=${gyroscope != null})")
    }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        // Use elapsedRealtime for monotonic timing across the state machine.
        val timestamp = android.os.SystemClock.elapsedRealtime()
        when (event.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val confirmed = try {
                    fallDetectionRepository.onAccelerometer(values, timestamp)
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "Accelerometer processing failed", t)
                    false
                }
                if (confirmed) {
                    AppLogger.w(TAG, "Fall confirmed by sensor state machine")
                    // Runs the full emergency protocol (vibrate -> voice ->
                    // STT confirmation -> SMS -> log -> follow-up).
                    fallDetectionRepository.onFallDetected()
                }
            }
            Sensor.TYPE_GYROSCOPE ->
                fallDetectionRepository.onGyroscope(values, timestamp)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* not used */ }

    override fun onDestroy() {
        AppLogger.i(TAG, "Fall detection service destroying")
        unregisterSensors()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ALERTS,
            getString(R.string.notif_channel_alerts),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_alerts_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundWithTypes() {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        // Include the location type only when permission is granted
        // (Android 14 throws a SecurityException otherwise).
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_visionaid)
            .setContentTitle(getString(R.string.notif_fall_active))
            .setContentText(getString(R.string.notif_fall_active_desc))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(this, Constants.NOTIFICATION_ID_FALL, notification, type)
    }

    companion object {
        private const val TAG = "FallService"
        const val ACTION_STOP = "com.visionaid.app.action.STOP_FALL"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FallDetectionService::class.java)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FallDetectionService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
