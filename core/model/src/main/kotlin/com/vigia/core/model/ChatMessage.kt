package com.vigia.core.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val body: String,
    val sources: List<MessageSource> = emptyList(),
    val reasoningSteps: List<String> = emptyList(),
    val latencyMs: Long = 0L,
    val attachmentUri: String? = null,
    val status: MessageStatus = MessageStatus.Complete,
    val createdAt: Long,
)

enum class MessageRole { USER, ASSISTANT }

/** Partial: stream severed mid-transit — body contains whatever tokens arrived. */
enum class MessageStatus { Complete, Partial }

data class MessageSource(
    val id: String,
    val url: String,
    val label: String,
    val trustLevel: String = "",
)
