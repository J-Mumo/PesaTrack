package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.AskChatMessageDao
import com.pesatrack.data.local.database.entities.AskChatMessageEntity
import com.pesatrack.domain.models.AskChatHistoryEntry
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AskChatHistoryRepositoryTest {
    private val dao = InMemoryAskChatMessageDao()
    private val repository = AskChatHistoryRepository(dao, Moshi.Builder().build())

    @Test
    fun saveAndLoad_roundTripsMessagesAndAssumptions() = runBlocking {
        val expected = listOf(
            AskChatHistoryEntry(
                id = 1,
                role = AskChatHistoryEntry.Role.USER,
                text = "How much did I spend?",
            ),
            AskChatHistoryEntry(
                id = 2,
                role = AskChatHistoryEntry.Role.ASSISTANT,
                text = "You spent KES 4,200.\n\n## Context",
                assumptions = listOf("Based on imported transactions", "KES amounts are rounded"),
            ),
            AskChatHistoryEntry(
                id = 3,
                role = AskChatHistoryEntry.Role.ASSISTANT,
                text = "I couldn't answer that right now.",
                isFallback = true,
                fallbackReason = "network",
            ),
        )

        repository.saveHistory(expected)

        assertEquals(expected, repository.loadHistory())
    }

    @Test
    fun clearHistory_removesSavedTranscript() = runBlocking {
        repository.saveHistory(
            listOf(AskChatHistoryEntry(1, AskChatHistoryEntry.Role.USER, "Question")),
        )

        repository.clearHistory()

        assertEquals(emptyList<AskChatHistoryEntry>(), repository.loadHistory())
    }

    private class InMemoryAskChatMessageDao : AskChatMessageDao() {
        private var rows = emptyList<AskChatMessageEntity>()

        override suspend fun getAll(): List<AskChatMessageEntity> = rows.sortedBy { it.id }

        override suspend fun deleteAll() {
            rows = emptyList()
        }

        override suspend fun insertAll(messages: List<AskChatMessageEntity>) {
            rows = rows + messages
        }
    }
}
