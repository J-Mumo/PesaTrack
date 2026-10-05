package com.pesatrack.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pesatrack.data.local.database.entities.AskChatMessageEntity

/** Room access for the on-device Ask Your Money transcript. */
@Dao
abstract class AskChatMessageDao {
    @Query("SELECT * FROM ask_chat_messages ORDER BY id ASC")
    abstract suspend fun getAll(): List<AskChatMessageEntity>

    @Query("DELETE FROM ask_chat_messages")
    abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(messages: List<AskChatMessageEntity>)

    /** Replace the transcript atomically so a process interruption cannot leave it half-written. */
    @Transaction
    open suspend fun replaceAll(messages: List<AskChatMessageEntity>) {
        deleteAll()
        if (messages.isNotEmpty()) insertAll(messages)
    }
}
