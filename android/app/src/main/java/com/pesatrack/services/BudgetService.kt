package com.pesatrack.services

import android.content.Context
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.BudgetRepository
import com.pesatrack.domain.models.BudgetAlert
import com.pesatrack.domain.models.BudgetForecast
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for checking budget thresholds and forecasts after expenses are saved.
 *
 * Called from [SmsReceiver] and ViewModels after a successful expense insert.
 * Returns a list of [BudgetAlert]s if any budget crossed the 80% or 100% threshold.
 * Also checks forecast projections for proactive warnings.
 *
 * Checks all three budget levels:
 * - Total Spending budget (always checked)
 * - Group-level budget (if expense's group has one)
 * - Sub-category-level budget (if expense's exact category has one)
 */
@Singleton
class BudgetService @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val forecastService: ForecastService,
    private val appPreferences: AppPreferences
) {

    /**
     * Check if any budgets have crossed a threshold after an expense was saved.
     *
     * @param expenseCategoryId The sub-category ID of the expense (not the group ID).
     *                          This method resolves the group ID internally.
     *                          Pass null if the expense is uncategorized — no alerts will fire.
     * @return List of [BudgetAlert]s for budgets at or above a threshold.
     */
    suspend fun checkBudgetsAfterExpense(expenseCategoryId: Long?): List<BudgetAlert> {
        if (expenseCategoryId == null) return emptyList()

        return budgetRepository.checkBudgetAlerts(expenseCategoryId)
    }

    /**
     * Check forecast projections after an expense was saved and send proactive notifications.
     *
     * Throttled: max 1 forecast notification per budget per 24 hours.
     * Skipped if the same budget already triggered an 80%/100% alert (redundant).
     *
     * @param context Application context for showing notifications.
     * @param expenseCategoryId The sub-category ID of the saved expense.
     * @param budgetAlertIds Set of budget IDs that already fired threshold alerts (to avoid overlap).
     */
    suspend fun checkForecastsAfterExpense(
        context: Context,
        expenseCategoryId: Long?,
        budgetAlertIds: Set<Long> = emptySet()
    ) {
        if (expenseCategoryId == null) return

        try {
            val forecasts = forecastService.getForecastsForActiveBudgets()

            for (forecast in forecasts) {
                val budgetId = forecast.budget.id

                // Skip if this budget already fired a threshold alert
                if (budgetId in budgetAlertIds) continue

                // Only notify for projected overspend or imminent exhaustion
                if (!forecast.isProjectedOverBudget && !forecast.isExhaustionImminent) continue

                // Check throttle (max 1 per budget per 24h)
                if (!appPreferences.canSendForecastNotification(budgetId)) continue

                // Send notification
                NotificationHelper.showForecastNotification(
                    context = context,
                    budgetId = budgetId,
                    categoryName = forecast.budget.categoryName ?: "Total Spending",
                    projectedTotal = forecast.projectedTotal,
                    budgetAmount = forecast.budget.amount,
                    projectedPercentage = forecast.projectedPercentage.toInt(),
                    safeDailyBudget = forecast.safeDailyBudget,
                    daysRemaining = forecast.daysRemaining,
                    exhaustionImminent = forecast.isExhaustionImminent,
                    remaining = forecast.budget.amount - forecast.spent
                )

                // Record throttle timestamp
                appPreferences.setLastForecastNotifTime(budgetId)
            }
        } catch (_: Exception) {
            // Non-critical — silently fail
        }
    }
}
