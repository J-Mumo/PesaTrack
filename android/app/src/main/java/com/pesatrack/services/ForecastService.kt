package com.pesatrack.services

import com.pesatrack.data.repository.BudgetRepository
import com.pesatrack.domain.models.Budget
import com.pesatrack.domain.models.BudgetForecast
import com.pesatrack.domain.models.BudgetPeriod
import com.pesatrack.domain.models.RecurringPeriodInfo
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Service for computing budget burn rate forecasts.
 *
 * Uses a linear burn rate model: `dailyBurnRate = spent / daysElapsed`.
 * Pure computation layer — no new database tables or queries.
 * Relies entirely on existing [BudgetRepository] data.
 *
 * Used by:
 * - [HomeViewModel] for the Home screen forecast card
 * - [BudgetViewModel] for per-budget forecast subtitles
 * - [BudgetService] for proactive forecast notifications
 * - [AnalyticsViewModel] for projection line data
 */
@Singleton
class ForecastService @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val recurringExpenseService: RecurringExpenseService
) {

    /**
     * Compute forecasts for all active budgets using the current period.
     * Returns only forecasts with sufficient data (≥ [BudgetForecast.MIN_DAYS_FOR_FORECAST] days).
     *
     * Used by HomeViewModel.
     */
    suspend fun getForecastsForActiveBudgets(): List<BudgetForecast> {
        val progressList = budgetRepository.getBudgetProgressList()
        val now = System.currentTimeMillis()

        return progressList.mapNotNull { progress ->
            val budget = progress.budget
            val (periodStart, periodEnd) = getEffectivePeriodRange(budget)
            val recurringInfo = try {
                recurringExpenseService.getRecurringInfoForPeriod(
                    periodStart, periodEnd,
                    budgetCategoryId = budget.categoryId,
                    isGroupBudget = budget.isGroupBudget
                )
            } catch (_: Exception) { null }
            computeForecast(budget, progress.spent, periodStart, periodEnd, now, recurringInfo)
        }
    }

    /**
     * Compute forecasts for active budgets filtered by period type,
     * using a specific calendar position.
     *
     * Used by BudgetViewModel for period-aware forecasts on the Budget screen.
     */
    suspend fun getForecastsForPeriod(
        period: BudgetPeriod,
        calendar: Calendar = Calendar.getInstance()
    ): List<BudgetForecast> {
        val progressList = budgetRepository.getBudgetProgressListForPeriod(period, calendar)
        val (periodStart, periodEnd) = budgetRepository.getPeriodRange(period, calendar)
        val now = System.currentTimeMillis()

        return progressList.mapNotNull { progress ->
            val budget = progress.budget
            val recurringInfo = try {
                recurringExpenseService.getRecurringInfoForPeriod(
                    periodStart, periodEnd,
                    budgetCategoryId = budget.categoryId,
                    isGroupBudget = budget.isGroupBudget
                )
            } catch (_: Exception) { null }
            computeForecast(budget, progress.spent, periodStart, periodEnd, now, recurringInfo)
        }
    }

    /**
     * Compute a forecast for a single budget given its spending and period range.
     *
     * Returns null if:
     * - Less than [BudgetForecast.MIN_DAYS_FOR_FORECAST] days have elapsed
     * - Budget amount is zero or negative
     * - Period range is invalid
     *
     * Used by BudgetService for per-expense alert checking.
     */
    fun computeForecast(
        budget: Budget,
        spent: Double,
        periodStart: Long,
        periodEnd: Long,
        now: Long = System.currentTimeMillis(),
        recurringInfo: RecurringPeriodInfo? = null
    ): BudgetForecast? {
        if (budget.amount <= 0) return null
        if (periodEnd <= periodStart) return null

        val totalDaysRaw = (periodEnd - periodStart).toDouble() / BudgetForecast.MS_PER_DAY
        val totalDays = max(1, totalDaysRaw.toInt())

        val elapsedMs = (now - periodStart).coerceAtLeast(0)
        val daysElapsed = (elapsedMs.toDouble() / BudgetForecast.MS_PER_DAY).toInt()

        if (daysElapsed < BudgetForecast.MIN_DAYS_FOR_FORECAST) return null

        val daysRemaining = max(0, totalDays - daysElapsed)

        // Linear burn rate (used as fallback and for daily burn display)
        val dailyBurnRate = if (daysElapsed > 0) spent / daysElapsed else 0.0

        // Projected total at end of period — recurring-aware if available
        val projectedTotal: Double
        val isFulfilledByRecurring: Boolean

        if (recurringInfo != null && recurringInfo.totalRecurringForPeriod > 0) {
            // Check if this budget is "fulfilled" — recurring payment(s) already cover the budget
            // and no further spending is expected. This handles budgets like rent where
            // a single payment at period start = 100% of budget and no further action needed.
            val discretionarySpent = (spent - recurringInfo.paidThisPeriod).coerceAtLeast(0.0)
            val discretionaryBurnRate = if (daysElapsed > 0) discretionarySpent / daysElapsed else 0.0
            val recurringCoversBudget = recurringInfo.paidThisPeriod >= budget.amount * 0.95
            val noUpcoming = recurringInfo.upcomingThisPeriod < budget.amount * 0.05
            val negligibleDiscretionary = discretionaryBurnRate * daysRemaining < budget.amount * 0.05

            if (recurringCoversBudget && noUpcoming && negligibleDiscretionary) {
                // Budget is fulfilled by recurring payment — project actual spent as total
                // No extrapolation needed; the budget is done for this period.
                projectedTotal = spent
                isFulfilledByRecurring = true
            } else {
                // Recurring-aware projection:
                // Separate recurring (known) from discretionary (extrapolated)
                projectedTotal = recurringInfo.paidThisPeriod +
                        recurringInfo.upcomingThisPeriod +
                        (discretionaryBurnRate * daysRemaining)
                isFulfilledByRecurring = false
            }
        } else {
            // Fallback: pure linear projection
            projectedTotal = dailyBurnRate * totalDays
            isFulfilledByRecurring = false
        }

        // Projected percentage of budget
        val projectedPercentage = if (budget.amount > 0) {
            (projectedTotal / budget.amount) * 100.0
        } else 0.0

        // Safe daily budget (how much can be spent per remaining day to stay on budget)
        val safeDailyBudget = if (daysRemaining > 0) {
            (budget.amount - spent) / daysRemaining
        } else {
            0.0
        }

        // Exhaustion date (when budget will be fully used up at current rate)
        // Suppressed for fulfilled recurring budgets — they're done, not in danger.
        val exhaustionDate: Long? = if (!isFulfilledByRecurring && dailyBurnRate > 0 && projectedTotal > budget.amount) {
            val daysToExhaustion = budget.amount / dailyBurnRate
            periodStart + (daysToExhaustion * BudgetForecast.MS_PER_DAY).toLong()
        } else {
            null
        }

        return BudgetForecast(
            budget = budget,
            spent = spent,
            dailyBurnRate = dailyBurnRate,
            exhaustionDate = exhaustionDate,
            projectedTotal = projectedTotal,
            projectedPercentage = projectedPercentage,
            safeDailyBudget = safeDailyBudget,
            daysRemaining = daysRemaining,
            daysElapsed = daysElapsed,
            totalDays = totalDays
        )
    }

    /**
     * Compute forecasts only for budgets affected by a specific expense category.
     * Returns forecasts for: Total Spending budget + group budget + sub-category budget
     * (at most 3 budgets). Used by BudgetService for scoped forecast notifications.
     *
     * @param expenseCategoryId The sub-category ID of the saved expense.
     */
    suspend fun getForecastsForAffectedBudgets(expenseCategoryId: Long): List<BudgetForecast> {
        val groupId = budgetRepository.getGroupIdForCategory(expenseCategoryId) ?: return emptyList()
        val affectedProgress = budgetRepository.getAffectedBudgetProgress(groupId, expenseCategoryId)
        val now = System.currentTimeMillis()

        return affectedProgress.mapNotNull { progress ->
            val budget = progress.budget
            val (periodStart, periodEnd) = getEffectivePeriodRange(budget)
            val recurringInfo = try {
                recurringExpenseService.getRecurringInfoForPeriod(
                    periodStart, periodEnd,
                    budgetCategoryId = budget.categoryId,
                    isGroupBudget = budget.isGroupBudget
                )
            } catch (_: Exception) { null }
            computeForecast(budget, progress.spent, periodStart, periodEnd, now, recurringInfo)
        }
    }

    /**
     * Compute forecast for a specific budget by ID.
     * Used by BudgetService for targeted alert checking.
     */
    suspend fun getForecastForBudget(budgetId: Long): BudgetForecast? {
        val budget = budgetRepository.getBudgetById(budgetId) ?: return null
        val spent = budgetRepository.getSpendingForBudget(budget)
        val (periodStart, periodEnd) = getEffectivePeriodRange(budget)
        val recurringInfo = try {
            recurringExpenseService.getRecurringInfoForPeriod(
                periodStart, periodEnd,
                budgetCategoryId = budget.categoryId,
                isGroupBudget = budget.isGroupBudget
            )
        } catch (_: Exception) { null }
        return computeForecast(budget, spent, periodStart, periodEnd, recurringInfo = recurringInfo)
    }

    /**
     * Get the effective period range for a budget, handling CUSTOM periods.
     */
    private fun getEffectivePeriodRange(budget: Budget): Pair<Long, Long> {
        return if (budget.period == BudgetPeriod.CUSTOM) {
            Pair(budget.customStartDate ?: 0L, budget.customEndDate ?: 0L)
        } else {
            budgetRepository.getCurrentPeriodRange(budget.period)
        }
    }
}
