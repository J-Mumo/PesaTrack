package com.pesatrack.domain.insights

/**
 * Immutable, in-memory representation of a Year-in-Review report.
 *
 * Built by [YearInReviewGenerator] from raw aggregates and rendered by the
 * Year-in-Review screen / notification. Persistence is handled separately via
 * [com.pesatrack.data.local.database.entities.ReportSnapshotEntity].
 */
data class YearInReviewSnapshot(
    /** The year this review covers. */
    val year: Int,
    /** Total spend in the year (KES). */
    val annualTotal: Double,
    /** Total spend in the previous year (KES). */
    val prevYearTotal: Double,
    /** annualTotal - prevYearTotal. */
    val delta: Double,
    /** Top 5 categories by spend. */
    val topCategories: List<CategoryBreakdown>,
    /** Category with largest absolute change vs previous year. */
    val biggestMover: BiggestMover?,
    /** Total transaction fees for the year (category 606). */
    val totalFees: Double,
    /** Monthly average fees. */
    val monthlyAvgFees: Double,
    /** Top 3 quiet leaks: high-frequency, low-average categories. */
    val quietLeaks: List<QuietLeakSummary>,
    /** Savings story for the year. */
    val savingsStory: SavingsStory?,
    /** Investment illustration for annual savings. */
    val investmentIllustration: InvestmentIllustration?,
    /** Optional goals progress (may be empty). */
    val goalsProgress: List<GoalProgress>?,
    /** When the snapshot was generated (epoch millis). */
    val generatedAt: Long
)

/**
 * A category with high frequency (≥50 txns/year) and low average (≤ KES 300).
 * These represent "quiet leaks" — small, frequent expenses that add up.
 */
data class QuietLeakSummary(
    val categoryName: String,
    val totalTransactions: Int,
    val totalAmount: Double
)

/**
 * Savings story for the year — months where income exceeded expenses.
 */
data class SavingsStory(
    /** Number of months where income > expenses. */
    val monthsInHeadroom: Int,
    /** Best month label (e.g. "March 2025"). */
    val bestMonth: String,
    /** Headroom in the best month. */
    val bestMonthHeadroom: Double,
    /** Total headroom across all positive months. */
    val totalHeadroom: Double
)

/**
 * Progress towards a financial goal.
 */
data class GoalProgress(
    val goalName: String,
    val target: Double,
    val achieved: Double,
    val percentage: Double
)
