package com.visionaid.app.presentation.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionaid.app.data.models.AssistantMessage
import com.visionaid.app.data.repository.AssistantRepository
import com.visionaid.app.domain.models.SpeechPriority
import com.visionaid.app.domain.models.VoiceCommand
import com.visionaid.app.domain.usecases.AskAssistantUseCase
import com.visionaid.app.utils.STTManager
import com.visionaid.app.utils.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Assistant screen state (spec 8.3 conversation flow):
 * press mic -> STT captures question -> "Let me check..." -> Gemini ->
 * answer spoken + shown in the transcript.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val askAssistantUseCase: AskAssistantUseCase,
    private val assistantRepository: AssistantRepository,
    private val sttManager: STTManager,
    private val ttsManager: TTSManager
) : ViewModel() {

    val messages: StateFlow<List<AssistantMessage>> = assistantRepository.messages

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    /** Live partial transcription while listening. */
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    val isConfigured: Boolean get() = askAssistantUseCase.isConfigured()

    init {
        sttManager.onPartialResult = { text ->
            if (_listening.value) _partial.value = text
        }
    }

    /** Starts a one-shot voice capture round (mic button). */
    fun startListening() {
        if (_listening.value || _thinking.value) return
        _listening.value = true
        _partial.value = ""
        ttsManager.stop()
        viewModelScope.launch {
            try {
                val utterance = sttManager.listenOnce(10_000L)
                if (utterance.isNullOrBlank()) {
                    ttsManager.speak("I did not catch that. Please try again.")
                } else {
                    // Voice commands still work here (e.g. "read text").
                    val command = sttManager.parseCommand(utterance)
                    if (command != VoiceCommand.ASSISTANT_QUERY) {
                        handleLocalCommand(command, utterance)
                    } else {
                        ask(utterance)
                    }
                }
            } finally {
                _listening.value = false
                _partial.value = ""
            }
        }
    }

    /** Typed-question path (accessibility fallback for TalkBack users). */
    fun askTyped(question: String) {
        if (question.isBlank() || _thinking.value) return
        viewModelScope.launch { ask(question) }
    }

    private suspend fun ask(question: String) {
        _thinking.value = true
        try {
            askAssistantUseCase(question)
        } finally {
            _thinking.value = false
        }
    }

    /** Commands that don't need the cloud, handled on-device. */
    private fun handleLocalCommand(command: VoiceCommand, utterance: String) {
        viewModelScope.launch {
            when (command) {
                VoiceCommand.STOP_SPEAKING -> ttsManager.stop()
                else ->
                    // Route remaining commands through the same use case used
                    // by the service (keeps behavior consistent).
                    askAssistantUseCase(utterance, command)
            }
        }
    }

    fun clearConversation() {
        assistantRepository.clearHistory()
    }

    override fun onCleared() {
        sttManager.onPartialResult = null
        super.onCleared()
    }
}
