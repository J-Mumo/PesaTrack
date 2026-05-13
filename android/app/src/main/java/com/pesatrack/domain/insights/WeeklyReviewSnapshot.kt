package com.pesatrack.domain.insights

/**
 * Immutable, in-memory representation of a Weekly Review report.
 *
 * Built by [WeeklyReviewGenerator] from raw aggregates and rendered by the
 * Weekly Review screen / notification. Persistence is handled separately via
 * [com.pesatrack.data.local.database.entities.ReportSnapshotEntity].
 *
 * The model intentionally carries pre-computed numbers (delta, percentage,
 * average per day) so the UI layer never has to recompute or interpret raw
 * data. This keeps the generator pure and easy to unit-test, and the UI
 * layer focused only on presentation.
 *
 * See plans/insights-and-reports-plan.md for the report anatomy this maps to.
 */
data class WeeklyReviewSnapshot(
    /** Inclusive start of the 7-day window (epoch millis). */
    val periodStart: Long,
    /** Exclusive end of the window (epoch millis). */
    val periodEnd: Long,
    /** When the snapshot was generated (epoch millis). */
    val generatedAt: Long,
    /** Total spend in the window (KES). */
    val periodTotal: Double,
    /** Total spend in the previous 7 days (KES). */
    val previousPeriodTotal: Double,
    /** Average spend per day across [periodDays] (KES). */
    val averagePerDay: Double,
    /** Number of days the window covers (typically 7). */
    val periodDays: Int,
    /** The category with the largest absolute delta vs. last week (null when no data). */
    val biggestChange: BiggestChange?,
    /** Top 5 categories by spend. May contain fewer than 5 entries. */
    val topCategories: List<CategoryShare>,
    /** Spend rolled into "(N others: KES X)" — i.e. everything outside Top 5. */
    val othersAmount: Double,
    /** Number of categories rolled into [othersAmount]. */
    val othersCount: Int,
    /** Total transaction fees in the window (category 606). */
    val feesTotal: Double,
    /** Headroom for the current calendar month, null when the user has no income set. */
    val headroom: Headroom?,
    /** True when there is not enough history to render honest comparisons. */
    val limitedData: Boolean
) {
    /**
     * Signed delta vs. last week, in KES. Positive when spending went up.
     * Returns 0 when there is no previous-week baseline.
     */
    val periodDelta: Double get() = periodTotal - previousPeriodTotal

    /**
     * Percentage change vs. last week (0–∞). `null` when previous week was zero
     * (no honest comparison is possible).
     */
    val periodDeltaPercent: Double? get() =
        if (previousPeriodTotal <= 0.0) null
        else ((periodTotal - previousPeriodTotal) / previousPeriodTotal) * 100.0
}

/**
 * One row in the Top 5 categories card.
 */
data class CategoryShare(
    val categoryId: Long?,
    val name: String,
    val amount: Double,
    /** Share of the period total, expressed as 0–100. */
    val percentageOfPeriod: Double
)

/**
 * The biggest-change card payload.
 */
data class BiggestChange(
    val categoryId: Long?,
    val name: String,
    /** Signed change vs. previous period in KES. Positive when spending went up. */
    val deltaAmount: Double
)

/**
 * Headroom card payload. Always tied to the current calendar month; the
 * Weekly Review surfaces this rather than a week-specific headroom because
 * weekly income is not meaningful for most users.
 */
data class Headroom(
    /** Label describing the income window, e.g. "May 2026". */
    val label: String,
    /** Income for the month (KES). */
    val income: Double,
    /** Spend so far in the month (KES). */
    val spendSoFar: Double,
    /** Days left in the month (0+). */
    val daysRemaining: Int
) {
    /** Income minus spend (can be negative). */
    val available: Double get() = income - spendSoFar
}
