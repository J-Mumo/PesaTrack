package com.pesatrack.data.local.database.dao

import androidx.room.*
import com.pesatrack.data.local.database.entities.CategoryRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user-defined category rules.
 */
@Dao
interface CategoryRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CategoryRuleEntity): Long

    @Update
    suspend fun update(rule: CategoryRuleEntity)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM category_rules WHERE id = :id")
    suspend fun getById(id: Long): CategoryRuleEntity?

    /**
     * Get all rules ordered by priority descending (highest priority first)
     */
    @Query("SELECT * FROM category_rules ORDER BY priority DESC, createdAt ASC")
    fun getAllRules(): Flow<List<CategoryRuleEntity>>

    /**
     * Get all active rules ordered by priority descending
     */
    @Query("SELECT * FROM category_rules WHERE isActive = 1 ORDER BY priority DESC, createdAt ASC")
    suspend fun getActiveRulesSync(): List<CategoryRuleEntity>

    /**
     * Get rules for a specific category
     */
    @Query("SELECT * FROM category_rules WHERE categoryId = :categoryId ORDER BY priority DESC")
    fun getRulesForCategory(categoryId: Long): Flow<List<CategoryRuleEntity>>

    /**
     * Get count of all rules
     */
    @Query("SELECT COUNT(*) FROM category_rules")
    suspend fun getRuleCount(): Int

    /**
     * Delete all rules for a specific category
     */
    @Query("DELETE FROM category_rules WHERE categoryId = :categoryId")
    suspend fun deleteRulesForCategory(categoryId: Long)
}
