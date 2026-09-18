package com.visionaid.app.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.AssistantMessage
import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.data.models.AppSettings
import com.visionaid.app.data.remote.gemini.GeminiApiService
import com.visionaid.app.data.remote.gemini.GeminiContracts
import com.visionaid.app.data.remote.gemini.GeminiContent
import com.visionaid.app.data.remote.gemini.GeminiGenerationConfig
import com.visionaid.app.data.remote.gemini.GeminiInlineData
import com.visionaid.app.data.remote.gemini.GeminiPart
import com.visionaid.app.data.remote.gemini.GeminiRequest
import com.visionaid.app.data.remote.gemini.GeminiSystemInstruction
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conversational AI assistant backed by the Gemini REST API (spec 8).
 *
 *  - keeps the last [Constants.ASSISTANT_HISTORY_SIZE] exchanges for context
 *  - injects live context: recent YOLO detections, location, time
 *  - retries transient failures once
 *  - answers from a friendly "offline" message when no key / no network
 */
@Singleton
class AssistantRepository @Inject constructor(
    private val geminiApi: GeminiApiService,
    private val preferences: AppPreferences
) {

    /** Assistant persona (spec 8.1). */
    private val systemPrompt =
        "You are VisionAid AI Assistant, a helpful, concise, and friendly assistant " +
            "for a visually impaired person. You help them navigate the world, answer " +
            "questions, and provide assistance. Keep responses brief (1-3 sentences) " +
            "unless asked for detail. When describing locations or directions, use " +
            "clock-position references (e.g., '2 o'clock position') or simple " +
            "left/right/ahead. Always prioritize safety information. You have access " +
            "to what the person's camera sees, so you can describe their environment " +
            "when asked."

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    /** Live context injected into every request (spec 8.2b). */
    data class LiveContext(
        val detections: List<DetectedObject> = emptyList(),
        val locationDescription: String? = null,
        val lastReadText: String? = null
    )

    @Volatile
    var liveContext: LiveContext = LiveContext()

    // ------------------------------------------------------------------
    // History management
    // ------------------------------------------------------------------

    fun addUserMessage(text: String): AssistantMessage {
        val message = AssistantMessage(content = text, isUser = true)
        appendMessage(message)
        return message
    }

    private fun appendMessage(message: AssistantMessage) {
        val updated = _messages.value + message
        _messages.value = updated.takeLast(Constants.ASSISTANT_HISTORY_SIZE * 2)
    }

    fun clearHistory() {
        _messages.value = emptyList()
    }

    // ------------------------------------------------------------------
    // Query execution
    // ------------------------------------------------------------------

    /**
     * Sends [question] to Gemini and returns the spoken answer.
     *
     * @param currentFrame optional frame for vision questions
     * @param withImageHint true when the question looks vision-related
     */
    suspend fun ask(
        question: String,
        currentFrame: Bitmap? = null,
        includeImage: Boolean = false
    ): String {
        addUserMessage(question)
        val settings = preferences.current.value

        if (!GeminiContracts.isKeyConfigured(settings)) {
            val reply = "I need a Gemini API key to answer that. " +
                "Please ask a helper to add one in the settings."
            appendMessage(AssistantMessage(content = reply, isUser = false))
            return reply
        }

        return try {
            val answer = askWithRetry(question, currentFrame, includeImage, settings)
            appendMessage(
                AssistantMessage(content = answer, isUser = false, hasImage = includeImage)
            )
            answer
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppLogger.e(TAG, "Assistant request failed", t)
            val reply = GeminiContracts.friendlyError(t)
            appendMessage(AssistantMessage(content = reply, isUser = false))
            reply
        }
    }

    private suspend fun askWithRetry(
        question: String,
        frame: Bitmap?,
        includeImage: Boolean,
        settings: AppSettings
    ): String {
        val request = buildRequest(question, frame, includeImage, settings)
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            try {
                val response =
                    geminiApi.generateContent(settings.geminiModel, settings.geminiApiKey, request)
                return response.firstText()
                    ?: throw IllegalStateException("Empty response from Gemini")
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                lastError = t
                if (attempt == 0) kotlinx.coroutines.delay(500)
            }
        }
        throw lastError ?: IllegalStateException("Assistant request failed")
    }

    /** Assembles contents = [context] + history + [question] (+ image). */
    private fun buildRequest(
        question: String,
        frame: Bitmap?,
        includeImage: Boolean,
        @Suppress("UNUSED_PARAMETER") settings: AppSettings
    ): GeminiRequest {
        // 1. Context block (spec 8.2b/8.4).
        val contextBlock = buildContextBlock()

        // 2. History (roles user/model, as the API expects).
        val history = _messages.value
            .dropLast(1) // the question itself is added below
            .takeLast(Constants.ASSISTANT_HISTORY_SIZE * 2)
            .map { msg ->
                GeminiContent(
                    role = if (msg.isUser) "user" else "model",
                    parts = listOf(GeminiPart(text = msg.content))
                )
            }

        // 3. Current turn (optionally with an image).
        val questionParts = mutableListOf<GeminiPart>()
        if (includeImage && frame != null) {
            questionParts.add(
                GeminiPart(inlineData = GeminiInlineData(data = encodeImage(frame)))
            )
        }
        questionParts.add(GeminiPart(text = question))

        return GeminiRequest(
            contents = (contextBlock?.let { listOf(it) } ?: emptyList()) +
                history +
                listOf(GeminiContent(role = "user", parts = questionParts)),
            systemInstruction = GeminiSystemInstruction(
                parts = listOf(GeminiPart(text = systemPrompt))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.5,
                maxOutputTokens = Constants.GEMINI_MAX_OUTPUT_TOKENS
            )
        )
    }

    /** Builds the live-context user message (detections, location, time). */
    private fun buildContextBlock(): GeminiContent? {
        val ctx = liveContext
        val lines = mutableListOf<String>()
        lines += "Current time: ${java.text.SimpleDateFormat(
            "EEEE, MMMM d, HH:mm", java.util.Locale.US
        ).format(java.util.Date())}"

        if (ctx.detections.isNotEmpty()) {
            val summary = ctx.detections
                .groupBy { it.label }
                .entries.joinToString(", ") { (label, objects) ->
                    if (objects.size == 1) {
                        val obj = objects.first()
                        "${label} at ${obj.clockPosition} (${obj.distance})"
                    } else "${objects.size} ${label}s"
                }
            lines += "Objects currently visible to the user's camera: $summary"
        }
        ctx.locationDescription?.let { lines += "User location: $it" }
        ctx.lastReadText?.takeIf { it.isNotBlank() }
            ?.let { lines += "Most recent text read by OCR: \"$it\"" }

        if (lines.size <= 1) return null
        return GeminiContent(
            role = "user",
            parts = listOf(GeminiPart(text = "[System context]\n" + lines.joinToString("\n")))
        )
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        const val TAG = "AssistantRepository"
    }
}
