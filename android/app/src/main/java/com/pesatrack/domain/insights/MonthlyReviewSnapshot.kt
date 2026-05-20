package com.pesatrack.domain.insights

import java.time.LocalDate

/**
 * Immutable, in-memory representation of a Monthly Review report.
 *
 * Built by [MonthlyReviewGenerator] from raw aggregates and rendered by the
 * Monthly Review screen / notification. Persistence is handled separately via
 * [com.pesatrack.data.local.database.entities.ReportSnapshotEntity].
 */
data class MonthlyReviewSnapshot(
    /** Unique identifier for this snapshot. */
    val id: String,
    /** First day of the reviewed month. */
    val monthStart: LocalDate,
    /** Last day of the reviewed month. */
    val monthEnd: LocalDate,
    /** Human-readable month name, e.g. "May 2026". */
    val monthName: String,
    /** Total spend in the month (KES). */
    val totalSpent: Double,
    /** Total spend in the previous calendar month (KES). */
    val previousMonthTotal: Double,
    /** totalSpent - previousMonthTotal. */
    val deltaAmount: Double,
    /** Percentage change vs previous month. Null when previous is zero. */
    val deltaPercent: Double?,
    /** Average spend per day across [daysInMonth]. */
    val averagePerDay: Double,
    /** Number of days in the reviewed month. */
    val daysInMonth: Int,
    /** Top 5 categories by spend + "Others" rollup. */
    val topCategories: List<CategoryBreakdown>,
    /** Category with largest absolute change vs previous month. */
    val biggestChangeCategory: CategoryChange?,
    /** Total transaction fees in the month (category 606). */
    val feesPaid: Double,
    /** Income minus totalSpent, null if no income set. */
    val headroom: Double?,
    /** Monthly income (null if not set). */
    val monthlyIncome: Double?,
    /** Projected end-of-month total based on current daily average × daysInMonth. */
    val pace: Double,
    /** Investment illustration for discretionary spend. */
    val investmentIllustration: InvestmentIllustration,
    /** When the snapshot was generated (epoch millis). */
    val generatedAt: Long
)

/**
 * One row in the Top Categories breakdown.
 */
data class CategoryBreakdown(
    val categoryName: String,
    val categoryId: Int,
    val amount: Double,
    /** Share of total spend (excluding fees), expressed as 0–100. */
    val percent: Double
)

/**
 * The biggest-change category card payload.
 */
data class CategoryChange(
    val categoryName: String,
    val categoryId: Int,
    val currentAmount: Double,
    val previousAmount: Double,
    /** Percentage change vs previous month. */
    val changePercent: Double
)

/**
 * Investment illustration payload.
 *
 * Shows what discretionary spending could yield if invested. Always surfaced
 * with explicit assumptions per Principle 5.
 */
data class InvestmentIllustration(
    /** Discretionary spend for the month (total - fees). */
    val principalAmount: Double,
    /** Annual rate used in the illustration. */
    val annualRate: Double,
    /** Compounding periods per year. */
    val compoundingPeriodsPerYear: Int,
    /** Investment horizon in months. */
    val horizonMonths: Int,
    /** Computed future value. */
    val futureValue: Double,
    /** Disclaimer text. */
    val disclaimer: String
)
