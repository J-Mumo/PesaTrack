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
     * Check forecast projections after an expense was saved and send a proactive notification.
     *
     * **One-shot per period**: a forecast notification fires ONCE when a budget's projection
     * first crosses 75% projected for the current period. It will not fire again until the
     * next period starts (new month/week/year).
     *
     * Scoped: only evaluates budgets affected by this expense's category (max ~3: total, group, sub-category).
     * Capped: sends at most 1 forecast notification per expense event (the highest-priority one).
     * Skipped if the same budget already triggered an 80%/100% threshold alert (redundant).
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
            // Only compute forecasts for budgets affected by this expense's category
            val forecasts = forecastService.getForecastsForAffectedBudgets(expenseCategoryId)

            // Filter to actionable forecasts:
            // - Not already covered by a threshold alert (80%/100% actual)
            // - Projected ≥75% (the threshold crossing that triggers the one-shot notification)
            // - Not already notified in this period (one-shot per period per budget)
            val candidates = forecasts
                .filter { it.budget.id !in budgetAlertIds }
                .filter { it.projectedPercentage >= FORECAST_NOTIFY_THRESHOLD }
                .filter { forecast ->
                    val periodKey = budgetRepository.getPeriodKey(forecast.budget.period)
                    appPreferences.canSendForecastNotification(forecast.budget.id, periodKey)
                }

            // Pick the single highest-priority forecast:
            // 1. Exhaustion imminent has top priority
            // 2. Then highest projected percentage
            val best = candidates
                .sortedWith(
                    compareByDescending<BudgetForecast> { it.isExhaustionImminent }
                        .thenByDescending { it.projectedPercentage }
                )
                .firstOrNull() ?: return

            // Send exactly 1 notification
            NotificationHelper.showForecastNotification(
                context = context,
                budgetId = best.budget.id,
                categoryName = best.budget.categoryName ?: "Total Spending",
                projectedTotal = best.projectedTotal,
                budgetAmount = best.budget.amount,
                projectedPercentage = best.projectedPercentage.toInt(),
                safeDailyBudget = best.safeDailyBudget,
                daysRemaining = best.daysRemaining,
                exhaustionImminent = best.isExhaustionImminent,
                remaining = best.budget.amount - best.spent
            )

            // Mark this budget as notified for this period (one-shot — won't fire again this period)
            val periodKey = budgetRepository.getPeriodKey(best.budget.period)
            appPreferences.setForecastNotifPeriodKey(best.budget.id, periodKey)
        } catch (_: Exception) {
            // Non-critical — silently fail
        }
    }

    companion object {
        /**
         * Projected percentage threshold at which a forecast notification fires.
         * Only fires once per budget per period when this threshold is first crossed.
         */
        const val FORECAST_NOTIFY_THRESHOLD = 75.0
    }
}
