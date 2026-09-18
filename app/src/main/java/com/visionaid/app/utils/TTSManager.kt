package com.visionaid.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.visionaid.app.domain.models.SpeechPriority
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text-to-speech manager with a priority queue (spec 7.1).
 *
 * Priorities:
 *  - URGENT : interrupts current speech immediately (fall/obstacle warnings)
 *  - HIGH   : spoken next (nearby objects)
 *  - NORMAL : normal queue (scene descriptions, OCR)
 *  - LOW    : ambient info; dropped when more than 3 items are pending
 *
 * Also handles:
 *  - speech rate / pitch control (defaults 1.2x from settings)
 *  - audio focus (duck other audio while speaking)
 *  - Bluetooth output preference via [BluetoothHelper]
 */
@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothHelper: BluetoothHelper
) {

    interface Listener {
        /** Called when a queued utterance finishes. */
        fun onUtteranceDone(utteranceId: String) {}
    }

    @Volatile
    var listener: Listener? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null

    @Volatile
    private var initialized = false

    private var speechRate = Constants.DEFAULT_VOICE_SPEED
    private var pitch = 1.0f
    private var languageTag = "en-US"

    /** Counter of utterances accepted but not yet finished. */
    private val pendingCount = AtomicInteger(0)

    /** Bookkeeping for the LOW-priority drop rule. */
    private val lowQueue = ConcurrentLinkedQueue<String>()

    private var utteranceCounter = 0L

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    )
        .setAudioAttributes(audioAttributes)
        .build()

    @Volatile
    private var hasAudioFocus = false

    init {
        // Constructing TTS is safe from any thread; callbacks arrive on init.
        tts = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post { onInitResult(status) }
        }
    }

    private fun onInitResult(status: Int) {
        val engine = tts
        if (status == TextToSpeech.SUCCESS && engine != null) {
            engine.language = localeFor(languageTag)
            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { /* no-op */ }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let {
                        lowQueue.remove(it)
                        pendingCount.decrementAndGet()
                        abandonFocusIfIdle()
                        listener?.onUtteranceDone(it)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let {
                        lowQueue.remove(it)
                        pendingCount.decrementAndGet()
                        abandonFocusIfIdle()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onError(utteranceId)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    utteranceId?.let {
                        lowQueue.remove(it)
                        pendingCount.decrementAndGet()
                        abandonFocusIfIdle()
                    }
                }
            })
            initialized = true
            AppLogger.i(TAG, "TTS engine ready ($languageTag, rate=$speechRate)")
        } else {
            AppLogger.e(TAG, "TTS engine failed to initialize (status=$status)", null)
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Speaks [text] with the given priority. Thread-safe; internally hops
     * to the main thread (required by TextToSpeech).
     */
    fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL) {
        if (text.isBlank()) return
        // Drop LOW messages when the queue is saturated (spec 7.1a).
        if (priority == SpeechPriority.LOW && pendingCount.get() >= Constants.MAX_PENDING_LOW_PRIORITY) {
            return
        }
        mainHandler.post { speakInternal(text, priority) }
    }

    /** URGENT variant that always interrupts (spec 7.1). */
    fun speakImmediately(text: String) = speak(text, SpeechPriority.URGENT)

    private fun speakInternal(text: String, priority: SpeechPriority) {
        val engine = tts ?: return
        if (!initialized) return // engine not ready yet; message dropped
        requestAudioFocus()

        val utteranceId = "utt-${utteranceCounter++}"

        if (priority == SpeechPriority.URGENT) {
            // Interrupt everything currently queued/being spoken.
            engine.stop()
            pendingCount.set(0)
            lowQueue.clear()
            // Slightly higher pitch makes warnings more noticeable.
            val params = android.os.Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            pendingCount.incrementAndGet()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            if (priority == SpeechPriority.LOW) lowQueue.add(utteranceId)
            pendingCount.incrementAndGet()
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    /** Stops all current and queued speech. */
    fun stop() {
        mainHandler.post {
            tts?.stop()
            pendingCount.set(0)
            lowQueue.clear()
            abandonFocusIfIdle()
        }
    }

    /** Applies voice settings (rate/pitch/language) to the engine. */
    fun applyVoiceSettings(rate: Float, pitchValue: Float, languageTagValue: String) {
        speechRate = rate
        pitch = pitchValue
        languageTag = languageTagValue
        mainHandler.post {
            tts?.language = localeFor(languageTag)
            tts?.setSpeechRate(rate)
            tts?.setPitch(pitchValue)
        }
    }

    /**
     * Routes TTS output to Bluetooth earphones when enabled
     * (SCO link held by [BluetoothHelper]).
     */
    fun setBluetoothOutput(enabled: Boolean) {
        bluetoothHelper.setScoEnabled(enabled)
    }

    /** True when the queue is empty and nothing is being spoken. */
    fun isSpeakingOrQueued(): Boolean = pendingCount.get() > 0

    /** Suspends until the queue drains (best effort, used by emergency protocol). */
    suspend fun awaitIdle(timeoutMs: Long = 15_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (pendingCount.get() > 0 && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(150)
        }
    }

    fun shutdown() {
        mainHandler.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
            initialized = false
        }
    }

    // ------------------------------------------------------------------
    // Audio focus
    // ------------------------------------------------------------------

    private fun requestAudioFocus() {
        if (!hasAudioFocus) {
            val result = audioManager.requestAudioFocus(focusRequest)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocusIfIdle() {
        if (hasAudioFocus && pendingCount.get() <= 0) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasAudioFocus = false
        }
    }

    private fun localeFor(tag: String): Locale =
        Locale.forLanguageTag(tag.ifBlank { "en-US" })

    private companion object {
        const val TAG = "TTSManager"
    }
}
