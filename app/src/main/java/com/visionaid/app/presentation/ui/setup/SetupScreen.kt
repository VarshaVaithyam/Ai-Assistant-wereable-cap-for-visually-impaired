package com.visionaid.app.presentation.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visionaid.app.R
import com.visionaid.app.presentation.theme.AlertRed
import com.visionaid.app.presentation.theme.LargeAccessButton
import com.visionaid.app.presentation.theme.SuccessGreen
import com.visionaid.app.presentation.theme.VisionAidYellow

/**
 * First-time setup wizard (spec 11.3). Every step is voice-narrated so a
 * non-sighted user can complete it independently (with TalkBack).
 */
@Composable
fun SetupScreen(
    onFinished: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val step by viewModel.step.collectAsStateWithLifecycle()

    // Voice narration for the current step (spec 11.3: "all steps have
    // voice narration explaining what to do").
    LaunchedEffect(step) {
        val instruction = when (step) {
            SetupViewModel.STEP_PERMISSIONS ->
                "Step 1. Permissions. Grant access to the microphone, location, " +
                    "messages and camera so VisionAid can help you."
            SetupViewModel.STEP_ESP32 ->
                "Step 2. Enter the IP address of the camera on your cap, then test " +
                    "the connection."
            SetupViewModel.STEP_BLUETOOTH ->
                "Step 3. Choose your Bluetooth earphones for voice output."
            SetupViewModel.STEP_CONTACT ->
                "Step 4. Add an emergency contact who will receive alerts."
            SetupViewModel.STEP_VOICE ->
                "Step 5. Adjust the speed and pitch of the voice."
            else ->
                "Step 6. Test run. Verify that all components are working."
        }
        viewModel.narrate(instruction)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.title_setup) + " — " +
                stringResource(R.string.setup_step_of, step + 1, SetupViewModel.STEP_COUNT),
            style = MaterialTheme.typography.titleLarge,
            color = VisionAidYellow
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = (step + 1f) / SetupViewModel.STEP_COUNT,
            modifier = Modifier.fillMaxWidth(),
            color = VisionAidYellow
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (step) {
                SetupViewModel.STEP_PERMISSIONS -> PermissionsStep(viewModel)
                SetupViewModel.STEP_ESP32 -> Esp32Step(viewModel)
                SetupViewModel.STEP_BLUETOOTH -> BluetoothStep(viewModel)
                SetupViewModel.STEP_CONTACT -> ContactStep(viewModel)
                SetupViewModel.STEP_VOICE -> VoiceStep(viewModel)
                SetupViewModel.STEP_TEST -> TestStep(viewModel)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.previousStep() },
                enabled = step > SetupViewModel.STEP_PERMISSIONS,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White
                )
            ) { Text(stringResource(R.string.setup_btn_back), style = MaterialTheme.typography.titleMedium) }

            if (step < SetupViewModel.STEP_TEST) {
                Button(
                    onClick = { viewModel.nextStep() },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VisionAidYellow,
                        contentColor = Color.Black
                    )
                ) { Text(stringResource(R.string.setup_btn_next), style = MaterialTheme.typography.titleMedium) }
            } else {
                Button(
                    onClick = { viewModel.finishSetup(onFinished) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        contentColor = Color.Black
                    )
                ) { Text(stringResource(R.string.setup_btn_finish), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

// ======================================================================
// Step 1: permissions
// ======================================================================

@Composable
private fun PermissionsStep(viewModel: SetupViewModel) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    var useBluetooth by remember { mutableStateOf(true) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LargeAccessButton(
            text = "Grant permissions",
            onClick = { launcher.launch(permissions.map { it.permission }.toTypedArray()) },
            modifier = Modifier.fillMaxWidth()
        )
        permissions.forEach { row ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.label, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            row.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCCCCCC)
                        )
                    }
                    Checkbox(
                        checked = row.granted,
                        onCheckedChange = null,
                        tint = if (row.granted) SuccessGreen else AlertRed
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Use Bluetooth earphones for voice output?",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = useBluetooth,
                onCheckedChange = {
                    useBluetooth = it
                    viewModel.enableBluetoothOutput(it)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = VisionAidYellow)
            )
            Spacer(Modifier.height(8.dp))
            Text(" Bluetooth earphones", color = Color.White)
        }
    }
}

// ======================================================================
// Step 2: ESP32-CAM
// ======================================================================

