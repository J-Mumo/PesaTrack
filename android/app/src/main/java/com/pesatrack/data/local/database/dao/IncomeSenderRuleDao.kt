package com.pesatrack.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesatrack.data.local.database.entities.IncomeSenderRuleEntity

/**
 * DAO for [IncomeSenderRuleEntity] — see `plans/income-tracking-plan.md` §5.5.
 */
@Dao
interface IncomeSenderRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: IncomeSenderRuleEntity)

    @Query("SELECT * FROM income_sender_rules WHERE sender = :sender LIMIT 1")
    suspend fun getBySender(sender: String): IncomeSenderRuleEntity?

    @Query("DELETE FROM income_sender_rules WHERE sender = :sender")
    suspend fun deleteBySender(sender: String)
}
