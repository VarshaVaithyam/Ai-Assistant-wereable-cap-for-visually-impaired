package com.visionaid.app.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.AppSettings
import com.visionaid.app.data.models.EmergencyContact
import com.visionaid.app.data.repository.ContactRepository
import com.visionaid.app.domain.models.SpeechPriority
import com.visionaid.app.services.BluetoothAudioService
import com.visionaid.app.utils.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings screen state (spec 11.5): ESP32, voice, detection, fall
 * detection, emergency contacts, AI assistant key.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val contactRepository: ContactRepository,
    private val ttsManager: TTSManager
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val contacts: StateFlow<List<EmergencyContact>> = contactRepository.observeContacts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- contact editor dialog state ---
    private val _dialogOpen = MutableStateFlow(false)
    val dialogOpen: StateFlow<Boolean> = _dialogOpen.asStateFlow()

    private val _editingContact = MutableStateFlow<EmergencyContact?>(null)
    val editingContact: StateFlow<EmergencyContact?> = _editingContact.asStateFlow()

    private val _dialogName = MutableStateFlow("")
    val dialogName: StateFlow<String> = _dialogName.asStateFlow()

    private val _dialogPhone = MutableStateFlow("")
    val dialogPhone: StateFlow<String> = _dialogPhone.asStateFlow()

    private val _dialogRelationship = MutableStateFlow("")
    val dialogRelationship: StateFlow<String> = _dialogRelationship.asStateFlow()

    // ------------------------------------------------------------------
    // ESP32
    // ------------------------------------------------------------------

    fun setEsp32Ip(ip: String) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setEsp32Config(
                ip.trim(),
                s.esp32Port,
                s.esp32HttpPort
            )
        }
    }

    fun setEsp32Port(port: String) {
        viewModelScope.launch {
            val parsed = port.toIntOrNull() ?: return@launch
            val s = preferences.current.value
            preferences.setEsp32Config(s.esp32IpAddress, parsed, s.esp32HttpPort)
        }
    }

    // ------------------------------------------------------------------
    // Voice
    // ------------------------------------------------------------------

    fun setVoiceSpeed(speed: Float) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setVoiceConfig(speed, s.voicePitch, s.voiceLanguage)
            ttsManager.applyVoiceSettings(speed, s.voicePitch, s.voiceLanguage)
        }
    }

    fun setVoicePitch(pitch: Float) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setVoiceConfig(s.voiceSpeed, pitch, s.voiceLanguage)
            ttsManager.applyVoiceSettings(s.voiceSpeed, pitch, s.voiceLanguage)
        }
    }

    fun setBluetoothAudio(context: android.content.Context, enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBluetoothAudioEnabled(enabled)
            ttsManager.setBluetoothOutput(enabled)
            if (enabled) BluetoothAudioService.start(context)
            else BluetoothAudioService.stop(context)
        }
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    fun setConfidence(value: Float) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setDetectionConfig(
                value, s.announcementCooldown, s.ocrEnabled,
                s.vlmEnabled, s.vlmPeriodicEnabled
            )
        }
    }

    fun setAnnouncementCooldown(ms: Int) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setDetectionConfig(
                s.detectionConfidenceThreshold, ms, s.ocrEnabled,
                s.vlmEnabled, s.vlmPeriodicEnabled
            )
        }
    }

    fun setOcrEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setDetectionConfig(
                s.detectionConfidenceThreshold, s.announcementCooldown, enabled,
                s.vlmEnabled, s.vlmPeriodicEnabled
            )
        }
    }

    fun setVlmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setDetectionConfig(
                s.detectionConfidenceThreshold, s.announcementCooldown, s.ocrEnabled,
                enabled, s.vlmPeriodicEnabled
            )
        }
    }

    fun setVlmPeriodic(enabled: Boolean) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setDetectionConfig(
                s.detectionConfidenceThreshold, s.announcementCooldown, s.ocrEnabled,
                s.vlmEnabled, enabled
            )
        }
    }

    fun setContinuousListening(enabled: Boolean) {
        viewModelScope.launch { preferences.setContinuousListening(enabled) }
    }

    // ------------------------------------------------------------------
    // Fall detection
    // ------------------------------------------------------------------

    fun setFallSensitivity(sensitivity: String) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setFallConfig(sensitivity, s.fallAlertCountdown)
        }
    }

    fun setFallCountdown(seconds: Int) {
        viewModelScope.launch {
            val s = preferences.current.value
            preferences.setFallConfig(s.fallDetectionSensitivity, seconds)
        }
    }

    // ------------------------------------------------------------------
    // Assistant
    // ------------------------------------------------------------------

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch { preferences.setGeminiApiKey(key) }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch { preferences.setGeminiModel(model) }
    }

    fun setUserName(name: String) {
        viewModelScope.launch { preferences.setUserName(name) }
    }

    // ------------------------------------------------------------------
    // Contacts
    // ------------------------------------------------------------------

    fun openContactDialog(existing: EmergencyContact? = null) {
        _editingContact.value = existing
        _dialogName.value = existing?.name.orEmpty()
        _dialogPhone.value = existing?.phoneNumber.orEmpty()
        _dialogRelationship.value = existing?.relationship.orEmpty()
    }

    fun setDialogName(value: String) { _dialogName.value = value }
    fun setDialogPhone(value: String) { _dialogPhone.value = value }
    fun setDialogRelationship(value: String) { _dialogRelationship.value = value }

    val dialogFormValid: Boolean
        get() = _dialogName.value.isNotBlank() && _dialogPhone.value.isNotBlank()

    fun saveDialogContact() {
        viewModelScope.launch {
            val existing = _editingContact.value
            val contact = EmergencyContact(
                id = existing?.id ?: 0,
                name = _dialogName.value.trim(),
                phoneNumber = _dialogPhone.value.trim(),
                relationship = _dialogRelationship.value.trim().ifBlank { "contact" },
                isPrimary = existing?.isPrimary ?: false
            )
            if (existing == null) {
                contactRepository.saveContact(contact)
            } else {
                contactRepository.updateContact(contact)
            }
            ttsManager.speak("Contact ${contact.name} saved.", SpeechPriority.NORMAL)
            closeDialog()
        }
    }

    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
            ttsManager.speak("Contact ${contact.name} removed.", SpeechPriority.NORMAL)
        }
    }

    fun closeDialog() {
        _editingContact.value = null
        _dialogName.value = ""
        _dialogPhone.value = ""
        _dialogRelationship.value = ""
    }
}
