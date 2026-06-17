package com.pesatrack.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesatrack.data.local.database.entities.MonthlyIncomeBudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the manual monthly income budget / override.
 *
 * One row per month, keyed on yearMonth (unique index).
 * Backing table is still "income".
 */
@Dao
interface MonthlyIncomeBudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(income: MonthlyIncomeBudgetEntity): Long

    @Query("DELETE FROM income")
    suspend fun deleteAll()

    @Query("SELECT * FROM income WHERE yearMonth = :yearMonth LIMIT 1")
    suspend fun getByYearMonth(yearMonth: String): MonthlyIncomeBudgetEntity?

    @Query("SELECT * FROM income WHERE yearMonth = :yearMonth LIMIT 1")
    fun observeByYearMonth(yearMonth: String): Flow<MonthlyIncomeBudgetEntity?>

    @Query("SELECT * FROM income ORDER BY yearMonth DESC")
    suspend fun getAll(): List<MonthlyIncomeBudgetEntity>

    @Query("DELETE FROM income WHERE yearMonth = :yearMonth")
    suspend fun deleteByYearMonth(yearMonth: String)
}
