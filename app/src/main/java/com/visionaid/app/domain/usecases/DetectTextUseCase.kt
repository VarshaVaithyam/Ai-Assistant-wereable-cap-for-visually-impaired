package com.visionaid.app.domain.usecases

import com.visionaid.app.data.repository.CameraRepository
import com.visionaid.app.data.repository.OCRRepository
import com.visionaid.app.domain.models.OcrMode
import com.visionaid.app.ml.TextRecognizer
import com.visionaid.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * On-demand OCR (spec 4.3): the manual read modes triggered by voice
 * commands ("read text", "read sign", "read document") and UI buttons.
 *
 * The continuous background scanning mode lives in [ProcessFrameUseCase].
 */
class DetectTextUseCase @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val textRecognizer: TextRecognizer,
    private val ocrRepository: OCRRepository
) {

    /**
     * @param mode MANUAL_READ (all text + positions), DOCUMENT (ordered
     *        prose) or CONTINUOUS (single scan).
     * @param signsOnly maps the "read sign" command: large text only.
     * @return the text that was read (empty on failure).
     */
    suspend operator fun invoke(
        mode: OcrMode = OcrMode.MANUAL_READ,
        signsOnly: Boolean = false
    ): String {
        val frame = cameraRepository.captureLatestFrame()
        if (frame == null) {
            return "The camera is not active yet."
        }
        return try {
            withContext(Dispatchers.Default) {
                val text = textRecognizer.process(frame, mode, signsOnly)
                ocrRepository.recordReadText(text)
                text
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "On-demand OCR failed", t)
            "Sorry, I could not read the text right now."
        }
    }

    private companion object {
        const val TAG = "DetectText"
    }
}
