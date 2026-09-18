package com.visionaid.app.presentation.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visionaid.app.R
import com.visionaid.app.data.models.ConnectionStatus
import com.visionaid.app.presentation.theme.AlertRed
import com.visionaid.app.presentation.theme.LargeAccessButton
import com.visionaid.app.presentation.theme.SuccessGreen
import com.visionaid.app.presentation.theme.VisionAidYellow

/**
 * Home screen (spec 11.2): a large status indicator and four large buttons
 * in a 2x2 grid, plus a bottom bar with settings / connection / battery.
 * Everything is reachable with TalkBack and voice commands.
 */
@Composable
fun MainScreen(
    onOpenDetection: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val isActive by viewModel.isPipelineActive.collectAsStateWithLifecycle()
    val battery by viewModel.batteryPercent.collectAsStateWithLifecycle()

    // Voice greeting once per session + battery read on entry.
    LaunchedEffect(Unit) {
        viewModel.greetOnLaunch()
        viewModel.refreshBattery(context)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------- Status indicator ----------
            StatusHeader(isActive = isActive, connectionStatus = connectionStatus)

            Spacer(Modifier.height(24.dp))

            // ---------- 2x2 action grid ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LargeAccessButton(
                        text = if (isActive) stringResource(R.string.btn_stop_detection)
                        else stringResource(R.string.btn_start_detection),
                        onClick = { viewModel.toggleDetection(context) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        containerColor = if (isActive) AlertRed else VisionAidYellow,
                        contentDescription = stringResource(R.string.cd_start_detection)
                    )
                    LargeAccessButton(
                        text = stringResource(R.string.btn_ai_assistant),
                        onClick = onOpenAssistant,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentDescription = stringResource(R.string.cd_ai_assistant)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LargeAccessButton(
                        text = stringResource(R.string.btn_read_text),
                        onClick = { viewModel.readTextNow() },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        containerColor = SuccessGreen,
                        contentDescription = stringResource(R.string.cd_read_text)
                    )
                    LargeAccessButton(
                        text = stringResource(R.string.btn_emergency),
                        onClick = onOpenEmergency,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        containerColor = AlertRed,
                        contentDescription = stringResource(R.string.cd_emergency)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Bottom bar ----------
            BottomBar(
                connectionStatus = connectionStatus,
                batteryPercent = battery,
                onOpenSettings = onOpenSettings,
                onOpenDetection = onOpenDetection
            )
        }
    }
}

@Composable
private fun StatusHeader(isActive: Boolean, connectionStatus: ConnectionStatus) {
    val statusText = if (isActive) stringResource(R.string.status_active)
    else stringResource(R.string.status_inactive)
    val statusColor = if (isActive) SuccessGreen else Color(0xFF888888)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = statusColor,
            shape = CircleShape,
            modifier = Modifier.size(28.dp)
        ) {}
        Spacer(Modifier.size(16.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = statusText }
        )
    }
}

@Composable
private fun BottomBar(
    connectionStatus: ConnectionStatus,
    batteryPercent: Int?,
    onOpenSettings: () -> Unit,
    onOpenDetection: () -> Unit
) {
    val connectionLabel = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> "ESP32"
        ConnectionStatus.CONNECTING -> "connecting"
        ConnectionStatus.ERROR -> "error"
        ConnectionStatus.DISCONNECTED -> "offline"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.connection_status, connectionLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Connection status $connectionLabel" }
        )
        batteryPercent?.let { level ->
            Icon(
                imageVector = Icons.Filled.BatteryStd,
                contentDescription = stringResource(R.string.battery_level, level),
                tint = VisionAidYellow,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                text = "$level%",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = onOpenDetection,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = VisionAidYellow
            )
        ) {
            Icon(
                Icons.Filled.Visibility,
                contentDescription = stringResource(R.string.title_detection),
                modifier = Modifier.size(32.dp)
            )
        }
        IconButton(
            onClick = onOpenSettings,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = VisionAidYellow
            )
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
