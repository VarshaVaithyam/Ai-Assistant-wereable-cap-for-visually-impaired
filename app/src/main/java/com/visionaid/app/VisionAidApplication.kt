package com.visionaid.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.visionaid.app.utils.AppLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * - Boots the Hilt dependency graph (@HiltAndroidApp).
 * - Provides the WorkManager configuration backed by [HiltWorkerFactory]
 *   so Workers (e.g. [com.visionaid.app.services.ServiceRestarterWorker])
 *   can have dependencies injected.
 * - Initializes the local file logger used across the app.
 */
@HiltAndroidApp
class VisionAidApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i(TAG, "VisionAid application started")
    }

    private companion object {
        const val TAG = "VisionAidApp"
    }
}
