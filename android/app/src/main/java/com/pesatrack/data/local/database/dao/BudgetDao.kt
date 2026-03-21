package com.pesatrack.data.local.database.dao

import androidx.room.*
import com.pesatrack.data.local.database.entities.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Budget operations.
 *
 * Supports CRUD for budget entities and queries to find
 * budgets affected by a specific category group (for alert checking).
 */
@Dao
interface BudgetDao {

    /**
     * Insert a new budget
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    /**
     * Update an existing budget
     */
    @Update
    suspend fun update(budget: BudgetEntity)

    /**
     * Delete a budget
     */
    @Delete
    suspend fun delete(budget: BudgetEntity)

    /**
     * Get budget by ID
     */
    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    /**
     * Get all active budgets ordered by categoryGroupId (Total first, then groups).
     * Emits as Flow so the UI reacts to changes.
     */
    @Query("SELECT * FROM budgets WHERE isActive = 1 ORDER BY categoryGroupId IS NOT NULL, categoryGroupId ASC")
    fun getActiveBudgets(): Flow<List<BudgetEntity>>

    /**
     * Get all active budgets as a suspend list (for one-shot queries like alert checking).
     */
    @Query("SELECT * FROM budgets WHERE isActive = 1 ORDER BY categoryGroupId IS NOT NULL, categoryGroupId ASC")
    suspend fun getActiveBudgetsList(): List<BudgetEntity>

    /**
     * Get the "Total Spending" budget for a specific period (if it exists).
     */
    @Query("SELECT * FROM budgets WHERE categoryGroupId IS NULL AND period = :period AND isActive = 1 LIMIT 1")
    suspend fun getTotalBudgetForPeriod(period: String): BudgetEntity?

    /**
     * Get a group-level budget for a specific category group and period.
     */
    @Query("SELECT * FROM budgets WHERE categoryGroupId = :groupId AND period = :period AND isActive = 1 LIMIT 1")
    suspend fun getGroupBudgetForPeriod(groupId: Long, period: String): BudgetEntity?

    /**
     * Get all active budgets that could be affected by an expense in a specific category group.
     * Returns:
     * - The "Total" budget (categoryGroupId IS NULL) — always affected
     * - The group budget matching the expense's group (if exists)
     */
    @Query("""
        SELECT * FROM budgets 
        WHERE isActive = 1 
        AND (categoryGroupId IS NULL OR categoryGroupId = :groupId)
    """)
    suspend fun getBudgetsAffectedByGroup(groupId: Long): List<BudgetEntity>

    /**
     * Check if any active budgets exist (used for prompt logic).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM budgets WHERE isActive = 1)")
    suspend fun hasActiveBudgets(): Boolean

    /**
     * Get count of active budgets
     */
    @Query("SELECT COUNT(*) FROM budgets WHERE isActive = 1")
    suspend fun getActiveBudgetCount(): Int
}
