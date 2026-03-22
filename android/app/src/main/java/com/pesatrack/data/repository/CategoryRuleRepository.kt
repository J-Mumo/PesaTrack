package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.CategoryRuleDao
import com.pesatrack.data.local.database.entities.CategoryRuleEntity
import com.pesatrack.data.local.database.entities.RuleMatchType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain model for a user-defined categorization rule
 */
data class CategoryRule(
    val id: Long = 0,
    val pattern: String,
    val matchType: RuleMatchType,
    val categoryId: Long,
    val priority: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Repository for user-defined auto-categorization rules.
 */
@Singleton
class CategoryRuleRepository @Inject constructor(
    private val categoryRuleDao: CategoryRuleDao
) {

    /**
     * Get all rules as a Flow
     */
    fun getAllRules(): Flow<List<CategoryRuleEntity>> {
        return categoryRuleDao.getAllRules()
    }

    /**
     * Get all active rules (one-shot, for categorization pipeline)
     */
    suspend fun getActiveRules(): List<CategoryRule> {
        return categoryRuleDao.getActiveRulesSync().map { it.toDomain() }
    }

    /**
     * Add a new rule
     */
    suspend fun addRule(
        pattern: String,
        matchType: RuleMatchType,
        categoryId: Long,
        priority: Int = 0
    ): Long {
        val entity = CategoryRuleEntity(
            pattern = pattern.trim(),
            matchType = matchType.name,
            categoryId = categoryId,
            priority = priority,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        return categoryRuleDao.insert(entity)
    }

    /**
     * Update an existing rule
     */
    suspend fun updateRule(
        id: Long,
        pattern: String,
        matchType: RuleMatchType,
        categoryId: Long,
        priority: Int,
        isActive: Boolean
    ) {
        val existing = categoryRuleDao.getById(id) ?: return
        categoryRuleDao.update(
            existing.copy(
                pattern = pattern.trim(),
                matchType = matchType.name,
                categoryId = categoryId,
                priority = priority,
                isActive = isActive
            )
        )
    }

    /**
     * Delete a rule
     */
    suspend fun deleteRule(id: Long) {
        categoryRuleDao.delete(id)
    }

    /**
     * Get rule count
     */
    suspend fun getRuleCount(): Int {
        return categoryRuleDao.getRuleCount()
    }

    private fun CategoryRuleEntity.toDomain(): CategoryRule {
        return CategoryRule(
            id = id,
            pattern = pattern,
            matchType = try { RuleMatchType.valueOf(matchType) } catch (_: Exception) { RuleMatchType.CONTAINS },
            categoryId = categoryId,
            priority = priority,
            isActive = isActive,
            createdAt = createdAt
        )
    }
}
