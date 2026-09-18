package com.visionaid.app.data.models

import java.util.UUID

/**
 * One message in the assistant conversation (user or AI).
 *
 * @param id       Unique message id.
 * @param content  Message text.
 * @param isUser   True when the message was spoken/typed by the user.
 * @param timestamp Creation time.
 * @param hasImage True when an image was attached to this message (VLM).
 */
data class AssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val hasImage: Boolean = false
)
