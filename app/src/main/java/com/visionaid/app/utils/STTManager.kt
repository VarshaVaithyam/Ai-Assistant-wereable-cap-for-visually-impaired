package com.visionaid.app.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.visionaid.app.domain.models.VoiceCommand
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Speech-to-text manager built on Android's SpeechRecognizer (spec 7.2).
 *
 * Two modes:
 *  1. Continuous listening for the wake word ("Hey Vision"), then command
 *     routing via [CommandListener].
 *  2. One-shot listening ([listenOnce]) used by the assistant screen and
 *     the emergency "are you okay?" protocol.
 *
 * The recognizer is fragile (engine busy errors, OEM quirks), so this class
 * aggressively restarts/recreates it on errors. All recognizer operations
 * happen on the main thread.
 */
@Singleton
class STTManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Callbacks for continuous mode. Implement in the detection service. */
    interface CommandListener {
        /** The wake word was heard; give the user feedback. */
        fun onWakeWord()
        /** A command (or generic query) was recognized. */
        fun onCommand(command: VoiceCommand, utterance: String)
    }

    @Volatile
    var commandListener: CommandListener? = null

    /** Optional live transcription for the assistant UI. */
    @Volatile
    var onPartialResult: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var continuousMode = false
    private val isListening = AtomicBoolean(false)
    private var restartAttempts = 0
    private var lastRestartAt = 0L

    // ------------------------------------------------------------------
    // Continuous wake-word listening
    // ------------------------------------------------------------------

    /** Starts continuous listening (no-op when already running). */
    fun startContinuousListening() {
        mainHandler.post {
            if (continuousMode) return@post
            continuousMode = true
            AppLogger.i(TAG, "Continuous listening started")
            startListeningInternal()
        }
    }

    /** Stops continuous listening and releases the recognizer. */
    fun stopContinuousListening() {
        mainHandler.post {
            continuousMode = false
            destroyRecognizer()
            AppLogger.i(TAG, "Continuous listening stopped")
        }
    }

    private fun startListeningInternal() {
        if (!continuousMode || isListening.get()) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            AppLogger.w(TAG, "Speech recognition not available on this device")
            return
        }
        try {
            val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                recognizer = it
            }
            engine.setRecognitionListener(continuousListener)
            engine.startListening(buildRecognizerIntent())
            isListening.set(true)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to start listening", t)
            scheduleRestart()
        }
    }

    private val continuousListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { /* listening */ }

        override fun onBeginningOfSpeech() { /* listening */ }

        override fun onRmsChanged(rmsdB: Float) { /* ignored */ }

        override fun onBufferReceived(buffer: ByteArray?) { /* ignored */ }

        override fun onEndOfSpeech() { /* utterance finished */ }

        override fun onError(error: Int) {
            isListening.set(false)
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restartAttempts = 0 // normal silence
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    destroyRecognizer()
                    AppLogger.w(TAG, "Recognizer busy; recreating")
                }
                SpeechRecognizer.ERROR_CLIENT -> destroyRecognizer()
                else -> AppLogger.d(TAG, "Recognition error $error")
            }
            scheduleRestart()
        }

        override fun onResults(results: Bundle?) {
            isListening.set(false)
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = texts?.firstOrNull()
            if (!best.isNullOrBlank()) {
                handleUtterance(best)
            }
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            texts?.firstOrNull()?.let { onPartialResult?.invoke(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) { /* ignored */ }
    }

    /** Strips the wake word, routes the remainder to a command or query. */
    private fun handleUtterance(utterance: String) {
        val text = utterance.lowercase().trim()
        AppLogger.d(TAG, "Heard: \"$text\"")

        // Detect + strip the wake word.
        var heardWakeWord = false
        var remainder = text
        for (wake in Constants.WAKE_WORDS) {
            val idx = remainder.indexOf(wake)
            if (idx >= 0) {
                heardWakeWord = true
                remainder = remainder.removeRange(idx, idx + wake.length).trim()
                break
            }
        }

        val listener = commandListener ?: return
        if (heardWakeWord) {
            listener.onWakeWord()
            if (remainder.isBlank()) return
        } else {
            // Without the wake word, only react to strong keywords ("help").
            if (!text.contains("help") && !text.contains("emergency")) return
        }

        val command = parseCommand(remainder.ifBlank { text })
        listener.onCommand(command, remainder.ifBlank { text })
    }

    // ------------------------------------------------------------------
    // One-shot listening
    // ------------------------------------------------------------------

    /**
     * Listens for a single utterance and returns the recognized text.
     * Returns null on timeout/error/cancellation.
     */
    suspend fun listenOnce(timeoutMs: Long = 8_000L): String? {
        // Pause continuous mode so the two don't fight over the recognizer.
        val wasContinuous = continuousMode
        if (wasContinuous) stopContinuousListening()
        try {
            val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    mainHandler.post {
                        try {
                            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                                if (cont.isActive) cont.resume(null)
                                return@post
                            }
                            val engine = SpeechRecognizer
                                .createSpeechRecognizer(context)
                                .also { recognizer = it }
                            engine.setRecognitionListener(object : RecognitionListener {
                                override fun onReadyForSpeech(params: Bundle?) {}
                                override fun onBeginningOfSpeech() {}
                                override fun onRmsChanged(rmsdB: Float) {}
                                override fun onBufferReceived(buffer: ByteArray?) {}
                                override fun onEndOfSpeech() {}
                                override fun onError(error: Int) {
                                    cleanup()
                                    if (cont.isActive) cont.resume(null)
                                }

                                override fun onResults(results: Bundle?) {
                                    val text = results
                                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.firstOrNull()
                                    cleanup()
                                    if (cont.isActive) cont.resume(text)
                                }

                                override fun onPartialResults(partialResults: Bundle?) {
                                    partialResults
                                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.firstOrNull()?.let { onPartialResult?.invoke(it) }
                                }

                                override fun onEvent(eventType: Int, params: Bundle?) {}

                                fun cleanup() {
                                    mainHandler.post {
                                        try {
                                            engine.destroy()
                                        } catch (_: Exception) {
                                        }
                                        // The field must not keep a destroyed engine:
                                        // continuous mode recreates lazily from null.
                                        if (recognizer === engine) recognizer = null
                                    }
                                }
                            })
                            engine.startListening(buildRecognizerIntent())
                        } catch (t: Throwable) {
                            AppLogger.e(TAG, "listenOnce failed", t)
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            }
            return result
        } finally {
            if (wasContinuous) startContinuousListening()
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun buildRecognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Prefer the on-device recognizer: lower latency + works offline.
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    /** Maps a phrase to a VoiceCommand (spec 7.2c). */
    fun parseCommand(text: String): VoiceCommand {
        val t = text.lowercase().trim()
        return when {
            listOf("what's ahead", "what is ahead", "whats ahead", "what do i see", "obstacle").any { t.contains(it) } ->
                VoiceCommand.WHATS_AHEAD
            listOf("describe scene", "describe the scene", "what do you see").any { t.contains(it) } ->
                VoiceCommand.DESCRIBE_SCENE
            listOf("read document", "read the document").any { t.contains(it) } ->
                VoiceCommand.READ_DOCUMENT
            listOf("read sign", "read the sign", "what does the sign", "what does that sign").any { t.contains(it) } ->
                VoiceCommand.READ_SIGN
            listOf("read text", "read the text", "read this", "read aloud").any { t.contains(it) } ->
                VoiceCommand.READ_TEXT
            listOf("where am i", "my location", "what street").any { t.contains(it) } ->
                VoiceCommand.WHERE_AM_I
            listOf("call guardian", "call contact", "call my").any { t.contains(it) } ->
                VoiceCommand.CALL_GUARDIAN
            listOf("stop", "quiet", "silence", "shut up", "be quiet").any { t.contains(it) } ->
                VoiceCommand.STOP_SPEAKING
            t == "help" || t.contains("send help") || t.contains("help me") ->
                VoiceCommand.HELP
            else -> VoiceCommand.ASSISTANT_QUERY
        }
    }

    /** True when the utterance cancels the emergency alert (spec 6.4). */
    fun isCancelPhrase(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        return Constants.CANCEL_PHRASES.any { t.contains(it) }
    }

    /** True when the utterance confirms sending the alert. */
    fun isConfirmPhrase(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        return Constants.CONFIRM_PHRASES.any { t.contains(it) }
    }

    private fun scheduleRestart() {
        if (!continuousMode) return
        val now = System.currentTimeMillis()
        // Avoid tight restart loops.
        if (now - lastRestartAt < 250) return
        lastRestartAt = now
        restartAttempts++
        val delay = if (restartAttempts > 5) 2_000L else 300L
        mainHandler.postDelayed({
            if (continuousMode && !isListening.get()) {
                startListeningInternal()
            }
        }, delay)
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        isListening.set(false)
    }

    fun destroy() {
        continuousMode = false
        mainHandler.post { destroyRecognizer() }
    }

    private companion object {
        const val TAG = "STTManager"
    }
}
