package com.visionaid.app.data.remote.esp32

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.visionaid.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receives the MJPEG ("multipart/x-mixed-replace") stream served by the
 * ESP32-CAM at http://<ip>:<port>/stream and emits decoded Bitmaps.
 *
 * Parsing strategy (robust against different ESP32 firmwares):
 *  1. Read HTTP response headers line by line.
 *  2. Between boundary markers, look for a "Content-Length" header — the
 *     standard ESP32 camera web server sends one — and then read exactly
 *     that many bytes as a JPEG frame.
 *  3. If a part has no Content-Length, fall back to scanning for the JPEG
 *     SOI (0xFFD8) / EOI (0xFFD9) markers.
 *
 * Reconnection with exponential backoff is handled upstream by
 * [com.visionaid.app.data.repository.CameraRepository].
 */
@Singleton
class ESP32CamService @Inject constructor(
    baseClient: OkHttpClient
) {

    /** Dedicated client: the MJPEG connection is long-lived, so no read timeout. */
    private val streamClient: OkHttpClient = baseClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Opens the stream and emits frames until the connection drops or the
     * collecting coroutine is cancelled. Throws on connection errors so the
     * caller can apply its retry policy.
     */
    fun streamFrames(host: String, port: Int, path: String = "/stream"): Flow<Bitmap> =
        flow {
            val url = "http://$host:$port$path"
            AppLogger.i(TAG, "Connecting to MJPEG stream at $url")
            val request = Request.Builder().url(url).header("Connection", "close").build()

            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("ESP32 stream HTTP error: ${response.code}")
                }
                val body = response.body ?: throw IOException("ESP32 stream body was null")
                val contentType = body.contentType()?.toString().orEmpty()
                AppLogger.d(TAG, "Stream opened. Content-Type: $contentType")

                emitAllFrames(body.byteStream())
            }
        }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------
    // MJPEG parsing
    // ------------------------------------------------------------------

    private suspend fun emitAllFrames(input: InputStream) {
        val stream = BufferedInputStream(input, BUFFER_SIZE)
        while (currentCoroutineContext().isActive) {
            val frame = readNextFrame(stream) ?: break // null == EOF
            decodeFrame(frame)?.let { emit(it) }
        }
    }

    /** Reads one multipart part and returns the raw JPEG bytes (or null at EOF). */
    private fun readNextFrame(stream: BufferedInputStream): ByteArray? {
        return try {
            var contentLength = -1
            // 1. Read headers until the blank line that ends them.
            while (true) {
                val line = readLine(stream) ?: return null
                if (line.isEmpty()) break // end of headers
                val lower = line.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = lower.substringAfter("content-length:").trim().toIntOrNull() ?: -1
                }
                // Boundary lines ("--boundary...") and content-type lines are skipped.
            }
            when {
                contentLength > 0 -> readExact(stream, contentLength)
                else -> readUntilJpegMarkers(stream) // firmware without Content-Length
            }
        } catch (t: Throwable) {
            AppLogger.d(TAG, "Stream read ended: ${t.message}")
            null
        }
    }

    /** Reads exactly [length] bytes (one JPEG frame). */
    private fun readExact(stream: InputStream, length: Int): ByteArray? {
        if (length > MAX_FRAME_BYTES) return null
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = stream.read(buffer, offset, length - offset)
            if (read == -1) return null
            offset += read
        }
        return buffer
    }

    /**
     * Fallback parser: scan for the JPEG Start-Of-Image marker (0xFFD8),
     * then read until the End-Of-Image marker (0xFFD9).
     */
    private fun readUntilJpegMarkers(stream: InputStream): ByteArray? {
        // Find SOI.
        var prev = stream.read()
        if (prev == -1) return null
        while (true) {
            val cur = stream.read()
            if (cur == -1) return null
            if (prev == 0xFF && cur == 0xD8) break
            prev = cur
        }
        // Read until EOI.
        val out = java.io.ByteArrayOutputStream(16 * 1024)
        out.write(0xFF); out.write(0xD8)
        var last = -1
        while (true) {
            val b = stream.read()
            if (b == -1) return null
            out.write(b)
            if (last == 0xFF && b == 0xD9) {
                return out.toByteArray()
            }
            last = b
        }
    }

    private fun readLine(stream: InputStream): String? {
        val sb = StringBuilder(64)
        while (true) {
            val c = stream.read()
            if (c == -1) return null
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun decodeFrame(bytes: ByteArray): Bitmap? {
        if (bytes.size < 4) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            AppLogger.w(TAG, "Failed to decode a ${bytes.size}-byte frame")
        }
        return bitmap
    }

    private companion object {
        const val TAG = "ESP32CamService"
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_FRAME_BYTES = 2 * 1024 * 1024 // 2 MB safety cap
    }
}
