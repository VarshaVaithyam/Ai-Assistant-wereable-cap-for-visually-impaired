package com.visionaid.app.data.remote.gemini

import com.google.gson.annotations.SerializedName
import com.visionaid.app.data.models.AppSettings
import com.visionaid.app.utils.Constants
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.UnknownHostException

/**
 * Retrofit interface for the Google Gemini REST API
 * (https://generativelanguage.googleapis.com).
 *
 * Using the REST endpoint directly (instead of a client SDK) keeps prompt
 * engineering, retries and error handling fully under our control.
 */
interface GeminiApiService {

    /**
     * Calls `POST v1beta/models/{model}:generateContent`.
     *
     * @param model e.g. "gemini-2.5-flash"
     * @param key   user-provided API key
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") key: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// ======================================================================
// Request DTOs
// ======================================================================

data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("systemInstruction")
    val systemInstruction: GeminiSystemInstruction? = null,
    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val role: String,             // "user" or "model"
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    @SerializedName("inline_data")
    val inlineData: GeminiInlineData? = null
)

/** Bitmap -> JPEG bytes -> Base64 (NO_WRAP). */
data class GeminiInlineData(
    @SerializedName("mime_type")
    val mimeType: String = "image/jpeg",
    val data: String
)

data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

data class GeminiGenerationConfig(
    val temperature: Double? = null,
    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int? = null
)

// ======================================================================
// Response DTOs
// ======================================================================

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
) {
    /** Concatenated text of the first candidate, or null. */
    fun firstText(): String? = candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.mapNotNull { it.text }
        ?.joinToString(separator = "")
        ?.takeIf { it.isNotBlank() }
}

data class GeminiCandidate(
    val content: GeminiContent? = null
)

data class GeminiError(
    val code: Int? = null,
    val message: String? = null
)

// ======================================================================
// Helpers shared by VLMService and AssistantRepository
// ======================================================================

object GeminiContracts {

    /** Maps any Gemini/transport failure to a short, user-friendly message. */
    fun friendlyError(t: Throwable): String = when (t) {
        is UnknownHostException ->
            "No internet connection. This feature needs a network link."
        is HttpException -> when (t.code()) {
            400 -> "The AI service rejected the request. Check the API key."
            403, 401 -> "The Gemini API key was rejected. Please check Settings."
            429 -> "The AI service is rate limited. Please wait a moment."
            else -> "The AI service returned an error (${t.code()})."
        }
        else -> "The AI service could not be reached. Please try again."
    }

    fun isKeyConfigured(settings: AppSettings): Boolean =
        settings.geminiApiKey.isNotBlank() &&
            settings.geminiApiKey != Constants.GEMINI_PLACEHOLDER_KEY
}
