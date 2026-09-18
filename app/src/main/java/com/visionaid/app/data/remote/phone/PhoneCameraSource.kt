package com.visionaid.app.data.remote.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.util.Size
import com.visionaid.app.utils.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone rear-camera source used as a fallback when the ESP32-CAM stream is
 * unavailable (spec 16.2). Uses CameraX in RGBA_8888 image-analysis mode so
 * frames arrive as ready-to-process Bitmaps.
 *
 * Must be bound to a [LifecycleOwner]; the foreground service (a
 * LifecycleService) provides one.
 */
@Singleton
class PhoneCameraSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Called for every analyzer frame (on the analyzer thread). */
    @Volatile
    var onFrame: ((Bitmap) -> Unit)? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzerExecutor: ExecutorService? = null

    @Volatile
    private var running = false

    val isRunning: Boolean get() = running

    /**
     * Binds the rear camera to [lifecycleOwner]. Safe to call repeatedly.
     *
     * @param onError invoked on the main thread when binding fails.
     */
    fun start(lifecycleOwner: LifecycleOwner, onError: (Throwable) -> Unit) {
        if (running) return
        running = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(TARGET_WIDTH, TARGET_HEIGHT),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                )
                            )
                            .build()
                    )
                    // Frames arrive as RGBA — directly convertible to Bitmap.
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    // Drop stale frames instead of queueing them.
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val executor = Executors.newSingleThreadExecutor { r ->
                    Thread(r, "phone-camera-analyzer")
                }
                analyzerExecutor = executor
                analysis.setAnalyzer(executor, ::handleFrame)

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    analysis
                )
                AppLogger.i(TAG, "Phone camera fallback started")
            } catch (t: Throwable) {
                running = false
                AppLogger.e(TAG, "Phone camera bind failed", t)
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            cameraProvider?.unbindAll()
            analyzerExecutor?.shutdown()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Phone camera stop issue: ${t.message}")
        } finally {
            cameraProvider = null
            analyzerExecutor = null
        }
        AppLogger.i(TAG, "Phone camera fallback stopped")
    }

    // ------------------------------------------------------------------
    // Frame conversion
    // ------------------------------------------------------------------

    private fun handleFrame(proxy: ImageProxy) {
        try {
            val bitmap = proxyToBitmap(proxy)
            if (bitmap != null) onFrame?.invoke(bitmap)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Frame conversion failed: ${t.message}")
        } finally {
            proxy.close()
        }
    }

    /**
     * Converts an RGBA_8888 [ImageProxy] to a Bitmap, handling padded row
     * strides and display rotation.
     */
    private fun proxyToBitmap(proxy: ImageProxy): Bitmap? {
        val plane = proxy.planes.firstOrNull() ?: return null
        val width = proxy.width
        val height = proxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (pixelStride != 4) return null // unexpected format

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val buffer = plane.buffer

        if (rowStride == width * 4) {
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            // Rows are padded: copy row by row into a contiguous buffer.
            val contiguous = ByteBuffer.allocate(width * height * 4)
            val rowBytes = ByteArray(width * 4)
            val duplicate = buffer.duplicate()
            for (row in 0 until height) {
                val start = row * rowStride
                duplicate.position(start)
                duplicate.limit(minOf(start + width * 4, duplicate.capacity()))
                duplicate.get(rowBytes, 0, width * 4)
                contiguous.put(rowBytes)
                duplicate.clear()
            }
            contiguous.rewind()
            bitmap.copyPixelsFromBuffer(contiguous)
        }

        // Rotate into display orientation.
        val rotation = proxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        }
        return bitmap
    }

    private companion object {
        const val TAG = "PhoneCameraSource"
        // QVGA-ish analysis resolution: enough for 320px YOLO input.
        const val TARGET_WIDTH = 640
        const val TARGET_HEIGHT = 480
    }
}
