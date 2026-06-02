package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.BudgetDao
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.IncomeDao
import com.pesatrack.data.local.database.entities.BudgetEntity
import com.pesatrack.data.local.database.entities.IncomeEntity
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.domain.models.Budget
import com.pesatrack.domain.models.BudgetAlert
import com.pesatrack.domain.models.BudgetPeriod
import com.pesatrack.domain.models.BudgetProgress
import com.pesatrack.domain.models.BudgetStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for budget data operations.
 *
 * Handles CRUD, period date range computation, spending aggregation,
 * and budget progress/alert calculation.
 *
 * Supports budget levels:
 * - Group-level (categoryId = group ID, isGroupBudget = true)
 * - Sub-category-level (categoryId = sub-category ID, isGroupBudget = false)
 *
 * Supports period types:
 * - WEEKLY — calendar-aligned week (Mon–Sun)
 * - MONTHLY — offset by user's "month start day" preference (default 1)
 * - YEARLY — calendar-aligned year
 * - CUSTOM — legacy, kept for DB compatibility but hidden from UI
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val incomeDao: IncomeDao,
    private val appPreferences: AppPreferences
) {

    /**
     * Cached month start day, updated from AppPreferences.
     * Default 1 = standard calendar month. Set to e.g. 25 for "salary on 25th" use case.
     * Call [refreshMonthStartDay] to update from preferences (called by ViewModel on init).
     */
    @Volatile
    private var _monthStartDay: Int = 1
    val monthStartDay: Int get() = _monthStartDay

    /**
     * Refresh the cached month start day from AppPreferences.
     * Should be called from a coroutine context (e.g. ViewModel init).
     */
    suspend fun refreshMonthStartDay() {
        _monthStartDay = appPreferences.getMonthStartDay()
    }

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

    /**
     * Get the most recent custom budget's date range from the database.
     * Used to restore the custom date selection when re-entering the Budget screen.
     * Returns Pair(startDate, endDate) or null if no custom budgets exist.
     */
    suspend fun getMostRecentCustomDateRange(): Pair<Long, Long>? {
        val customBudgets = budgetDao.getActiveCustomBudgets()
        if (customBudgets.isEmpty()) return null
        // Use the most recently created custom budget's dates
        val mostRecent = customBudgets.maxByOrNull { it.createdAt }
        val start = mostRecent?.customStartDate ?: return null
        val end = mostRecent?.customEndDate ?: return null
        return Pair(start, end)
    }

    // ==================== Period Range Helpers ====================

    /**
     * Get the start and end timestamps (millis) for a given period type and calendar position.
     *
     * For standard periods (WEEKLY/MONTHLY/YEARLY), the calendar determines which
     * period to compute (e.g. April 2026 vs March 2026).
     *
     * For CUSTOM periods, this is not used — the range comes from the budget entity itself.
     *
     * @param period The period type.
     * @param calendar The calendar positioned at the desired period.
     */
    fun getPeriodRange(period: BudgetPeriod, calendar: Calendar = Calendar.getInstance()): Pair<Long, Long> {
        val cal = calendar.clone() as Calendar
        return when (period) {
            BudgetPeriod.WEEKLY -> {
                // Set to Monday of the week
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                // If the computed Monday is in the future (e.g. Sunday + firstDayOfWeek issue),
                // go back one week
                if (cal.timeInMillis > calendar.timeInMillis) {
                    cal.add(Calendar.WEEK_OF_YEAR, -1)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            BudgetPeriod.MONTHLY -> {
                val startDay = _monthStartDay.coerceIn(1, 28)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                // Determine period start:
                // If calendar's day >= startDay, period started this month on startDay.
                // If calendar's day < startDay, period started last month on startDay.
                if (cal.get(Calendar.DAY_OF_MONTH) >= startDay) {
                    cal.set(Calendar.DAY_OF_MONTH, startDay)
                } else {
                    cal.add(Calendar.MONTH, -1)
                    cal.set(Calendar.DAY_OF_MONTH, startDay)
                }
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            BudgetPeriod.YEARLY -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            BudgetPeriod.CUSTOM -> {
                // For CUSTOM, the range is on the budget itself, not from a calendar.
                // Return "now" as a fallback (callers should use budget.customStartDate/customEndDate).
                val now = System.currentTimeMillis()
                Pair(now, now)
            }
        }
    }

    /**
     * Legacy alias — uses Calendar.getInstance() (i.e. "current" period).
     * Used by HomeViewModel and BudgetService which always want the current period.
     */
    fun getCurrentPeriodRange(period: BudgetPeriod): Pair<Long, Long> {
        return getPeriodRange(period, Calendar.getInstance())
    }

    // ==================== Spending Queries ====================

    /**
     * Get actual spending for a budget within a specific date range.
     *
     * For standard periods, the caller provides start/end from getPeriodRange().
     * For CUSTOM periods, the caller uses the budget's own customStartDate/customEndDate.
     */
    suspend fun getSpendingForBudgetInRange(budget: Budget, start: Long, end: Long): Double {
        return when {
            budget.categoryId == null -> {
                expenseDao.getTotalSpendingInRange(start, end)
            }
            budget.isGroupBudget -> {
                expenseDao.getGroupSpendingInRange(budget.categoryId, start, end)
            }
            else -> {
                expenseDao.getSubcategorySpendingInRange(budget.categoryId, start, end)
            }
        }
    }

    /**
     * Get actual spending for a budget using the current period (legacy — for HomeViewModel/BudgetService).
     * Uses Calendar.getInstance() to determine the period range.
     */
    suspend fun getSpendingForBudget(budget: Budget): Double {
        val (start, end) = if (budget.period == BudgetPeriod.CUSTOM) {
            Pair(budget.customStartDate ?: 0L, budget.customEndDate ?: 0L)
        } else {
            getCurrentPeriodRange(budget.period)
        }
        return getSpendingForBudgetInRange(budget, start, end)
    }

    // ==================== Progress Computation ====================

    /**
     * Compute BudgetProgress for all active budgets (all periods, current date).
     * Used by HomeViewModel to render progress bars and by BudgetService for alerts.
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

    /**
     * Compute BudgetProgress for active budgets filtered by period type,
     * using a specific calendar position for date range computation.
     *
     * This is the **period-aware** version used by the Budget screen.
     * It ensures that when the user navigates to April, only April expenses count.
     *
     * @param period The period type (WEEKLY/MONTHLY/YEARLY).
     * @param calendar The calendar positioned at the selected period.
     */
    suspend fun getBudgetProgressListForPeriod(
        period: BudgetPeriod,
        calendar: Calendar = Calendar.getInstance()
    ): List<BudgetProgress> {
        val categoryMap = buildCategoryMap()

        if (period == BudgetPeriod.CUSTOM) {
            // For CUSTOM, load all custom budgets
            val entities = budgetDao.getActiveCustomBudgets()
            return entities.map { entity ->
                val budget = entity.toDomain(categoryMap)
                val start = budget.customStartDate ?: 0L
                val end = budget.customEndDate ?: 0L
                val spent = getSpendingForBudgetInRange(budget, start, end)
                val percentage = if (budget.amount > 0) (spent / budget.amount) * 100.0 else 0.0
                BudgetProgress(
                    budget = budget,
                    spent = spent,
                    percentage = percentage,
                    status = BudgetStatus.fromPercentage(percentage)
                )
            }
        }

        // Standard periods: use the calendar to compute the correct date range
        val (start, end) = getPeriodRange(period, calendar)
        val entities = budgetDao.getActiveBudgetsByPeriod(period.name)
        return entities.map { entity ->
            val budget = entity.toDomain(categoryMap)
            val spent = getSpendingForBudgetInRange(budget, start, end)
            val percentage = if (budget.amount > 0) (spent / budget.amount) * 100.0 else 0.0
            BudgetProgress(
                budget = budget,
                spent = spent,
                percentage = percentage,
                status = BudgetStatus.fromPercentage(percentage)
            )
        }
    }

    /**
     * Get total budgeted amount for a specific period type only.
     * Used by the income allocation card on the Budget screen.
     */
    suspend fun getTotalBudgetedForPeriod(period: BudgetPeriod): Double {
        if (period == BudgetPeriod.CUSTOM) {
            val entities = budgetDao.getActiveCustomBudgets()
            return entities.sumOf { it.amount }
        }
        val entities = budgetDao.getActiveBudgetsByPeriod(period.name)
        val categoryBudgets = entities.filter { it.categoryId != null }
        return if (categoryBudgets.isNotEmpty()) {
            categoryBudgets.sumOf { it.amount }
        } else {
            entities.sumOf { it.amount }
        }
    }

    // ==================== Period Key / Label / Navigation ====================

    /**
     * Get a period key string for income lookup, based on period type and a Calendar reference.
     *
     * For MONTHLY with monthStartDay offset, the key uses the period-start date:
     * e.g. monthStartDay=25 with calendar in late March → key "2026-03-25" (period Mar 25–Apr 24).
     *
     * - MONTHLY (startDay=1) → "2026-03"
     * - MONTHLY (startDay≠1) → "2026-03-25" (year-month-startDay of period start)
     * - WEEKLY  → "2026-W13"
     * - YEARLY  → "2026"
     * - CUSTOM  → "custom-{startMs}-{endMs}" (legacy)
     */
    fun getPeriodKey(
        period: BudgetPeriod,
        calendar: Calendar = Calendar.getInstance(),
        customStart: Long? = null,
        customEnd: Long? = null
    ): String {
        return when (period) {
            BudgetPeriod.MONTHLY -> {
                val startDay = _monthStartDay.coerceIn(1, 28)
                if (startDay == 1) {
                    // Standard key: "2026-03"
                    val year = calendar.get(Calendar.YEAR)
                    val month = calendar.get(Calendar.MONTH) + 1
                    String.format("%04d-%02d", year, month)
                } else {
                    // Offset key: compute period start, use "2026-03-25"
                    val (startMs, _) = getPeriodRange(period, calendar)
                    val startCal = Calendar.getInstance().apply { timeInMillis = startMs }
                    val year = startCal.get(Calendar.YEAR)
                    val month = startCal.get(Calendar.MONTH) + 1
                    val day = startCal.get(Calendar.DAY_OF_MONTH)
                    String.format("%04d-%02d-%02d", year, month, day)
                }
            }
            BudgetPeriod.WEEKLY -> {
                val year = calendar.get(Calendar.YEAR)
                val week = calendar.get(Calendar.WEEK_OF_YEAR)
                String.format("%04d-W%02d", year, week)
            }
            BudgetPeriod.YEARLY -> {
                val year = calendar.get(Calendar.YEAR)
                String.format("%04d", year)
            }
            BudgetPeriod.CUSTOM -> {
                "custom-${customStart ?: 0}-${customEnd ?: 0}"
            }
        }
    }

    /**
     * Get a human-readable label for a period, based on period type and a Calendar reference.
     *
     * For MONTHLY with monthStartDay=1: "March 2026"
     * For MONTHLY with monthStartDay≠1: "Mar 25 – Apr 24, 2026"
     *
     * - WEEKLY  → "Mar 24 – Mar 30, 2026"
     * - YEARLY  → "2026"
     * - CUSTOM  → "Mar 25 – Apr 25, 2026" (legacy)
     */
    fun getPeriodLabel(
        period: BudgetPeriod,
        calendar: Calendar = Calendar.getInstance(),
        customStart: Long? = null,
        customEnd: Long? = null
    ): String {
        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val shortMonthNames = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        return when (period) {
            BudgetPeriod.MONTHLY -> {
                val startDay = _monthStartDay.coerceIn(1, 28)
                if (startDay == 1) {
                    // Standard label: "March 2026"
                    val year = calendar.get(Calendar.YEAR)
                    val month = calendar.get(Calendar.MONTH)
                    "${monthNames[month]} $year"
                } else {
                    // Offset label: "Mar 25 – Apr 24, 2026"
                    val (startMs, endMs) = getPeriodRange(period, calendar)
                    val startCal = Calendar.getInstance().apply { timeInMillis = startMs }
                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = endMs
                        add(Calendar.DAY_OF_MONTH, -1) // end is exclusive, show last inclusive day
                    }
                    val sMonth = shortMonthNames[startCal.get(Calendar.MONTH)]
                    val sDay = startCal.get(Calendar.DAY_OF_MONTH)
                    val eMonth = shortMonthNames[endCal.get(Calendar.MONTH)]
                    val eDay = endCal.get(Calendar.DAY_OF_MONTH)
                    val eYear = endCal.get(Calendar.YEAR)
                    "$sMonth $sDay – $eMonth $eDay, $eYear"
                }
            }
            BudgetPeriod.WEEKLY -> {
                // Compute the Monday of the week
                val weekCal = calendar.clone() as Calendar
                weekCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                if (weekCal.timeInMillis > calendar.timeInMillis) {
                    weekCal.add(Calendar.WEEK_OF_YEAR, -1)
                }
                val startMonth = shortMonthNames[weekCal.get(Calendar.MONTH)]
                val startDay = weekCal.get(Calendar.DAY_OF_MONTH)
                weekCal.add(Calendar.DAY_OF_MONTH, 6)
                val endMonth = shortMonthNames[weekCal.get(Calendar.MONTH)]
                val endDay = weekCal.get(Calendar.DAY_OF_MONTH)
                val year = weekCal.get(Calendar.YEAR)
                "$startMonth $startDay – $endMonth $endDay, $year"
            }
            BudgetPeriod.YEARLY -> {
                "${calendar.get(Calendar.YEAR)}"
            }
            BudgetPeriod.CUSTOM -> {
                if (customStart != null && customEnd != null) {
                    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    "${fmt.format(customStart)} – ${fmt.format(customEnd)}"
                } else {
                    "Custom Period"
                }
            }
        }
    }

    /**
     * Navigate the calendar by a delta for the given period type.
     * Returns a new Calendar positioned at the new period.
     * Not applicable for CUSTOM (returns same calendar).
     */
    fun navigateCalendar(period: BudgetPeriod, current: Calendar, delta: Int): Calendar {
        val newCal = current.clone() as Calendar
        when (period) {
            BudgetPeriod.WEEKLY -> newCal.add(Calendar.WEEK_OF_YEAR, delta)
            BudgetPeriod.MONTHLY -> newCal.add(Calendar.MONTH, delta)
            BudgetPeriod.YEARLY -> newCal.add(Calendar.YEAR, delta)
            BudgetPeriod.CUSTOM -> { /* no navigation for custom */ }
        }
        return newCal
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

        // Ensure month start day is fresh (may be called from SmsReceiver before any ViewModel)
        refreshMonthStartDay()

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
     * Compute BudgetProgress for budgets affected by a specific expense category.
     * Returns progress for: Total Spending budget (categoryId=null) + group budget + sub-category budget.
     * Used for scoped budget notifications (max ~3 budgets).
     *
     * @param groupId The category group ID.
     * @param subcategoryId The sub-category ID.
     */
    suspend fun getAffectedBudgetProgress(groupId: Long, subcategoryId: Long): List<BudgetProgress> {
        refreshMonthStartDay()
        val categoryMap = buildCategoryMap()
        val affectedEntities = budgetDao.getBudgetsAffectedByCategory(groupId, subcategoryId)
        return affectedEntities.map { entity ->
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

    // ==================== Income ====================

    /**
     * Get income for a specific period key (e.g. "2026-03", "2026-W13", "custom-xxx-yyy").
     * Returns the amount, or null if no income set for that period.
     */
    suspend fun getMonthlyIncome(yearMonth: String): Double? {
        return incomeDao.getByYearMonth(yearMonth)?.amount
    }

    /**
     * Set (upsert) income for a specific period key.
     * If income already exists for that period, it's replaced.
     */
    suspend fun setMonthlyIncome(yearMonth: String, amount: Double, note: String? = null) {
        val existing = incomeDao.getByYearMonth(yearMonth)
        incomeDao.upsert(
            IncomeEntity(
                id = existing?.id ?: 0,
                amount = amount,
                yearMonth = yearMonth,
                note = note,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Get the current year-month string (e.g. "2026-03").
     */
    fun getCurrentYearMonth(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        return String.format("%04d-%02d", year, month)
    }

    /**
     * Compute the sum of all active budget amounts (for allocation comparison).
     * Only counts category budgets to avoid double counting with Total budgets.
     */
    suspend fun getTotalBudgetedAmount(): Double {
        val entities = budgetDao.getActiveBudgetsList()
        val categoryBudgets = entities.filter { it.categoryId != null }
        return if (categoryBudgets.isNotEmpty()) {
            categoryBudgets.sumOf { it.amount }
        } else {
            entities.sumOf { it.amount }
        }
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
            customStartDate = customStartDate,
            customEndDate = customEndDate,
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
            customStartDate = customStartDate,
            customEndDate = customEndDate,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
