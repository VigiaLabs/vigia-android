package com.vigia.core.model

data class ConversationTurn(
    val role: Role,
    val text: String,
    val timestampMs: Long,
) {
    enum class Role { USER, ASSISTANT }
}
