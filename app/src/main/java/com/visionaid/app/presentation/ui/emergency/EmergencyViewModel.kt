package com.visionaid.app.presentation.ui.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionaid.app.data.models.FallEvent
import com.visionaid.app.data.repository.FallDetectionRepository
import com.visionaid.app.domain.models.EmergencyPhase
import com.visionaid.app.domain.models.EmergencyState
import com.visionaid.app.domain.models.LocationInfo
import com.visionaid.app.domain.models.SpeechPriority
import com.visionaid.app.utils.LocationManager
import com.visionaid.app.utils.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Emergency screen state (spec 11.6): SOS trigger with countdown + cancel,
 * current location display and recent fall history.
 */
@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val fallDetectionRepository: FallDetectionRepository,
    private val locationManager: LocationManager,
    private val ttsManager: TTSManager
) : ViewModel() {

    val emergencyState: StateFlow<EmergencyState> = fallDetectionRepository.emergencyState

    val fallHistory: StateFlow<List<FallEvent>> = fallDetectionRepository.fallHistory

    val location: StateFlow<LocationInfo?> = locationManager.currentLocation
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        fallDetectionRepository.refreshHistory()
        viewModelScope.launch { locationManager.fetchCurrentLocation() }
    }

    /** Tap once on SOS: starts the 10 s countdown with voice prompt. */
    fun startSosCountdown() {
        if (emergencyState.value.phase == EmergencyPhase.COUNTDOWN) {
            // A second tap during the countdown cancels (spec 11.6).
            cancelAlert()
        } else {
            ttsManager.speakImmediately(
                "Emergency alert in 10 seconds. Tap again to cancel.",
                com.visionaid.app.domain.models.SpeechPriority.URGENT
            )
            fallDetectionRepository.startManualSos()
        }
    }

    /** Cancels an active countdown (button or "I'm fine"). */
    fun cancelAlert() {
        fallDetectionRepository.cancelEmergency()
    }

    /** Skips the countdown and sends the alert immediately. */
    fun sendNow() {
        ttsManager.speakImmediately(
            "Sending emergency alert now.", SpeechPriority.URGENT
        )
        fallDetectionRepository.confirmSendNow()
    }
}
