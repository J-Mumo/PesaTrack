package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.BudgetDao
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.entities.BudgetEntity
import com.pesatrack.domain.models.Budget
import com.pesatrack.domain.models.BudgetAlert
import com.pesatrack.domain.models.BudgetPeriod
import com.pesatrack.domain.models.BudgetProgress
import com.pesatrack.domain.models.BudgetStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for budget data operations.
 *
 * Handles CRUD, period date range computation, spending aggregation,
 * and budget progress/alert calculation.
 *
 * Supports three budget levels:
 * - Total Spending (categoryId = null)
 * - Group-level (categoryId = group ID, isGroupBudget = true)
 * - Sub-category-level (categoryId = sub-category ID, isGroupBudget = false)
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) {

    // ==================== CRUD ====================

    /**
     * Save a new budget. Returns the inserted row ID.
     */
    suspend fun saveBudget(budget: Budget): Long {
        return budgetDao.insert(budget.toEntity())
    }

    /**
     * Update an existing budget.
     */
    suspend fun updateBudget(budget: Budget) {
        budgetDao.update(budget.toEntity())
    }

    /**
     * Delete a budget.
     */
    suspend fun deleteBudget(budget: Budget) {
        budgetDao.delete(budget.toEntity())
    }

    /**
     * Get all active budgets as a Flow (reacts to changes).
     * Each budget is enriched with category name/color.
     */
    fun getActiveBudgets(): Flow<List<Budget>> {
        return budgetDao.getActiveBudgets().map { entities ->
            val categoryMap = buildCategoryMap()
            entities.map { it.toDomain(categoryMap) }
        }
    }

    /**
     * Get a budget by ID.
     */
    suspend fun getBudgetById(id: Long): Budget? {
        val entity = budgetDao.getById(id) ?: return null
        val categoryMap = buildCategoryMap()
        return entity.toDomain(categoryMap)
    }

    /**
     * Check if any active budgets exist.
     */
    suspend fun hasActiveBudgets(): Boolean {
        return budgetDao.hasActiveBudgets()
    }

    // ==================== Period Range Helpers ====================

    /**
     * Get the start and end timestamps (millis) for the current period.
     *
     * - WEEKLY: Monday 00:00:00 → next Monday 00:00:00 (ISO week)
     * - MONTHLY: 1st of month 00:00:00 → 1st of next month 00:00:00
     * - YEARLY: Jan 1 00:00:00 → Jan 1 next year 00:00:00
     */
    fun getCurrentPeriodRange(period: BudgetPeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        return when (period) {
            BudgetPeriod.WEEKLY -> {
                // Set to Monday of current week
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                // If today is Sunday and firstDayOfWeek is Sunday, we may be in the wrong week
                // Adjust: if the computed Monday is in the future, go back one week
                if (calendar.timeInMillis > System.currentTimeMillis()) {
                    calendar.add(Calendar.WEEK_OF_YEAR, -1)
                }
                val start = calendar.timeInMillis
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            BudgetPeriod.MONTHLY -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.MONTH, 1)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            BudgetPeriod.YEARLY -> {
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.YEAR, 1)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
        }
    }

    // ==================== Spending Queries ====================

    /**
     * Get actual spending for a budget in its current period.
     *
     * Three paths:
     * 1. Total spending (categoryId = null) → sum all non-excluded expenses
     * 2. Group-level (isGroupBudget = true) → sum all sub-categories in the group
     * 3. Sub-category-level (isGroupBudget = false) → sum only that sub-category
     */
    suspend fun getSpendingForBudget(budget: Budget): Double {
        val (start, end) = getCurrentPeriodRange(budget.period)
        return when {
            budget.categoryId == null -> {
                // Total spending budget
                expenseDao.getTotalSpendingInRange(start, end)
            }
            budget.isGroupBudget -> {
                // Group-level budget — sum all sub-categories in the group
                expenseDao.getGroupSpendingInRange(budget.categoryId, start, end)
            }
            else -> {
                // Sub-category-level budget — sum only that specific sub-category
                expenseDao.getSubcategorySpendingInRange(budget.categoryId, start, end)
            }
        }
    }

    // ==================== Progress Computation ====================

    /**
     * Compute BudgetProgress for all active budgets.
     * Used by the UI to render progress bars.
     */
    suspend fun getBudgetProgressList(): List<BudgetProgress> {
        val categoryMap = buildCategoryMap()
        val entities = budgetDao.getActiveBudgetsList()
        return entities.map { entity ->
            val budget = entity.toDomain(categoryMap)
            val spent = getSpendingForBudget(budget)
            val percentage = if (budget.amount > 0) (spent / budget.amount) * 100.0 else 0.0
            BudgetProgress(
                budget = budget,
                spent = spent,
                percentage = percentage,
                status = BudgetStatus.fromPercentage(percentage)
            )
        }
    }

    // ==================== Alert Checking ====================

    /**
     * Check which budgets have crossed alert thresholds (80% or 100%)
     * after an expense was saved.
     *
     * @param expenseCategoryId The sub-category ID of the saved expense.
     *                          Pass null if the expense is uncategorized (no alerts will fire).
     * @return List of alerts for budgets that are at or above a threshold.
     */
    suspend fun checkBudgetAlerts(expenseCategoryId: Long?): List<BudgetAlert> {
        if (expenseCategoryId == null) return emptyList()

        // Resolve the group ID from the sub-category
        val groupId = getGroupIdForCategory(expenseCategoryId) ?: return emptyList()

        val categoryMap = buildCategoryMap()
        val affectedEntities = budgetDao.getBudgetsAffectedByCategory(groupId, expenseCategoryId)
        val alerts = mutableListOf<BudgetAlert>()

        for (entity in affectedEntities) {
            val budget = entity.toDomain(categoryMap)
            val spent = getSpendingForBudget(budget)
            val percentage = if (budget.amount > 0) (spent / budget.amount) * 100.0 else 0.0

            when {
                percentage >= 100.0 -> alerts.add(
                    BudgetAlert(budget = budget, spent = spent, percentage = percentage, threshold = 100)
                )
                percentage >= 80.0 -> alerts.add(
                    BudgetAlert(budget = budget, spent = spent, percentage = percentage, threshold = 80)
                )
            }
        }

        return alerts
    }

    /**
     * Resolve the category group ID for a given sub-category ID.
     * Returns the parentId (group) if the category has a parent, or the ID itself if it's a group.
     * Returns null if the category doesn't exist.
     */
    suspend fun getGroupIdForCategory(categoryId: Long): Long? {
        val category = categoryDao.getById(categoryId) ?: return null
        return if (category.isGroup) category.id else category.parentId
    }

    /**
     * Get count of categorized expenses (for budget prompt trigger).
     */
    suspend fun getCategorizedExpenseCount(): Int {
        return expenseDao.getCategorizedExpenseCount()
    }

    /**
     * Get top spending category group from last month (for smart prompt).
     * Returns Triple(groupId, groupName, totalSpent) or null if no data.
     */
    suspend fun getTopSpendingGroupLastMonth(): Triple<Long, String, Double>? {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMs = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val endMs = cal.timeInMillis

        val categoryTotals = expenseDao.getCategoryTotalsForMonth(startMs, endMs)
        if (categoryTotals.isEmpty()) return null

        // Aggregate by parent group
        val groupTotals = mutableMapOf<Long, Double>()
        val categoryMap = buildCategoryMap()

        for (ct in categoryTotals) {
            val catId = ct.categoryId ?: continue
            val parentId = ct.parentId ?: catId // If it's a group itself
            groupTotals[parentId] = (groupTotals[parentId] ?: 0.0) + ct.total
        }

        val topEntry = groupTotals.maxByOrNull { it.value } ?: return null
        val groupName = categoryMap[topEntry.key]?.first ?: "Unknown"
        return Triple(topEntry.key, groupName, topEntry.value)
    }

    // ==================== Mapping Helpers ====================

    /**
     * Build a map of category ID → (name, color) for enriching budget domain objects.
     * Includes both groups and sub-categories.
     */
    private suspend fun buildCategoryMap(): Map<Long, Pair<String, String?>> {
        val allCategories = categoryDao.getGroupCategoriesSync() +
            categoryDao.getGroupCategoriesSync().flatMap { group ->
                categoryDao.getChildCategoriesSync(group.id)
            }
        return allCategories.associate { it.id to Pair(it.name, it.color) }
    }

    private fun BudgetEntity.toDomain(categoryMap: Map<Long, Pair<String, String?>>): Budget {
        val categoryInfo = categoryId?.let { categoryMap[it] }
        return Budget(
            id = id,
            categoryId = categoryId,
            categoryName = categoryInfo?.first,
            categoryColor = categoryInfo?.second,
            isGroupBudget = isGroupBudget,
            amount = amount,
            period = BudgetPeriod.fromString(period),
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Budget.toEntity(): BudgetEntity {
        return BudgetEntity(
            id = id,
            categoryId = categoryId,
            isGroupBudget = isGroupBudget,
            amount = amount,
            period = period.name,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
