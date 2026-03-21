package com.pesatrack.data.local.database.dao

import androidx.room.*
import com.pesatrack.data.local.database.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Category operations
 * Supports hierarchical parent-child categories
 */
@Dao
interface CategoryDao {
    
    /**
     * Insert a new category
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long
    
    /**
     * Insert multiple categories
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)
    
    /**
     * Update a category
     */
    @Update
    suspend fun update(category: CategoryEntity)
    
    /**
     * Delete a category (only non-default)
     */
    @Query("DELETE FROM categories WHERE id = :id AND isDefault = 0")
    suspend fun delete(id: Long)
    
    /**
     * Get category by ID
     */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?
    
    /**
     * Get all categories
     */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>
    
    /**
     * Get only group (parent) categories
     */
    @Query("SELECT * FROM categories WHERE isGroup = 1 ORDER BY sortOrder ASC")
    fun getGroupCategories(): Flow<List<CategoryEntity>>

    /**
     * Get only group (parent) categories (suspend, for one-shot queries)
     */
    @Query("SELECT * FROM categories WHERE isGroup = 1 ORDER BY sortOrder ASC")
    suspend fun getGroupCategoriesSync(): List<CategoryEntity>
    
    /**
     * Get child categories for a specific parent
     */
    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder ASC, name ASC")
    fun getChildCategories(parentId: Long): Flow<List<CategoryEntity>>
    
    /**
     * Get child categories for a specific parent (suspend)
     */
    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder ASC, name ASC")
    suspend fun getChildCategoriesSync(parentId: Long): List<CategoryEntity>
    
    /**
     * Get all selectable categories (non-group categories)
     */
    @Query("SELECT * FROM categories WHERE isGroup = 0 ORDER BY parentId ASC, sortOrder ASC, name ASC")
    fun getSelectableCategories(): Flow<List<CategoryEntity>>
    
    /**
     * Get default categories
     */
    @Query("SELECT * FROM categories WHERE isDefault = 1 ORDER BY sortOrder ASC")
    fun getDefaultCategories(): Flow<List<CategoryEntity>>
    
    /**
     * Check if categories table is empty
     */
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
    
    /**
     * Search categories by name
     */
    @Query("SELECT * FROM categories WHERE name LIKE '%' || :query || '%' AND isGroup = 0 ORDER BY name ASC")
    fun searchCategories(query: String): Flow<List<CategoryEntity>>
    
    /**
     * Get parent category for a child
     */
    @Query("SELECT * FROM categories WHERE id = (SELECT parentId FROM categories WHERE id = :childId)")
    suspend fun getParentCategory(childId: Long): CategoryEntity?
    
    /**
     * Clear all categories
     */
    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
