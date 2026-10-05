package com.pesatrack.domain.models

/** Persistable, local-only portion of a chat message. */
data class AskChatHistoryEntry(
    val id: Long,
    val role: Role,
    val text: String,
    val isFallback: Boolean = false,
    val fallbackReason: String? = null,
    val assumptions: List<String> = emptyList(),
) {
    enum class Role { USER, ASSISTANT }
}