@Composable
private fun Esp32Step(viewModel: SetupViewModel) {
    val ip by viewModel.esp32Ip.collectAsStateWithLifecycle()
    val port by viewModel.esp32Port.collectAsStateWithLifecycle()
    val testResult by viewModel.esp32TestResult.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Enter the address of the camera on your cap. " +
                "The default is 192.168.4.1 when the camera creates its own Wi-Fi network.",
            color = Color.White
        )
        OutlinedTextField(
            value = ip,
            onValueChange = { viewModel.setEsp32Ip(it) },
            label = { Text(stringResource(R.string.setup_esp32_ip_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = port,
            onValueChange = { viewModel.setEsp32Port(it) },
            label = { Text(stringResource(R.string.setup_esp32_port_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        LargeAccessButton(
            text = stringResource(R.string.setup_esp32_test),
            onClick = { viewModel.testEsp32Connection() },
            modifier = Modifier.fillMaxWidth()
        )
        when (testResult) {
            true -> Text(stringResource(R.string.setup_esp32_ok), color = SuccessGreen)
            false -> Text(stringResource(R.string.setup_esp32_fail), color = AlertRed)
            null -> {}
        }
    }
}

// ======================================================================
// Step 3: Bluetooth
// ======================================================================

@Composable
private fun BluetoothStep(viewModel: SetupViewModel) {
    val devices by viewModel.pairedDevices.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshBluetoothDevices() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Make sure your earphones are paired in Android Bluetooth settings, " +
                "then press the button to detect them.",
            color = Color.White
        )
        LargeAccessButton(
            text = "Search paired devices",
            onClick = { viewModel.refreshBluetoothDevices() },
            modifier = Modifier.fillMaxWidth()
        )
        if (devices.isEmpty()) {
            Text(stringResource(R.string.setup_bluetooth_none), color = AlertRed)
        } else {
            Text(stringResource(R.string.setup_bluetooth_paired), color = VisionAidYellow)
            devices.forEach { name ->
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )) {
                    Text(
                        name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Text(
                "Voice output is routed automatically when connected.",
                color = Color(0xFFCCCCCC)
            )
        }
    }
}

// ======================================================================
// Step 4: emergency contact
// ======================================================================

@Composable
private fun ContactStep(viewModel: SetupViewModel) {
    val name by viewModel.contactName.collectAsStateWithLifecycle()
    val phone by viewModel.contactPhone.collectAsStateWithLifecycle()
    val relationship by viewModel.contactRelationship.collectAsStateWithLifecycle()
    val saved by viewModel.contactSaved.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "This person will receive an SMS with your location if a fall is detected.",
            color = Color.White
        )
        OutlinedTextField(
            value = name,
            onValueChange = { viewModel.setContactName(it) },
            label = { Text(stringResource(R.string.setup_contact_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { viewModel.setContactPhone(it) },
            label = { Text(stringResource(R.string.setup_contact_phone_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = relationship,
            onValueChange = { viewModel.setContactRelationship(it) },
            label = { Text(stringResource(R.string.setup_contact_relationship_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        LargeAccessButton(
            text = stringResource(R.string.setup_contact_save),
            onClick = { viewModel.saveContact() },
            enabled = viewModel.contactFormValid,
            modifier = Modifier.fillMaxWidth(),
            containerColor = SuccessGreen
        )
        if (saved) {
            Text("Contact saved.", color = SuccessGreen)
        }
    }
}

// ======================================================================
// Step 5: voice preferences
// ======================================================================

@Composable
private fun VoiceStep(viewModel: SetupViewModel) {
    val speed by viewModel.voiceSpeed.collectAsStateWithLifecycle()
    val pitch by viewModel.voicePitch.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.setup_voice_speed, "%.1f".format(speed)), color = Color.White)
        Slider(
            value = speed,
            onValueChange = { viewModel.setVoiceSpeed(it) },
            valueRange = 0.5f..2.0f
        )
        Text(stringResource(R.string.setup_voice_pitch, "%.1f".format(pitch)), color = Color.White)
        Slider(
            value = pitch,
            onValueChange = { viewModel.setVoicePitch(it) },
            valueRange = 0.5f..2.0f
        )
        LargeAccessButton(
            text = stringResource(R.string.setup_voice_test),
            onClick = { viewModel.testVoice() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ======================================================================
// Step 6: test run
// ======================================================================

@Composable
private fun TestStep(viewModel: SetupViewModel) {
    val items by viewModel.testItems.collectAsStateWithLifecycle()
    val running by viewModel.testsRunning.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Press the button to verify all components are working.",
            color = Color.White,
            textAlign = TextAlign.Start
        )
        LargeAccessButton(
            text = if (running) stringResource(R.string.setup_test_running)
            else stringResource(R.string.setup_run_tests),
            onClick = { viewModel.runTests() },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        )
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (label, color) = when (item.status) {
                    SetupViewModel.TestStatus.PENDING -> stringResource(R.string.pending) to Color(0xFF888888)
                    SetupViewModel.TestStatus.RUNNING -> "…" to VisionAidYellow
                    SetupViewModel.TestStatus.PASSED -> stringResource(R.string.passed) to SuccessGreen
                    SetupViewModel.TestStatus.FAILED -> stringResource(R.string.failed) to AlertRed
                }
                Text(
                    item.label,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(label, color = color, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
