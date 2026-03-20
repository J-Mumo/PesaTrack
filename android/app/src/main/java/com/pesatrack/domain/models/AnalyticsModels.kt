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
