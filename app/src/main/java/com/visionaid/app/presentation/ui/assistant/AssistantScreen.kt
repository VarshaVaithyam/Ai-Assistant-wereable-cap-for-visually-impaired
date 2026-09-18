@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.visionaid.app.presentation.ui.assistant

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visionaid.app.R
import com.visionaid.app.presentation.theme.AlertRed
import com.visionaid.app.presentation.theme.SuccessGreen
import com.visionaid.app.presentation.theme.VisionAidYellow

/**
 * AI assistant chat screen (spec 11): transcript + big mic button + typed
 * fallback. Answers are spoken automatically via TTS.
 */
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listening by viewModel.listening.collectAsStateWithLifecycle()
    val thinking by viewModel.thinking.collectAsStateWithLifecycle()
    val partial by viewModel.partial.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var typed by remember { mutableStateOf("") }

    // Keep the transcript scrolled to the newest message.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
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
                stringResource(R.string.title_assistant),
                style = MaterialTheme.typography.titleLarge,
                color = VisionAidYellow
            )
        }

        if (!viewModel.isConfigured) {
            Text(
                stringResource(R.string.assistant_no_key),
                color = AlertRed,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // ---------- Transcript ----------
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message)
            }
            if (listening && partial.isNotBlank()) {
                item {
                    Text(
                        partial,
                        color = Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (thinking) {
                item { Text(stringResource(R.string.assistant_thinking), color = VisionAidYellow) }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Mic button ----------
        Surface(
            color = if (listening) AlertRed else SuccessGreen,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .size(96.dp)
                .align(Alignment.CenterHorizontally)
                .semantics {
                    contentDescription = "Microphone. Tap and speak your question."
                },
            onClick = { viewModel.startListening() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        if (listening) stringResource(R.string.assistant_listening) else "",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Typed fallback ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.size(8.dp))
            IconButton(
                onClick = {
                    viewModel.askTyped(typed)
                    typed = ""
                },
                enabled = typed.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(contentColor = VisionAidYellow)
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = stringResource(R.string.assistant_send),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            stringResource(R.string.assistant_hint),
            color = Color(0xFF999999),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MessageBubble(message: com.visionaid.app.data.models.AssistantMessage) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                message.content,
                color = if (isUser) Color.White else Color.Black,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
