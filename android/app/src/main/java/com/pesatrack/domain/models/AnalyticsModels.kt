package com.pesatrack.domain.models

import com.pesatrack.data.local.database.dao.MonthlyTotal

/**
 * Computed month-over-month comparison result.
 * Produced by the ViewModel from two MonthlyTotal values.
 */
data class MonthComparison(
    val currentMonthTotal: Double,
    val previousMonthTotal: Double,
    /** Positive = spending increased, negative = spending decreased */
    val percentageChange: Double,
    val currentMonthLabel: String,   // e.g. "March 2026"
    val previousMonthLabel: String   // e.g. "February 2026"
)

/**
 * Represents a category's spending trend over multiple months,
 * including statistical analysis for variable-spend detection.
 */
data class CategoryTrend(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String?,
    /** Monthly data points (filled to always have exactly N entries) */
    val monthlyData: List<MonthlyTotal>,
    /** Mean monthly spend (μ) */
    val mean: Double,
    /** Standard deviation (σ) */
    val standardDeviation: Double,
    /** Coefficient of variation: σ/μ × 100. Higher = more volatile */
    val coefficientOfVariation: Double,
    /** Current (latest) month's total */
    val currentMonthTotal: Double,
    /** True if current month > μ + σ */
    val isOverspending: Boolean,
    /** How far above average the current month is, as a percentage */
    val overspendPercentage: Double
) {
    enum class SpendLevel {
        NORMAL,   // current ≤ μ + 0.5σ
        ELEVATED, // μ + 0.5σ < current ≤ μ + σ
        HIGH      // current > μ + σ
    }

    val spendLevel: SpendLevel
        get() {
            val threshold05 = mean + 0.5 * standardDeviation
            val threshold10 = mean + standardDeviation
            return when {
                currentMonthTotal > threshold10 -> SpendLevel.HIGH
                currentMonthTotal > threshold05 -> SpendLevel.ELEVATED
                else -> SpendLevel.NORMAL
            }
        }
}

/**
 * Default category IDs that are known to have high spend variance
 * in a typical Kenyan context. Used as fallback when not enough
 * data exists for CV-based detection.
 */
/**
 * Year-over-Year comparison result.
 * Produced by the ViewModel from two annual totals.
 */
data class YearComparison(
    val currentYearTotal: Double,
    val previousYearTotal: Double,
    /** Positive = spending increased, negative = spending decreased */
    val percentageChange: Double,
    val currentYearLabel: String,    // e.g. "2026"
    val previousYearLabel: String    // e.g. "2025"
)

val DEFAULT_VARIABLE_SPEND_CATEGORIES = setOf(
    1712L, // Fuel
    703L,  // Groceries
    702L,  // Eating Out
    1608L, // Uber/Bolt
    1602L, // Boda Boda
    205L,  // Data Bundles
    202L,  // Airtime
    1002L, // Electricity
    704L,  // Snacks/Drinks
    906L,  // Pharmacy
    1505L, // General Shopping
    1503L  // Clothing
)

// ==================== Category × Month Grid (Analytics Yearly → Grid) ====================

/**
 * Full-year pivot of spend by category × period. Backs the Analytics
 * "Yearly → Grid" sub-tab and provides the same data structure the Home
 * "Trend by group" preview uses for its 3-month subset.
 *
 * Periods honour `monthStartDay` — a user on the 25th sees 12 columns
 * labelled "Jan 25 – Feb 24", etc., not calendar Jan–Dec.
 *
 * Groups (rows whose category has `parentId == null`) come first; every
 * group is followed by its sub-categories, both sorted by `yearTotal` desc.
 * Callers that want a groups-only view filter to `depth == 0` and take the
 * top N.
 */
data class CategoryMonthGrid(
    /** Year the grid covers, based on the first period's start. */
    val year: Int,
    /** N short period labels, one per column (e.g. `["Jan","Feb",…]`). */
    val periodLabels: List<String>,
    /** All rows: groups and, immediately after each group, its sub-categories. */
    val rows: List<GridRow>,
    /** Column totals, one per period (same length as [periodLabels]). */
    val periodTotals: List<Double>,
    /** Sum of every cell in the grid. */
    val grandTotal: Double,
    /**
     * 0-based column indexes that are partial (current period, or the very
     * first period if it starts before the user has any recorded activity).
     * The UI should mark these with a `*` and skip them from any heat-map.
     */
    val partialPeriodIndexes: Set<Int>,
    /**
     * True when the include-fees toggle was on and Investment & Savings row
     * indices should still highlight positively — for the Grid this is
     * informational only; the direction semantics live on [GroupTrendRow].
     */
    val includesFees: Boolean = false
)

/** One row of [CategoryMonthGrid]. Groups (depth 0) and sub-categories (depth 1). */
data class GridRow(
    val categoryId: Long,
    val label: String,
    val color: String?,
    /** 0 = group (no parent), 1 = sub-category. */
    val depth: Int,
    val parentId: Long?,
    /**
     * One entry per column. `null` means the category had no expenses that
     * period — rendered as `—`, never `KES 0`, so an absent period is visibly
     * distinct from a genuine zero.
     */
    val monthlyValues: List<Double?>,
    /** Sum of non-null entries in [monthlyValues]. */
    val yearTotal: Double,
    /** True if the group has at least one sub-category with activity. */
    val isExpandable: Boolean
)

/**
 * Home "Trend by group" preview data. Top N groups × last M periods (default
 * 5 × 3), sorted by combined-window total. Complements the existing "By
 * Category" section: `By Category` answers "where did this month's money
 * go?"; this answers "which groups are drifting month-over-month?"
 *
 * The last column may be the current (partial) period — see
 * [currentPeriodIsPartial]. Direction arrows compare the last period to the
 * mean of the earlier ones; for group 18 (Investment & Savings) the UI
 * should invert the semantic — an increase is a positive signal (see the
 * AGENTS.md "save/invest by default" principle).
 */
data class GroupTrendPreview(
    /** Short period labels, e.g. `["Jun","Jul","Aug"]`. */
    val periodLabels: List<String>,
    /** True when the last column is the ongoing (partial) period. */
    val currentPeriodIsPartial: Boolean,
    val rows: List<GroupTrendRow>
)

data class GroupTrendRow(
    /** The group's category id (rows always have `parentId == null`). */
    val categoryId: Long,
    val label: String,
    val color: String?,
    /** One entry per column; `null` = no activity that period. */
    val amounts: List<Double?>,
    val direction: TrendDirection,
    /**
     * True when the row is group 18 (Investment & Savings). Callers should
     * flip the direction semantics for this row so `▲` renders in the
     * positive colour.
     */
    val isInvestment: Boolean
)

/**
 * Direction of the last period vs the average of the earlier periods in the
 * preview window.
 * - `UP2` / `UP` — increase ≥ 25% / 5–25%
 * - `FLAT` — within ±5%
 * - `DOWN` / `DOWN2` — decrease 5–25% / ≥ 25%
 * - `INSUFFICIENT` — fewer than 2 non-null earlier periods to compare against
 */
enum class TrendDirection { UP2, UP, FLAT, DOWN, DOWN2, INSUFFICIENT }
