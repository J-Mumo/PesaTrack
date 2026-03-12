package com.pesatrack.data.local.database.dao

import androidx.room.*
import com.pesatrack.data.local.database.entities.RecipientCategoryMappingEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for RecipientCategoryMapping operations.
 *
 * Manages learned recipient→category associations used for
 * auto-categorization of incoming SMS and historical imports.
 *
 * Supports multi-category mappings: one recipient can have
 * multiple category associations with usage counts for
 * confidence-based auto-categorization.
 */
@Dao
interface RecipientCategoryMappingDao {

    /**
     * Insert or update a mapping.
     * If the (recipientKey, categoryId) pair already exists, it's replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: RecipientCategoryMappingEntity)

    /**
     * Insert multiple mappings at once (for batch categorization)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mappings: List<RecipientCategoryMappingEntity>)

    /**
     * Get ALL category mappings for a specific recipient (multi-category).
     * Ordered by timesUsed DESC so the most frequently used category comes first.
     */
    @Query("""
        SELECT * FROM recipient_category_mapping 
        WHERE recipientKey = :recipientKey 
        ORDER BY timesUsed DESC
    """)
    suspend fun getMappingsForRecipient(recipientKey: String): List<RecipientCategoryMappingEntity>

    /**
     * Get the single most-used (primary) mapping for a recipient.
     * Returns null if no mapping exists.
     */
    @Query("""
        SELECT * FROM recipient_category_mapping 
        WHERE recipientKey = :recipientKey 
        ORDER BY timesUsed DESC 
        LIMIT 1
    """)
    suspend fun getPrimaryMapping(recipientKey: String): RecipientCategoryMappingEntity?

    /**
     * Get all mappings as a Flow (for settings/debug display)
     */
    @Query("SELECT * FROM recipient_category_mapping ORDER BY recipientKey, timesUsed DESC")
    fun getAllMappings(): Flow<List<RecipientCategoryMappingEntity>>

    /**
     * Get all mappings as a list (suspend, for batch lookups)
     */
    @Query("SELECT * FROM recipient_category_mapping")
    suspend fun getAllMappingsSync(): List<RecipientCategoryMappingEntity>

    /**
     * Get all distinct recipient keys (for quick lookup during import)
     */
    @Query("SELECT DISTINCT recipientKey FROM recipient_category_mapping")
    suspend fun getAllRecipientKeys(): List<String>

    /**
     * Increment the usage counter for a specific (recipientKey, categoryId) pair
     */
    @Query("""
        UPDATE recipient_category_mapping 
        SET timesUsed = timesUsed + 1, lastUsed = :timestamp 
        WHERE recipientKey = :recipientKey AND categoryId = :categoryId
    """)
    suspend fun incrementUsage(
        recipientKey: String,
        categoryId: Long,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Get the total usage count across all categories for a recipient.
     * Used to compute confidence percentages.
     */
    @Query("""
        SELECT COALESCE(SUM(timesUsed), 0) 
        FROM recipient_category_mapping 
        WHERE recipientKey = :recipientKey
    """)
    suspend fun getTotalUsageForRecipient(recipientKey: String): Int

    /**
     * Delete a specific (recipientKey, categoryId) mapping
     */
    @Query("""
        DELETE FROM recipient_category_mapping 
        WHERE recipientKey = :recipientKey AND categoryId = :categoryId
    """)
    suspend fun deleteMapping(recipientKey: String, categoryId: Long)

    /**
     * Delete all mappings for a recipient
     */
    @Query("DELETE FROM recipient_category_mapping WHERE recipientKey = :recipientKey")
    suspend fun deleteAllForRecipient(recipientKey: String)

    /**
     * Delete all mappings
     */
    @Query("DELETE FROM recipient_category_mapping")
    suspend fun deleteAll()

    /**
     * Get the total number of distinct recipients with mappings
     */
    @Query("SELECT COUNT(DISTINCT recipientKey) FROM recipient_category_mapping")
    suspend fun getMappedRecipientCount(): Int

    /**
     * Get the total number of mapping rows
     */
    @Query("SELECT COUNT(*) FROM recipient_category_mapping")
    suspend fun getMappingCount(): Int
}
