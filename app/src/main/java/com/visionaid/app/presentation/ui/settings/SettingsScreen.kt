package com.visionaid.app.presentation.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visionaid.app.R
import com.visionaid.app.data.models.EmergencyContact
import com.visionaid.app.presentation.theme.AlertRed
import com.visionaid.app.presentation.theme.SuccessGreen
import com.visionaid.app.presentation.theme.VisionAidYellow

/**
 * Settings screen (spec 11.5) — fully voice/TalkBack accessible.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val showDialog by viewModel.editingContact.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = VisionAidYellow,
                    modifier = Modifier.height(32.dp)
                )
            }
            Text(
                stringResource(R.string.title_settings),
                style = MaterialTheme.typography.titleLarge,
                color = VisionAidYellow
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val s = settings ?: return@Column

            // ---------------- ESP32 section ----------------
            SectionTitle(stringResource(R.string.settings_esp32_section))
            SettingsCard {
                var ip by remember(s.esp32IpAddress) { mutableStateOf(s.esp32IpAddress) }
                var port by remember(s.esp32Port) { mutableStateOf(s.esp32Port.toString()) }
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text(stringResource(R.string.setup_esp32_ip_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.setup_esp32_port_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        viewModel.setEsp32Ip(ip)
                        viewModel.setEsp32Port(port)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VisionAidYellow, contentColor = Color.Black
                    )
                ) { Text(stringResource(R.string.settings_save)) }
            }

            // ---------------- Voice section ----------------
            SectionTitle(stringResource(R.string.settings_voice_section))
            SettingsCard {
                Text(
                    stringResource(R.string.setup_voice_speed, "%.1f".format(s.voiceSpeed)),
                    color = Color.White
                )
                Slider(
                    value = s.voiceSpeed,
                    onValueChange = { viewModel.setVoiceSpeed(it) },
                    valueRange = 0.5f..2.0f
                )
                Text(
                    stringResource(R.string.setup_voice_pitch, "%.1f".format(s.voicePitch)),
                    color = Color.White
                )
                Slider(
                    value = s.voicePitch,
                    onValueChange = { viewModel.setVoicePitch(it) },
                    valueRange = 0.5f..2.0f
                )
                ToggleRow(
                    label = stringResource(R.string.settings_bluetooth_audio),
                    checked = s.bluetoothAudioEnabled,
                    onChange = { viewModel.setBluetoothAudio(context, it) }
                )
            }

            // ---------------- Detection section ----------------
            SectionTitle(stringResource(R.string.settings_detection_section))
            SettingsCard {
                Text(
                    stringResource(
                        R.string.settings_confidence,
                        "%.2f".format(s.detectionConfidenceThreshold)
                    ),
                    color = Color.White
                )
                Slider(
                    value = s.detectionConfidenceThreshold,
                    onValueChange = { viewModel.setConfidence(it) },
                    valueRange = 0.2f..0.9f
                )
                Text(
                    stringResource(R.string.settings_cooldown, s.announcementCooldown),
                    color = Color.White
                )
                Slider(
                    value = s.announcementCooldown.toFloat(),
                    onValueChange = { viewModel.setAnnouncementCooldown(it.toInt()) },
                    valueRange = 1000f..6000f
                )
                ToggleRow(
                    stringResource(R.string.settings_ocr_enabled),
                    s.ocrEnabled
                ) { viewModel.setOcrEnabled(it) }
                ToggleRow(
                    stringResource(R.string.settings_vlm_enabled),
                    s.vlmEnabled
                ) { viewModel.setVlmEnabled(it) }
                if (s.vlmEnabled) {
                    ToggleRow("Periodic scene descriptions", s.vlmPeriodicEnabled) {
                        viewModel.setVlmPeriodic(it)
                    }
                }
                ToggleRow(
                    stringResource(R.string.settings_listening_enabled),
                    s.continuousListening
                ) { viewModel.setContinuousListening(it) }
            }

            // ---------------- Fall detection section ----------------
            SectionTitle(stringResource(R.string.settings_fall_section))
            SettingsCard {
                Text(stringResource(R.string.settings_fall_sensitivity), color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "low" to stringResource(R.string.settings_low),
                        "medium" to stringResource(R.string.settings_medium),
                        "high" to stringResource(R.string.settings_high)
                    ).forEach { (key, label) ->
                        Button(
                            onClick = { viewModel.setFallSensitivity(key) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (s.fallDetectionSensitivity == key)
                                    VisionAidYellow else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (s.fallDetectionSensitivity == key)
                                    Color.Black else Color.White
                            )
                        ) { Text(label) }
                    }
                }
                Text(
                    stringResource(R.string.settings_fall_countdown, s.fallAlertCountdown),
                    color = Color.White
                )
                Slider(
                    value = s.fallAlertCountdown.toFloat(),
                    onValueChange = { viewModel.setFallCountdown(it.toInt()) },
                    valueRange = 5f..60f
                )
            }

            // ---------------- Contacts section ----------------
            SectionTitle(stringResource(R.string.settings_contacts_section))
            SettingsCard {
                if (contacts.isEmpty()) {
                    Text(
                        "No emergency contacts yet. Add at least one so alerts can be sent.",
                        color = AlertRed
                    )
                }
                contacts.forEach { contact ->
                    ContactRow(
                        contact = contact,
                        onDelete = { viewModel.deleteContact(contact) }
                    )
                }
                Button(
                    onClick = { viewModel.openContactDialog() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen, contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_add_contact))
                }
            }

            // ---------------- Assistant section ----------------
            SectionTitle(stringResource(R.string.settings_assistant_section))
            SettingsCard {
                var apiKey by remember(s.geminiApiKey) { mutableStateOf(s.geminiApiKey) }
                var showKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.settings_api_key_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation()
                )
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Hide" else "Show")
                }
                Text(
                    stringResource(R.string.settings_api_key_help),
                    color = Color(0xFFCCCCCC),
                    style = MaterialTheme.typography.bodySmall
                )
                var model by remember(s.geminiModel) { mutableStateOf(s.geminiModel) }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.settings_gemini_model_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                var userName by remember(s.userName) { mutableStateOf(s.userName) }
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text(stringResource(R.string.settings_user_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        viewModel.setGeminiApiKey(apiKey)
                        viewModel.setGeminiModel(model)
                        viewModel.setUserName(userName)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VisionAidYellow, contentColor = Color.Black
                    )
                ) { Text(stringResource(R.string.settings_save)) }
            }

            // ---------------- About ----------------
            SectionTitle(stringResource(R.string.settings_about_section))
            SettingsCard {
                Text(
                    stringResource(R.string.settings_about_text),
                    color = Color(0xFFDDDDDD),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (dialogOpen) {
        ContactDialog(viewModel)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        color = VisionAidYellow
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = VisionAidYellow)
        )
    }
}

@Composable
private fun ContactRow(contact: EmergencyContact, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.name + if (contact.isPrimary) " ★" else "",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${contact.phoneNumber} · ${contact.relationship}",
                color = Color(0xFFBBBBBB),
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete ${contact.name}",
                tint = AlertRed
            )
        }
    }
}

@Composable
private fun ContactDialog(viewModel: SettingsViewModel) {
    val name by viewModel.dialogName.collectAsStateWithLifecycle()
    val phone by viewModel.dialogPhone.collectAsStateWithLifecycle()
    val relationship by viewModel.dialogRelationship.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = { viewModel.closeDialog() },
        title = { Text(stringResource(R.string.settings_add_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.setDialogName(it) },
                    label = { Text(stringResource(R.string.setup_contact_name_hint)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { viewModel.setDialogPhone(it) },
                    label = { Text(stringResource(R.string.setup_contact_phone_hint)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { viewModel.setDialogRelationship(it) },
                    label = { Text(stringResource(R.string.setup_contact_relationship_hint)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.saveDialogContact() },
                enabled = viewModel.dialogFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen, contentColor = Color.Black
                )
            ) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeDialog() }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
