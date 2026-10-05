package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/** One locally persisted message in the single Ask Your Money conversation. */
@Entity(tableName = "ask_chat_messages")
data class AskChatMessageEntity(
    /** Stable UI ordering/id, allocated monotonically by the chat ViewModel. */
    @PrimaryKey
    val id: Long,
    /** Either `user` or `assistant`. */
    val role: String,
    val text: String,
    @ColumnInfo(defaultValue = "0")
    val isFallback: Boolean = false,
    val fallbackReason: String? = null,
    /** JSON array of assumption strings, or null when there are none. */
    val assumptionsJson: String? = null,
)
