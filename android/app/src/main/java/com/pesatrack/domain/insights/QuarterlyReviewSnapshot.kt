package com.pesatrack.domain.insights

/**
 * Immutable, in-memory representation of a Quarterly Review report.
 *
 * Built by [QuarterlyReviewGenerator] from raw aggregates and rendered by the
 * Quarterly Review screen / notification. Persistence is handled separately via
 * [com.pesatrack.data.local.database.entities.ReportSnapshotEntity].
 */
data class QuarterlyReviewSnapshot(
    /** Unique identifier for this snapshot. */
    val id: String,
    /** Human-readable label, e.g. "Q1 2026". */
    val periodLabel: String,
    /** Total spend in the quarter (KES). */
    val periodTotal: Double,
    /** Total spend in the previous quarter (KES). */
    val prevQuarterTotal: Double,
    /** periodTotal - prevQuarterTotal. */
    val delta: Double,
    /** Percentage change vs previous quarter. Null when previous is zero. */
    val deltaPercent: Double?,
    /** Top 5 categories by spend. */
    val topCategories: List<CategoryBreakdown>,
    /** Category with largest absolute change vs previous quarter. */
    val biggestMover: BiggestMover?,
    /** Total transaction fees in the quarter (category 606). */
    val totalFees: Double,
    /** Monthly headroom trend across the quarter (up to 3 entries). */
    val savingsMomentum: SavingsMomentum?,
    /** Investment illustration for quarterly savings. */
    val investmentIllustration: InvestmentIllustration?,
    /** When the snapshot was generated (epoch millis). */
    val generatedAt: Long
)

/**
 * Category with the biggest absolute change between quarters.
 */
data class BiggestMover(
    val categoryName: String,
    val currentAmount: Double,
    val previousAmount: Double,
    /** Percentage change vs previous quarter. */
    val changePercent: Double
)

/**
 * Monthly headroom trend across the quarter.
 * Shows whether the user's savings capacity improved or declined over 3 months.
 */
data class SavingsMomentum(
    /** List of monthly headroom values (income - spend) for each month of the quarter. */
    val headroomPerMonth: List<MonthlyHeadroom>
)

/**
 * Headroom for a single month within a quarter.
 */
data class MonthlyHeadroom(
    val monthLabel: String,
    val headroom: Double
)
