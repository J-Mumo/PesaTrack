package com.pesatrack.domain.models

/**
 * Simple remaining-balance summary for a budget period.
 * Replaces the previous projection / safe-daily-spend model.
 */
data class BudgetRemaining(
    val budgetId: Long,
    val remaining: Double,
    val daysRemaining: Int
)
