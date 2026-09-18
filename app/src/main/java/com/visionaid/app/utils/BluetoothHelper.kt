package com.visionaid.app.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bluetooth audio helper (spec 7.3).
 *
 * Responsibilities:
 *  - expose paired audio devices (needs BLUETOOTH_CONNECT on Android 12+)
 *  - hold the SCO link so TTS reaches the earphones
 *  - auto-reconnect when the SCO link drops
 *  - publish connection status to the UI
 *
 * On API 31+ routing uses [AudioManager.setCommunicationDevice]; older
 * platforms use the legacy startBluetoothSco() API.
 */
@Singleton
class BluetoothHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class Status { UNSUPPORTED, DISABLED, DISCONNECTED, CONNECTING, CONNECTED }

    data class State(
        val status: Status = Status.DISCONNECTED,
        val deviceName: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    /** User intent: keep the SCO link alive. */
    @Volatile
    private var scoWanted = false

    private var reconnectAttempts = 0

    init {
        // Listen for SCO state changes to implement auto-reconnect (spec 7.3).
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context, scoReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshInitialState()
    }

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            reconnectAttempts = 0
                            _state.value = _state.value.copy(status = Status.CONNECTED)
                            AppLogger.i(TAG, "Bluetooth SCO connected")
                        }
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                            _state.value = _state.value.copy(status = Status.DISCONNECTED)
                            if (scoWanted) attemptReconnect()
                        }
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> refreshInitialState()
            }
        }
    }

    private fun refreshInitialState() {
        val adapter = adapter
        when {
            adapter == null -> _state.value = State(Status.UNSUPPORTED)
            !adapter.isEnabled -> _state.value = State(Status.DISABLED)
            else -> _state.value = _state.value.copy(status = Status.DISCONNECTED)
        }
    }

    /** True when the platform permission for connecting is granted. */
    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns currently paired devices that expose an audio profile
     * (headset / A2DP). Requires BLUETOOTH_CONNECT on Android 12+.
     */
    fun pairedAudioDevices(): List<BluetoothDevice> {
        val adapter = adapter ?: return emptyList()
        if (!adapter.isEnabled || !hasConnectPermission()) return emptyList()
        return try {
            @Suppress("DEPRECATION")
            adapter.bondedDevices
                ?.filter { device ->
                    try {
                        device.bluetoothClass?.hasService(
                            android.bluetooth.BluetoothClass.Service.AUDIO
                        ) == true ||
                            device.bluetoothClass?.majorDeviceClass ==
                            android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
                    } catch (_: SecurityException) {
                        false
                    }
                }
                ?.sortedBy { it.name }
                .orEmpty()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "pairedAudioDevices failed: ${t.message}")
            emptyList()
        }
    }

    /** Attempts to connect to [device] (or the first paired audio device). */
    fun connect(device: BluetoothDevice? = null) {
        if (!scoWanted) return
        val target = device ?: pairedAudioDevices().firstOrNull() ?: run {
            _state.value = _state.value.copy(status = Status.DISCONNECTED)
            return
        }
        val name = if (hasConnectPermission()) target.name else null
        _state.value = State(Status.CONNECTING, name)
        startSco()
    }

    /** Enables/disables routing speech output to Bluetooth earphones. */
    fun setScoEnabled(enabled: Boolean) {
        scoWanted = enabled
        if (enabled) {
            if (adapter?.isEnabled == true) connect() else {
                _state.value = _state.value.copy(status = Status.DISABLED)
            }
        } else {
            stopSco()
            _state.value = _state.value.copy(status = Status.DISCONNECTED)
        }
    }

    private fun startSco() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val device = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                    AppLogger.i(TAG, "Communication device set to BT SCO")
                } else {
                    AppLogger.w(TAG, "No BT SCO communication device available")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "startSco failed", t)
        }
    }

    private fun stopSco() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "stopSco failed: ${t.message}")
        }
    }

    /** Auto-reconnect with capped retries (spec 7.3). */
    private fun attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            AppLogger.w(TAG, "Giving up on Bluetooth reconnect after $reconnectAttempts attempts")
            return
        }
        reconnectAttempts++
        AppLogger.d(TAG, "Reconnecting SCO (attempt $reconnectAttempts)")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (scoWanted) startSco()
        }, RECONNECT_DELAY_MS)
    }

    private companion object {
        const val TAG = "BluetoothHelper"
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val RECONNECT_DELAY_MS = 2_000L
    }
}
