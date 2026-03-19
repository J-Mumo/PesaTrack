package com.pesatrack.domain.models

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
