package com.visionaid.app.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.utils.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Best-effort watchdog that revives the detection services after a crash
 * or aggressive system kill (spec 16.1: "automatic service restart").
 *
 * NOTE: starting foreground services from the background is restricted on
 * Android 12+; revivals succeed mainly when the app has a recent
 * foreground presence or is exempt (e.g. visible notification). The worker
 * never crashes when the start is blocked — START_STICKY on the services
 * themselves remains the primary recovery mechanism.
 */
@HiltWorker
class ServiceRestarterWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferences: AppPreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = preferences.awaitFirst()
        if (!settings.serviceShouldRun) {
            AppLogger.d(TAG, "serviceShouldRun=false; nothing to do")
            return Result.success()
        }
        return try {
            DetectionForegroundService.start(applicationContext)
            FallDetectionService.start(applicationContext)
            AppLogger.i(TAG, "Detection services restarted by watchdog")
            Result.success()
        } catch (t: Throwable) {
            // Expected on Android 12+ background-start restrictions.
            AppLogger.w(TAG, "Watchdog restart blocked: ${t.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ServiceRestarter"
        private const val WORK_NAME = "visionaid_service_watchdog"

        /** Schedules the periodic watchdog (every 15 min, the WorkManager minimum). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceRestarterWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
