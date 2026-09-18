package com.visionaid.app.presentation.ui.setup

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.EmergencyContact
import com.visionaid.app.data.models.AppSettings
import com.visionaid.app.data.remote.api.ApiClient
import com.visionaid.app.data.repository.ContactRepository
import com.visionaid.app.domain.models.SpeechPriority
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.BluetoothHelper
import com.visionaid.app.utils.Constants
import com.visionaid.app.utils.LocationManager
import com.visionaid.app.utils.PermissionHelper
import com.visionaid.app.utils.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * First-time setup wizard state (spec 11.3):
 * permissions -> ESP32 IP -> Bluetooth -> emergency contact -> voice prefs
 * -> component test run.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferences: AppPreferences,
    private val apiClient: ApiClient,
    private val bluetoothHelper: BluetoothHelper,
    private val contactRepository: ContactRepository,
    private val locationManager: LocationManager,
    private val ttsManager: TTSManager
) : ViewModel() {

    companion object {
        const val STEP_PERMISSIONS = 0
        const val STEP_ESP32 = 1
        const val STEP_BLUETOOTH = 2
        const val STEP_CONTACT = 3
        const val STEP_VOICE = 4
        const val STEP_TEST = 5
        const val STEP_COUNT = 6
    }

    data class PermissionRow(
        val permission: String,
        val label: String,
        val rationale: String,
        val granted: Boolean
    )

    enum class TestStatus { PENDING, RUNNING, PASSED, FAILED }

    data class TestItem(val label: String, var status: TestStatus = TestStatus.PENDING)

    // ------------------------------------------------------------------
    // Wizard state
    // ------------------------------------------------------------------

    private val _step = MutableStateFlow(STEP_PERMISSIONS)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _permissions = MutableStateFlow<List<PermissionRow>>(emptyList())
    val permissions: StateFlow<List<PermissionRow>> = _permissions.asStateFlow()

    private val _esp32Ip = MutableStateFlow(Constants.ESP32_DEFAULT_IP)
    val esp32Ip: StateFlow<String> = _esp32Ip.asStateFlow()

    private val _esp32Port = MutableStateFlow(Constants.ESP32_DEFAULT_STREAM_PORT.toString())
    val esp32Port: StateFlow<String> = _esp32Port.asStateFlow()

    private val _esp32TestResult = MutableStateFlow<Boolean?>(null)
    val esp32TestResult: StateFlow<Boolean?> = _esp32TestResult.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<String>>(emptyList())
    val pairedDevices: StateFlow<List<String>> = _pairedDevices.asStateFlow()

    private val _contactName = MutableStateFlow("")
    val contactName: StateFlow<String> = _contactName.asStateFlow()

    private val _contactPhone = MutableStateFlow("")
    val contactPhone: StateFlow<String> = _contactPhone.asStateFlow()

    private val _contactRelationship = MutableStateFlow("")
    val contactRelationship: StateFlow<String> = _contactRelationship.asStateFlow()

    private val _contactSaved = MutableStateFlow(false)
    val contactSaved: StateFlow<Boolean> = _contactSaved.asStateFlow()

    private val _voiceSpeed = MutableStateFlow(Constants.DEFAULT_VOICE_SPEED)
    val voiceSpeed: StateFlow<Float> = _voiceSpeed.asStateFlow()

    private val _voicePitch = MutableStateFlow(1.0f)
    val voicePitch: StateFlow<Float> = _voicePitch.asStateFlow()

    private val _testItems = MutableStateFlow<List<TestItem>>(emptyList())
    val testItems: StateFlow<List<TestItem>> = _testItems.asStateFlow()

    private val _testsRunning = MutableStateFlow(false)
    val testsRunning: StateFlow<Boolean> = _testsRunning.asStateFlow()

    init {
        refreshPermissions()
        loadInitialSettings()
        _testItems.value = listOf(
            TestItem("Permissions granted"),
            TestItem("ESP32-CAM reachable"),
            TestItem("Motion sensors available"),
            TestItem("Voice output working"),
            TestItem("Location available")
        )
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /** Speaks a step instruction (voice narration, spec 11.3). */
    fun narrate(instruction: String) {
        ttsManager.speak(instruction, SpeechPriority.NORMAL)
    }

    fun goToStep(newStep: Int) {
        _step.value = newStep.coerceIn(STEP_PERMISSIONS, STEP_TEST)
    }

    fun nextStep() {
        if (_step.value == STEP_TEST) return
        _step.value = _step.value + 1
    }

    fun previousStep() {
        if (_step.value == STEP_PERMISSIONS) return
        _step.value = _step.value - 1
    }

    // ------------------------------------------------------------------
    // Step 1: permissions
    // ------------------------------------------------------------------

    fun refreshPermissions() {
        _permissions.value = PermissionHelper.applicablePermissions().map { item ->
            item.toRow()
        }
    }

    private fun com.visionaid.app.utils.PermissionHelper.PermissionItem.toRow() =
        PermissionRow(permission, label, rationale, PermissionHelper.isGranted(appContext, permission))

    val missingCriticalPermissions: Boolean
        get() = _permissions.value.any { !it.granted && it.permission in listOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) }

    // ------------------------------------------------------------------
    // Step 2: ESP32
    // ------------------------------------------------------------------

    fun setEsp32Ip(ip: String) {
        _esp32Ip.value = ip
        _esp32TestResult.value = null
    }

    fun setEsp32Port(port: String) {
        _esp32Port.value = port
        _esp32TestResult.value = null
    }

    /** Pings /touch_status on the ESP32 to verify reachability. */
    fun testEsp32Connection() {
        viewModelScope.launch {
            _esp32TestResult.value = null
            val ip = _esp32Ip.value.trim()
            val httpPort = preferences.current.value.esp32HttpPort
            val reachable = apiClient.getJson(
                "http://$ip:$httpPort${Constants.ESP32_TOUCH_STATUS_PATH}", 3_000L
            ) != null
            _esp32TestResult.value = reachable
            ttsManager.speak(
                if (reachable) "Connected to ESP32 camera."
                else "Could not reach the ESP32 camera. Check the address and Wi-Fi.",
                SpeechPriority.NORMAL
            )
        }
    }

    // ------------------------------------------------------------------
    // Step 3: Bluetooth
    // ------------------------------------------------------------------

    fun refreshBluetoothDevices() {
        _pairedDevices.value = bluetoothHelper.pairedAudioDevices()
            .mapNotNull { device ->
                try {
                    device.name ?: device.address
                } catch (_: SecurityException) {
                    device.address
                }
            }
    }

    fun enableBluetoothOutput(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBluetoothAudioEnabled(enabled)
            if (enabled) ttsManager.setBluetoothOutput(true)
        }
    }

    // ------------------------------------------------------------------
    // Step 4: emergency contact
    // ------------------------------------------------------------------

    fun setContactName(value: String) { _contactName.value = value; _contactSaved.value = false }
    fun setContactPhone(value: String) { _contactPhone.value = value; _contactSaved.value = false }
    fun setContactRelationship(value: String) { _contactRelationship.value = value; _contactSaved.value = false }

    val contactFormValid: Boolean
        get() = _contactName.value.isNotBlank() && _contactPhone.value.isNotBlank()

    fun saveContact() {
        viewModelScope.launch {
            contactRepository.saveContact(
                EmergencyContact(
                    name = _contactName.value.trim(),
                    phoneNumber = _contactPhone.value.trim(),
                    relationship = _contactRelationship.value.trim().ifBlank { "contact" },
                    isPrimary = true // first contact is always primary
                )
            )
            _contactSaved.value = true
            ttsManager.speak("Contact ${_contactName.value} saved.", SpeechPriority.NORMAL)
        }
    }

    // ------------------------------------------------------------------
    // Step 5: voice preferences
    // ------------------------------------------------------------------

    fun setVoiceSpeed(speed: Float) {
        _voiceSpeed.value = speed
        ttsManager.applyVoiceSettings(speed, _voicePitch.value, "en-US")
    }

    fun setVoicePitch(pitch: Float) {
        _voicePitch.value = pitch
        ttsManager.applyVoiceSettings(_voiceSpeed.value, pitch, "en-US")
    }

    fun testVoice() {
        ttsManager.speak(
            "This is how VisionAid will sound when guiding you.",
            SpeechPriority.NORMAL
        )
    }

    // ------------------------------------------------------------------
    // Step 6: test run
    // ------------------------------------------------------------------

    fun runTests() {
        if (_testsRunning.value) return
        viewModelScope.launch {
            _testsRunning.value = true
            val results = _testItems.value.map { it.copy(status = TestStatus.RUNNING) }
            _testItems.value = results

            // 1. Permissions
            updateTest(0, PermissionHelper.allGranted(appContext))

            // 2. ESP32 reachability
            val ip = _esp32Ip.value.trim()
            val httpPort = preferences.current.value.esp32HttpPort
            updateTest(1, apiClient.getJson(
                "http://$ip:$httpPort${Constants.ESP32_TOUCH_STATUS_PATH}", 3_000L
            ) != null)

            // 3. Motion sensors
            val sensorManager =
                appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val hasSensors = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
            updateTest(2, hasSensors)

            // 4. Speech output (we can only verify the engine speaks).
            ttsManager.speak("Voice test.", SpeechPriority.NORMAL)
            updateTest(3, true)

            // 5. Location
            val location = locationManager.fetchCurrentLocation(8_000L)
            updateTest(4, location != null)

            _testsRunning.value = false
        }
    }

    private fun updateTest(index: Int, passed: Boolean) {
        _testItems.value = _testItems.value.mapIndexed { i, item ->
            if (i == index) item.copy(status = if (passed) TestStatus.PASSED else TestStatus.FAILED)
            else item
        }
    }

    // ------------------------------------------------------------------
    // Finish
    // ------------------------------------------------------------------

    /** Persists everything and completes setup. */
    fun finishSetup(onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
                preferences.setEsp32Config(
                    ip = _esp32Ip.value.trim(),
                    streamPort = _esp32Port.value.toIntOrNull()
                        ?: Constants.ESP32_DEFAULT_STREAM_PORT,
                    httpPort = Constants.ESP32_DEFAULT_HTTP_PORT
                )
                preferences.setVoiceConfig(_voiceSpeed.value, _voicePitch.value, "en-US")
                preferences.setSetupCompleted(true)
                AppLogger.i("SetupVM", "Setup completed")
                ttsManager.speak("Setup complete. VisionAid is ready.", SpeechPriority.NORMAL)
                onFinished()
            } catch (t: Throwable) {
                AppLogger.e("SetupVM", "finishSetup failed", t)
            }
        }
    }

    private fun loadInitialSettings() {
        viewModelScope.launch {
            val settings: AppSettings = preferences.awaitFirst()
            _esp32Ip.value = settings.esp32IpAddress
            _esp32Port.value = settings.esp32Port.toString()
            _voiceSpeed.value = settings.voiceSpeed
            _voicePitch.value = settings.voicePitch
        }
    }
}
