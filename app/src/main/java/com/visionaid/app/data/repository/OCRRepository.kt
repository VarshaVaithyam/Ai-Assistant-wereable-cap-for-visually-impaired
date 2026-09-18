package com.visionaid.app.data.repository

import com.visionaid.app.domain.models.OcrMode
import com.visionaid.app.utils.Constants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared state + triggers for the OCR pipeline.
 *
 * The UI (MainScreen "Read Text") and voice commands emit manual-read
 * requests through [manualReadRequests]; the foreground service collects
 * them and runs [com.visionaid.app.ml.TextRecognizer] on the latest frame.
 */
@Singleton
class OCRRepository @Inject constructor() {

    /** One-shot read requests (mode + signsOnly flag). */
    private val _manualReadRequests = MutableSharedFlow<ManualReadRequest>(
        replay = 0,
        extraBufferCapacity = 4
    )
    val manualReadRequests: SharedFlow<ManualReadRequest> = _manualReadRequests.asSharedFlow()

    /** Last text read aloud (kept for the assistant's context injection). */
    private val _lastReadText = MutableStateFlow("")
    val lastReadText: StateFlow<String> = _lastReadText.asStateFlow()

    data class ManualReadRequest(val mode: OcrMode, val signsOnly: Boolean = false)

    /** Fire a manual read (UI button or voice command). */
    fun requestManualRead(mode: OcrMode = OcrMode.MANUAL_READ, signsOnly: Boolean = false) {
        _manualReadRequests.tryEmit(ManualReadRequest(mode, signsOnly))
    }

    fun recordReadText(text: String) {
        if (text.isNotBlank()) _lastReadText.value = text.take(500)
    }
}
