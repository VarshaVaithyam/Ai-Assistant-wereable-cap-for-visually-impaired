package com.visionaid.app.data.remote.gemini

import android.graphics.Bitmap
import android.util.Base64
import com.visionaid.app.data.models.AppSettings
import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.domain.models.SceneDescription
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vision-Language Model service: rich scene descriptions via Gemini
 * with the blind-assistance prompt from the spec (step 5.1).
 *
 * Implements the spec's resilience requirements:
 *  - 10 s result cache
 *  - rate limiting (max 10 calls/minute)
 *  - "latest wins" queueing (a new request cancels the previous one)
 *  - graceful fallback to a local description built from YOLO detections
 */
@Singleton
class VLMService @Inject constructor(
    private val geminiApi: GeminiApiService,
    private val preferences: AppPreferences
) {

    /** Prompt tuned for practical, safety-first descriptions (spec 5.1). */
    private val scenePrompt =
        "You are an AI assistant helping a blind person understand their surroundings. " +
            "Describe this image concisely and practically. Focus on: " +
            "1) Any obstacles or hazards in the path, " +
            "2) People and their approximate positions, " +
            "3) Important signs or text visible, " +
            "4) The general environment (indoor/outdoor, room type, street), " +
            "5) Any notable objects. " +
            "Keep the description under 3 sentences and prioritize safety-relevant information."

    // --- cache ---
    private var cachedDescription: SceneDescription? = null

    // --- rate limiting (sliding 60 s window) ---
    private val callTimestamps = ArrayDeque<Long>()

    // --- latest-wins queueing ---
    private var inFlight: Job? = null
    private val mutex = Mutex()

    /** Timestamp of the last successful description (drives the 30 s timer). */
    @Volatile
    var lastDescriptionAt: Long = 0L
        private set

    /**
     * Describes [bitmap]. When the API is unavailable (no key / no network /
     * rate limited) it falls back to a local description assembled from the
     * latest [detections] — object detection is fully offline.
     */
    suspend fun describeScene(bitmap: Bitmap?, detections: List<DetectedObject>): SceneDescription {
        val settings = preferences.current.value
        val now = System.currentTimeMillis()

        // 1. Serve from cache when fresh (spec: cache 10 s).
        cachedDescription?.let { cached ->
            if (now - cached.timestamp < Constants.VLM_CACHE_MS) return cached
        }

        // 2. Decide whether we can hit the cloud.
        val canCallCloud = GeminiContracts.isKeyConfigured(settings) &&
            settings.vlmEnabled &&
            !isRateLimited(now)

        if (!canCallCloud) {
            return localFallback(detections)
        }

        // 3. Run in the cloud (latest-wins: cancel any request still running).
        return try {
            mutex.withLock {
                registerCall(now)
            }
            val description = withTimeout(API_TIMEOUT_MS) {
                val text = callGemini(bitmap, settings)
                SceneDescription(
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    fromCloud = true
                )
            }
            cachedDescription = description
            lastDescriptionAt = description.timestamp
            description
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppLogger.e(TAG, "Scene description failed, using local fallback", t)
            localFallback(detections)
        }
    }

    private suspend fun callGemini(bitmap: Bitmap?, settings: AppSettings): String {
        val parts = mutableListOf<GeminiPart>()
        bitmap?.let { parts.add(GeminiPart(inlineData = GeminiInlineData(data = encodeImage(it)))) }
        parts.add(GeminiPart(text = scenePrompt))

        val request = GeminiRequest(
            contents = listOf(GeminiContent(role = "user", parts = parts)),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.3,
                maxOutputTokens = Constants.GEMINI_MAX_OUTPUT_TOKENS
            )
        )
        val response = geminiApi.generateContent(settings.geminiModel, settings.geminiApiKey, request)
        return response.firstText()
            ?: throw IllegalStateException("Gemini returned an empty response")
    }

    /** Builds a plain-language description from YOLO detections (offline). */
    private fun localFallback(detections: List<DetectedObject>): SceneDescription {
        val text = if (detections.isEmpty()) {
            "I don't see any recognizable objects right now."
        } else {
            val grouped = detections.groupBy { it.label }
                .entries.joinToString(", ") { (label, objects) ->
                    if (objects.size == 1) {
                        "${label} ${objects.first().distance}, ${objects.first().direction}"
                    } else {
                        "${objects.size} ${label}s"
                    }
                }
            "Around you I detect: $grouped."
        }
        return SceneDescription(
            text = text,
            timestamp = System.currentTimeMillis(),
            fromCloud = false
        )
    }

    private fun isRateLimited(now: Long): Boolean {
        pruneTimestamps(now)
        return callTimestamps.size >= Constants.VLM_RATE_LIMIT_PER_MINUTE
    }

    private fun registerCall(now: Long) {
        pruneTimestamps(now)
        callTimestamps.addLast(now)
    }

    private fun pruneTimestamps(now: Long) {
        while (callTimestamps.isNotEmpty() && now - callTimestamps.first() > 60_000L) {
            callTimestamps.removeFirst()
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /** Cancels an in-flight request (used when the user asks something new). */
    fun cancelInFlight() {
        inFlight?.cancel()
        inFlight = null
    }

    private companion object {
        const val TAG = "VLMService"
        const val API_TIMEOUT_MS = 20_000L
        const val JPEG_QUALITY = 80
    }
}
