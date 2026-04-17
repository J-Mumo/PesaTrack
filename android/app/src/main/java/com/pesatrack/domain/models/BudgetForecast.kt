package com.pesatrack.domain.models

/**
 * Forecast projection for a single budget within its current period.
 *
 * Uses a linear burn rate model: `dailyBurnRate = spent / daysElapsed`.
 * All values are computed from existing [BudgetProgress] data — no new
 * database queries needed.
 */
data class BudgetForecast(
    /** The budget being forecast. */
    val budget: Budget,
    /** Actual spending so far in this period (KES). */
    val spent: Double,
    /** Computed daily burn rate (KES/day). */
    val dailyBurnRate: Double,
    /** Date (epoch millis) the budget is projected to be exhausted. Null if on track. */
    val exhaustionDate: Long?,
    /** Projected total spend at end of period (KES). */
    val projectedTotal: Double,
    /** Projected percentage of budget at end of period. */
    val projectedPercentage: Double,
    /** Remaining budget divided by remaining days (KES/day). Negative if already over. */
    val safeDailyBudget: Double,
    /** Days remaining in the budget period. */
    val daysRemaining: Int,
    /** Days elapsed in the budget period. */
    val daysElapsed: Int,
    /** Total days in the period. */
    val totalDays: Int
) {
    /** Whether the budget is projected to exceed 100% by end of period. */
    val isProjectedOverBudget: Boolean
        get() = projectedPercentage > 100.0

    /**
     * Whether the budget is projected to run out within the next 5 days.
     * Only meaningful when [exhaustionDate] is non-null and there are days remaining.
     */
    val isExhaustionImminent: Boolean
        get() = exhaustionDate != null && daysRemaining > 0 &&
                (exhaustionDate - System.currentTimeMillis()) < 5 * MS_PER_DAY

    companion object {
        const val MS_PER_DAY = 86_400_000L

        /**
         * Minimum days elapsed before a forecast is considered reliable.
         * Projections from 1–4 days are too noisy to show.
         */
        const val MIN_DAYS_FOR_FORECAST = 5
    }
}
