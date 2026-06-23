package com.pesatrack.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pesatrack.data.local.database.entities.IncomeTransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for transaction-level income records.
 *
 * Dedupe contract: insert with [OnConflictStrategy.IGNORE] returns -1L
 * when a row with the same `transactionId` already exists.
 */
@Dao
interface IncomeTransactionDao {

    /** Returns -1L if a row with the same `transactionId` already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreOnConflict(income: IncomeTransactionEntity): Long

    @Update
    suspend fun update(income: IncomeTransactionEntity)

    @Query("SELECT * FROM income_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IncomeTransactionEntity?

    @Query("SELECT * FROM income_transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: String): IncomeTransactionEntity?

    @Query(
        "SELECT * FROM income_transactions " +
            "WHERE timestamp >= :startMs AND timestamp < :endMs " +
            "ORDER BY timestamp DESC"
    )
    suspend fun getForRange(startMs: Long, endMs: Long): List<IncomeTransactionEntity>

    @Query(
        "SELECT * FROM income_transactions " +
            "WHERE timestamp >= :startMs AND timestamp < :endMs " +
            "ORDER BY timestamp DESC"
    )
    fun observeForRange(startMs: Long, endMs: Long): Flow<List<IncomeTransactionEntity>>

    /**
     * Sum of non-excluded income transactions in the range.
     * Returns 0.0 when there are no rows.
     */
    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM income_transactions " +
            "WHERE timestamp >= :startMs AND timestamp < :endMs " +
            "AND isExcluded = 0"
    )
    suspend fun sumForRange(startMs: Long, endMs: Long): Double

    /**
     * Sum restricted to specific sources (e.g. only `isInflow = true` sources).
     * Empty source list returns 0.0.
     */
    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM income_transactions " +
            "WHERE timestamp >= :startMs AND timestamp < :endMs " +
            "AND isExcluded = 0 " +
            "AND source IN (:sources)"
    )
    suspend fun sumForRangeBySources(startMs: Long, endMs: Long, sources: List<String>): Double

    @Query("UPDATE income_transactions SET source = :source, isCategorized = 1 WHERE id = :id")
    suspend fun updateSource(id: Long, source: String)

    @Query("UPDATE income_transactions SET isExcluded = :excluded WHERE id = :id")
    suspend fun setExcluded(id: Long, excluded: Boolean)

    @Query("DELETE FROM income_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM income_transactions")
    suspend fun deleteAll()

    // ==================== Export Queries ====================

    /**
     * All income transactions ordered by timestamp descending, for CSV export.
     */
    @Query("SELECT * FROM income_transactions ORDER BY timestamp DESC")
    suspend fun getAllIncomeForExport(): List<IncomeTransactionEntity>
}
