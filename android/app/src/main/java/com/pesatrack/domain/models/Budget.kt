package com.pesatrack.domain.models

/**
 * Domain model for a budget — a spending limit for a category group or sub-category
 * within a specific period (weekly, monthly, yearly, or custom date range).
 */
data class Budget(
    val id: Long = 0,
    /** Category ID. Can be a group or sub-category ID. */
    val categoryId: Long?,
    /** Resolved category name. */
    val categoryName: String?,
    /** Hex colour of the category (for UI progress bar). */
    val categoryColor: String?,
    /**
     * Whether this budget tracks a whole group (true) or a single sub-category (false).
     */
    val isGroupBudget: Boolean = true,
    /** Budget limit in KES. */
    val amount: Double,
    /** Budget period. */
    val period: BudgetPeriod,
    /** Start date millis for CUSTOM period. Null for standard periods. */
    val customStartDate: Long? = null,
    /** End date millis for CUSTOM period. Null for standard periods. */
    val customEndDate: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Budget period — how often the budget resets.
 */
enum class BudgetPeriod {
    WEEKLY, MONTHLY, YEARLY, CUSTOM;

    fun displayName(): String = when (this) {
        WEEKLY -> "Weekly"
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
        CUSTOM -> "Custom"
    }

    companion object {
        fun fromString(value: String): BudgetPeriod =
            try { valueOf(value) } catch (_: Exception) { MONTHLY }
    }
}

/**
 * Budget progress — combines a budget with its actual spending for the current period.
 * Used by the UI to render progress bars.
 */
data class BudgetProgress(
    val budget: Budget,
    /** Actual spending in the current period (KES). */
    val spent: Double,
    /** Percentage of budget used: spent / budget.amount * 100. */
    val percentage: Double,
    /** Derived status based on percentage. */
    val status: BudgetStatus
)

/**
 * Budget status derived from percentage of budget used.
 */
enum class BudgetStatus {
    /** < 80% used */
    UNDER,
    /** 80–99% used */
    WARNING,
    /** ≥ 100% used */
    EXCEEDED;

    companion object {
        fun fromPercentage(percentage: Double): BudgetStatus = when {
            percentage >= 100.0 -> EXCEEDED
            percentage >= 80.0 -> WARNING
            else -> UNDER
        }
    }
}

/**
 * Alert data emitted when a budget crosses a threshold after an expense is saved.
 */
data class BudgetAlert(
    val budget: Budget,
    /** Total spending in the current period. */
    val spent: Double,
    /** Percentage of budget used. */
    val percentage: Double,
    /** The threshold that was crossed: 80 or 100. */
    val threshold: Int
)
