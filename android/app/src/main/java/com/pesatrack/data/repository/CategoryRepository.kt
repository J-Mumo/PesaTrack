package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.entities.CategoryEntity
import com.pesatrack.data.local.database.entities.DefaultCategories
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.CategoryGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for category operations
 * Supports hierarchical parent-child categories
 */
@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    
    /**
     * Get all categories (flat list)
     */
    fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get only selectable categories (non-group)
     */
    fun getSelectableCategories(): Flow<List<Category>> {
        return categoryDao.getSelectableCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get category groups with their children
     */
    fun getCategoryGroups(): Flow<List<CategoryGroup>> {
        return categoryDao.getGroupCategories().map { groups ->
            groups.map { group ->
                val children = categoryDao.getChildCategoriesSync(group.id)
                CategoryGroup(
                    parent = group.toDomain(),
                    children = children.map { it.toDomain() }
                )
            }
        }
    }
    
    /**
     * Get parent/group categories only
     */
    fun getGroups(): Flow<List<Category>> {
        return categoryDao.getGroupCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get children of a specific parent
     */
    fun getChildCategories(parentId: Long): Flow<List<Category>> {
        return categoryDao.getChildCategories(parentId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get children of a specific parent (suspend, for one-shot queries)
     */
    suspend fun getSubCategoriesSync(parentId: Long): List<Category> {
        return categoryDao.getChildCategoriesSync(parentId).map { it.toDomain() }
    }
    
    /**
     * Get category by ID
     */
    suspend fun getCategoryById(id: Long): Category? {
        return categoryDao.getById(id)?.toDomain()
    }
    
    /**
     * Get parent category for a child
     */
    suspend fun getParentCategory(childId: Long): Category? {
        return categoryDao.getParentCategory(childId)?.toDomain()
    }
    
    /**
     * Search categories by name
     */
    fun searchCategories(query: String): Flow<List<Category>> {
        return categoryDao.searchCategories(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Initialize default categories if database is empty
     */
    suspend fun initializeDefaultCategories() {
        val count = categoryDao.getCategoryCount()
        if (count == 0) {
            categoryDao.insertAll(DefaultCategories.categories)
        }
    }
    
    /**
     * Reset to default categories (clears custom and re-inserts defaults)
     */
    suspend fun resetToDefaults() {
        categoryDao.deleteAll()
        categoryDao.insertAll(DefaultCategories.categories)
    }
    
    /**
     * Add custom sub-category under a parent group
     */
    suspend fun addCategory(
        name: String,
        icon: String,
        color: String,
        parentId: Long? = null
    ): Long {
        val nextSort = if (parentId != null) {
            categoryDao.getMaxSortOrderForParent(parentId) + 1
        } else 0
        val category = CategoryEntity(
            name = name,
            icon = icon,
            color = color,
            parentId = parentId,
            isGroup = false,
            isDefault = false,
            sortOrder = nextSort
        )
        return categoryDao.insert(category)
    }
    
    /**
     * Add custom category group
     */
    suspend fun addCategoryGroup(
        name: String,
        icon: String,
        color: String
    ): Long {
        val nextSort = categoryDao.getMaxGroupSortOrder() + 1
        val group = CategoryEntity(
            name = name,
            icon = icon,
            color = color,
            parentId = null,
            isGroup = true,
            isDefault = false,
            sortOrder = nextSort
        )
        return categoryDao.insert(group)
    }

    /**
     * Update an existing category (name, icon, color).
     * Preserves parentId, isGroup, isDefault, and sortOrder.
     */
    suspend fun updateCategory(
        id: Long,
        name: String,
        icon: String,
        color: String
    ) {
        val entity = categoryDao.getById(id) ?: return
        categoryDao.update(entity.copy(name = name, icon = icon, color = color))
    }

    /**
     * Delete a sub-category. Returns false if category is in use by expenses.
     */
    suspend fun deleteCategory(id: Long): Boolean {
        val count = categoryDao.getExpenseCountForCategory(id)
        if (count > 0) return false
        categoryDao.delete(id)
        return true
    }

    /**
     * Delete a custom group and all its non-default children.
     * Returns false if any sub-category under the group has expenses.
     */
    suspend fun deleteGroup(groupId: Long): Boolean {
        val count = categoryDao.getExpenseCountForGroup(groupId)
        if (count > 0) return false
        categoryDao.deleteGroupAndChildren(groupId)
        return true
    }

    /**
     * Get number of expenses assigned to a specific category
     */
    suspend fun getExpenseCountForCategory(categoryId: Long): Int {
        return categoryDao.getExpenseCountForCategory(categoryId)
    }

    /**
     * Get number of expenses assigned to any sub-category in a group
     */
    suspend fun getExpenseCountForGroup(groupId: Long): Int {
        return categoryDao.getExpenseCountForGroup(groupId)
    }
    
    // Extension function for mapping
    
    private fun CategoryEntity.toDomain(): Category {
        return Category(
            id = id,
            name = name,
            icon = icon,
            color = color,
            parentId = parentId,
            isGroup = isGroup,
            isDefault = isDefault,
            sortOrder = sortOrder
        )
    }
}
