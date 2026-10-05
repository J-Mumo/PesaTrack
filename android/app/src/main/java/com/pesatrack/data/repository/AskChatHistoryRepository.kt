package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.AskChatMessageDao
import com.pesatrack.data.local.database.entities.AskChatMessageEntity
import com.pesatrack.domain.models.AskChatHistoryEntry
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Named
import javax.inject.Inject
import javax.inject.Singleton

/** Stores the Ask Your Money transcript only in the app's local Room database. */
@Singleton
class AskChatHistoryRepository @Inject constructor(
    private val dao: AskChatMessageDao,
    @Named("aiPro") moshi: Moshi,
) {
    private val assumptionsAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java),
    )

    suspend fun loadHistory(): List<AskChatHistoryEntry> = dao.getAll().mapNotNull { entity ->
        val role = when (entity.role) {
            ROLE_USER -> AskChatHistoryEntry.Role.USER
            ROLE_ASSISTANT -> AskChatHistoryEntry.Role.ASSISTANT
            else -> return@mapNotNull null
        }
        AskChatHistoryEntry(
            id = entity.id,
            role = role,
            text = entity.text,
            isFallback = entity.isFallback,
            fallbackReason = entity.fallbackReason,
            assumptions = decodeAssumptions(entity.assumptionsJson),
        )
    }

    suspend fun saveHistory(messages: List<AskChatHistoryEntry>) {
        dao.replaceAll(messages.map { entry ->
            AskChatMessageEntity(
                id = entry.id,
                role = if (entry.role == AskChatHistoryEntry.Role.USER) ROLE_USER else ROLE_ASSISTANT,
                text = entry.text,
                isFallback = entry.isFallback,
                fallbackReason = entry.fallbackReason,
                assumptionsJson = entry.assumptions.takeIf { it.isNotEmpty() }?.let(::encodeAssumptions),
            )
        })
    }

    suspend fun clearHistory() = dao.deleteAll()

    private fun encodeAssumptions(values: List<String>): String = assumptionsAdapter.toJson(values)

    private fun decodeAssumptions(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { assumptionsAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    private companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
