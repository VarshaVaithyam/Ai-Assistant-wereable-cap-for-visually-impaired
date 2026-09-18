package com.visionaid.app.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight file + logcat logger.
 *
 * Writes to <filesDir>/logs/visionaid.log with simple size-based rotation
 * (keeps [MAX_LOG_FILES] files of [MAX_LOG_BYTES] bytes each). Used so that
 * field issues can be diagnosed from the device without a debugger.
 */
object AppLogger {

    private const val TAG_PREFIX = "VisionAid/"
    private const val MAX_LOG_BYTES = 512 * 1024L   // 512 KB per file
    private const val MAX_LOG_FILES = 3

    @Volatile
    private var logDir: File? = null

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** Call once from [com.visionaid.app.VisionAidApplication]. */
    fun init(appContext: android.content.Context) {
        logDir = File(appContext.filesDir, "logs").apply { mkdirs() }
    }

    fun i(tag: String, message: String) = write("I", tag, message, null)
    fun d(tag: String, message: String) = write("D", tag, message, null)
    fun w(tag: String, message: String) = write("W", tag, message, null)
    fun e(tag: String, message: String, error: Throwable? = null) =
        write("E", tag, message, error)

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val logLine = buildString {
            append(dateFormat.format(Date()))
            append(' ').append(level).append('/').append(TAG_PREFIX).append(tag)
            append(": ").append(message)
            if (error != null) {
                append(" -> ")
                append(error.javaClass.simpleName)
                append(": ")
                append(error.message.orEmpty())
            }
        }
        when (level) {
            "E" -> android.util.Log.e(TAG_PREFIX + tag, message, error)
            "W" -> android.util.Log.w(TAG_PREFIX + tag, message, error)
            "I" -> android.util.Log.i(TAG_PREFIX + tag, message, error)
            else -> android.util.Log.d(TAG_PREFIX + tag, message, error)
        }
        try {
            val dir = logDir ?: return
            val file = File(dir, "visionaid.log")
            if (file.exists() && file.length() > MAX_LOG_BYTES) rotate(dir)
            file.appendText(logLine + "\n")
        } catch (_: Exception) {
            // Never let logging crash the app.
        }
    }

    private fun rotate(dir: File) {
        for (i in MAX_LOG_FILES - 1 downTo 1) {
            val src = File(dir, "visionaid.$i.log")
            val dst = File(dir, "visionaid.${i + 1}.log")
            if (src.exists()) src.renameTo(dst)
        }
        File(dir, "visionaid.log").renameTo(File(dir, "visionaid.1.log"))
        // Drop files beyond the ring buffer size.
        File(dir, "visionaid.${MAX_LOG_FILES + 1}.log").delete()
    }

    /** Returns the current log text (or null) — useful for a "share logs" feature. */
    fun dump(): String? = try {
        logDir?.let { dir -> File(dir, "visionaid.log").takeIf { it.exists() }?.readText() }
    } catch (_: Exception) {
        null
    }
}
