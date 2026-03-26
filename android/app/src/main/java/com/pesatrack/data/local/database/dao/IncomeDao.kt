package com.pesatrack.data.local.database.dao

import androidx.room.*
import com.pesatrack.data.local.database.entities.IncomeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for monthly income records.
 */
@Dao
interface IncomeDao {

    /**
     * Insert or replace income for a given month.
     * The unique index on yearMonth ensures only one row per month.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(income: IncomeEntity): Long

    /**
     * Get income for a specific month (e.g. "2026-03").
     * Returns null if no income has been set for that month.
     */
    @Query("SELECT * FROM income WHERE yearMonth = :yearMonth LIMIT 1")
    suspend fun getByYearMonth(yearMonth: String): IncomeEntity?

    /**
     * Get income as a Flow for reactive UI updates.
     */
    @Query("SELECT * FROM income WHERE yearMonth = :yearMonth LIMIT 1")
    fun observeByYearMonth(yearMonth: String): Flow<IncomeEntity?>

    /**
     * Get all income records ordered by yearMonth descending (most recent first).
     */
    @Query("SELECT * FROM income ORDER BY yearMonth DESC")
    suspend fun getAll(): List<IncomeEntity>

    /**
     * Delete income for a specific month.
     */
    @Query("DELETE FROM income WHERE yearMonth = :yearMonth")
    suspend fun deleteByYearMonth(yearMonth: String)
}
