package com.visionaid.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.visionaid.app.R
import com.visionaid.app.presentation.ui.main.MainActivity
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.BluetoothHelper
import com.visionaid.app.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the Bluetooth SCO audio link alive while the app is running so all
 * TTS output reaches the user's earphones (spec 7.3 / structure item).
 *
 * Started when the user enables Bluetooth output in settings/setup;
 * stopped when disabled.
 */
@AndroidEntryPoint
class BluetoothAudioService : LifecycleService() {

    @Inject lateinit var bluetoothHelper: BluetoothHelper

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        val hasConnectPermission = bluetoothHelper.hasConnectPermission()
        if (hasConnectPermission) {
            val openIntent = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification =
                NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_SERVICE)
                    .setSmallIcon(R.drawable.ic_stat_visionaid)
                    .setContentTitle(getString(R.string.notif_bluetooth_active))
                    .setContentText(getString(R.string.notif_bluetooth_active_desc))
                    .setOngoing(true)
                    .setContentIntent(openIntent)
                    .build()
            ServiceCompat.startForeground(
                this, Constants.NOTIFICATION_ID_BLUETOOTH, notification, type
            )
            AppLogger.i(TAG, "Bluetooth audio service started")
        } else {
            // Without BLUETOOTH_CONNECT we cannot hold a connectedDevice FGS.
            AppLogger.w(TAG, "Missing BLUETOOTH_CONNECT; stopping audio service")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_SERVICE,
            getString(R.string.notif_channel_service),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "BluetoothAudioService"
        const val ACTION_STOP = "com.visionaid.app.action.STOP_BT"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, BluetoothAudioService::class.java)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BluetoothAudioService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
