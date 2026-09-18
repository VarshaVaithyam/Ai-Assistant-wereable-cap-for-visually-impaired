package com.visionaid.app.presentation.ui.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visionaid.app.R
import com.visionaid.app.domain.models.EmergencyPhase
import com.visionaid.app.presentation.theme.AlertRed
import com.visionaid.app.presentation.theme.SuccessGreen
import com.visionaid.app.presentation.theme.VisionAidYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Emergency screen (spec 11.6):
 *  - a giant SOS button covering most of the screen
 *  - tap once -> 10 s countdown with voice; tap again -> cancel
 *  - explicit CANCEL / SEND NOW controls during the countdown
 *  - current location + recent fall history
 */
@Composable
fun EmergencyScreen(
    onBack: () -> Unit,
    viewModel: EmergencyViewModel = hiltViewModel()
) {
    val state by viewModel.emergencyState.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val history by viewModel.fallHistory.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // ---------- Header ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = VisionAidYellow,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                stringResource(R.string.title_emergency),
                style = MaterialTheme.typography.titleLarge,
                color = AlertRed
            )
        }

        when (state.phase) {
            EmergencyPhase.COUNTDOWN -> CountdownView(
                secondsLeft = state.secondsLeft,
                onCancel = { viewModel.cancelAlert() },
                onSendNow = { viewModel.sendNow() }
            )

            EmergencyPhase.SENDING -> StatusMessage(
                stringResource(R.string.sos_sending), VisionAidYellow
            )

            EmergencyPhase.SENT -> StatusMessage(
                stringResource(R.string.sos_sent), SuccessGreen
            )

            EmergencyPhase.CANCELLED -> StatusMessage(
                stringResource(R.string.sos_cancelled), SuccessGreen
            )

            EmergencyPhase.IDLE -> SosButton(
                onPress = { viewModel.startSosCountdown() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Current location ----------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = stringResource(R.string.current_location),
                tint = VisionAidYellow
            )
            Spacer(Modifier.size(8.dp))
            val loc = location
            Text(
                text = loc?.let {
                    "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" +
                        (it.address?.let { a -> "\n$a" } ?: "")
                } ?: stringResource(R.string.location_unknown),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Fall history ----------
        Text(
            stringResource(R.string.fall_history_title),
            style = MaterialTheme.typography.titleMedium,
            color = VisionAidYellow
        )
        if (history.isEmpty()) {
            Text(
                stringResource(R.string.no_fall_history),
                color = Color(0xFF999999),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(history) { event ->
                    FallHistoryRow(event)
                }
            }
        }
    }
}

// ======================================================================
// Views
// ======================================================================

@Composable
private fun SosButton(onPress: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onPress,
        modifier = modifier
            .padding(vertical = 8.dp)
            .semantics {
                contentDescription =
                    "Emergency SOS button. Tap once to start the countdown, then confirm."
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = AlertRed,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.sos_button),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun CountdownView(
    secondsLeft: Int,
    onCancel: () -> Unit,
    onSendNow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.sos_countdown, secondsLeft),
            style = MaterialTheme.typography.headlineLarge,
            color = VisionAidYellow,
            textAlign = TextAlign.Center,
            // Announce countdown changes to TalkBack users automatically.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 96.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen, contentColor = Color.Black
                )
            ) {
                Text(stringResource(R.string.sos_cancel), style = MaterialTheme.typography.headlineMedium)
            }
            Button(
                onClick = onSendNow,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 96.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlertRed, contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.sos_send_now), style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.headlineLarge,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
        )
    }
}

@Composable
private fun FallHistoryRow(event: com.visionaid.app.data.models.FallEvent) {
    val time = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(event.timestamp))
    val status = when {
        event.alertSent -> "Alert sent"
        event.wasCancelled -> "Cancelled"
        else -> "No alert"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(time, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${event.latitude}, ${event.longitude}",
                color = Color(0xFF999999),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(status, color = VisionAidYellow, style = MaterialTheme.typography.titleMedium)
    }
}
