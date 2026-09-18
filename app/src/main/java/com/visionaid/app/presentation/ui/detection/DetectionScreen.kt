// FilterChip is experimental in Material3 1.1.x.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.visionaid.app.presentation.ui.detection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visionaid.app.R
import com.visionaid.app.data.models.DetectionMode
import com.visionaid.app.presentation.theme.AlertRed
import com.visionaid.app.presentation.theme.SuccessGreen
import com.visionaid.app.presentation.theme.VisionAidYellow

// FilterChip is experimental in Material3 1.1.x.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

/**
 * Detection screen (spec 11.4):
 *  - camera feed with bounding-box overlays (for sighted helpers/developers)
 *  - toggle to hide the visual feed (saves battery for blind users)
 *  - scrolling detection log
 *  - pause/resume + mode selector
 */
@Composable
fun DetectionScreen(
    onBack: () -> Unit,
    viewModel: DetectionViewModel = hiltViewModel()
) {
    val frame by viewModel.displayFrame.collectAsStateWithLifecycle()
    val detections by viewModel.detections.collectAsStateWithLifecycle()
    val log by viewModel.detectionLog.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()

    var showFeed by remember { mutableStateOf(true) }

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
                stringResource(R.string.title_detection),
                style = MaterialTheme.typography.titleLarge,
                color = VisionAidYellow
            )
        }

        if (!viewModel.isModelAvailable) {
            Text(
                stringResource(R.string.no_model_warning),
                color = AlertRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // ---------- Mode selector ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip(
                selected = mode == DetectionMode.OBJECT_DETECTION,
                label = stringResource(R.string.detection_mode_objects),
                onSelect = { viewModel.setMode(DetectionMode.OBJECT_DETECTION) }
            )
            ModeChip(
                selected = mode == DetectionMode.OCR,
                label = stringResource(R.string.detection_mode_ocr),
                onSelect = { viewModel.setMode(DetectionMode.OCR) }
            )
            ModeChip(
                selected = mode == DetectionMode.SCENE_DESCRIPTION,
                label = stringResource(R.string.detection_mode_scene),
                onSelect = { viewModel.setMode(DetectionMode.SCENE_DESCRIPTION) }
            )
            ModeChip(
                selected = mode == DetectionMode.ALL,
                label = stringResource(R.string.detection_mode_all),
                onSelect = { viewModel.setMode(DetectionMode.ALL) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Camera feed (toggleable) ----------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.detection_show_feed), color = Color.White)
            Spacer(Modifier.size(8.dp))
            Switch(
                checked = showFeed,
                onCheckedChange = { showFeed = it },
                colors = SwitchDefaults.colors(checkedTrackColor = VisionAidYellow)
            )
        }

        if (showFeed) {
            FramePreview(
                frame = frame?.asImageBitmap(),
                detections = detections,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            )
        }

        // ---------- Pause / resume ----------
        Button(
            onClick = { viewModel.togglePause() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPaused) SuccessGreen else AlertRed,
                contentColor = Color.Black
            )
        ) {
            Icon(
                if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (isPaused) stringResource(R.string.detection_resume)
                else stringResource(R.string.detection_pause),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Detection log ----------
        Text(
            stringResource(R.string.detection_log_title),
            style = MaterialTheme.typography.titleMedium,
            color = VisionAidYellow
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            items(log) { entry ->
                Text(
                    entry,
                    color = Color(0xFFDDDDDD),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeChip(selected: Boolean, label: String, onSelect: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VisionAidYellow,
            selectedLabelColor = Color.Black,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = Color.White
        )
    )
}

/**
 * Camera preview with normalized bounding-box overlays. DANGER objects are
 * red; everything else yellow.
 */
@Composable
private fun FramePreview(
    frame: ImageBitmap?,
    detections: List<com.visionaid.app.data.models.DetectedObject>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .semantics { contentDescription = "Camera feed with detection overlays" }
    ) {
        if (frame != null) {
            androidx.compose.foundation.Image(
                bitmap = frame,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                "No camera feed yet",
                color = Color(0xFF888888),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Overlays: boxes are in normalized [0,1] coordinates.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = size
            detections.forEach { detection ->
                val boxColor = if (detection.isDangerous) AlertRed else VisionAidYellow
                val topLeft = Offset(
                    detection.boundingBox.left * canvasSize.width,
                    detection.boundingBox.top * canvasSize.height
                )
                val boxSize = Size(
                    detection.boundingBox.width() * canvasSize.width,
                    detection.boundingBox.height() * canvasSize.height
                )
                drawRect(
                    color = boxColor,
                    topLeft = topLeft,
                    size = boxSize,
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}
