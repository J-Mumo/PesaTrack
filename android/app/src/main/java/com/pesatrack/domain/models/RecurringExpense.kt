package com.pesatrack.domain.models

import com.pesatrack.domain.models.PaymentType

/**
 * Detected recurring expense pattern.
 *
 * Produced by [com.pesatrack.services.RecurringExpenseService] from historical
 * expense data — no new database tables required. The detection algorithm groups
 * expenses by recipient, analyses temporal intervals, and assigns confidence scores.
 */
data class RecurringExpense(
    /** Normalized recipient identifier (COALESCE(recipientName, recipient)) */
    val recipientKey: String,
    /** Human-readable name for display */
    val recipientDisplayName: String,
    /** Category ID if consistently categorized across occurrences */
    val categoryId: Long?,
    /** Resolved category display name */
    val categoryName: String?,
    /** Detected recurrence cycle */
    val cycle: RecurrenceCycle,
    /** Mean amount across all detected occurrences (KES) */
    val averageAmount: Double,
    /** Most recent occurrence amount (KES) */
    val lastAmount: Double,
    /** Amount variability classification */
    val amountPattern: AmountPattern,
    /** Detection confidence: 0.0–1.0 (≥0.7 = high, 0.5–0.69 = medium) */
    val confidence: Double,
    /** Total number of occurrences in the detection window */
    val occurrenceCount: Int,
    /** Timestamp of the most recent occurrence */
    val lastOccurrence: Long,
    /** Predicted timestamp of the next occurrence */
    val nextExpected: Long,
    /** For MONTHLY cycle: the typical day of month (1–31), null for other cycles */
    val expectedDayOfMonth: Int?,
    /** Payment type of the recurring expense */
    val paymentType: PaymentType,
    /** True if nextExpected < now and no matching expense found since */
    val isOverdue: Boolean
) {
    /** Monthly-equivalent amount for totalling (adjusts weekly/yearly to monthly) */
    val monthlyEquivalent: Double
        get() = when (cycle) {
            RecurrenceCycle.WEEKLY -> averageAmount * 4.33   // ~52 weeks / 12 months
            RecurrenceCycle.BIWEEKLY -> averageAmount * 2.17 // ~26 / 12
            RecurrenceCycle.MONTHLY -> averageAmount
            RecurrenceCycle.YEARLY -> averageAmount / 12.0
        }
}

/**
 * Recurrence cycle — the detected interval between occurrences.
 */
enum class RecurrenceCycle {
    WEEKLY,     // ~7 days
    BIWEEKLY,   // ~14 days
    MONTHLY,    // ~30 days
    YEARLY;     // ~365 days

    fun displayName(): String = when (this) {
        WEEKLY -> "Weekly"
        BIWEEKLY -> "Every 2 weeks"
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
    }

    /** Expected interval in days */
    val expectedDays: Int
        get() = when (this) {
            WEEKLY -> 7
            BIWEEKLY -> 14
            MONTHLY -> 30
            YEARLY -> 365
        }

    /** Tolerance window in days for interval matching */
    val toleranceDays: Int
        get() = when (this) {
            WEEKLY -> 2
            BIWEEKLY -> 3
            MONTHLY -> 5
            YEARLY -> 15
        }

    companion object {
        /** All cycles ordered by interval length, used for detection priority */
        val detectOrder: List<RecurrenceCycle> = listOf(WEEKLY, BIWEEKLY, MONTHLY, YEARLY)
    }
}

/**
 * Amount variability classification for a recurring expense.
 */
enum class AmountPattern {
    /** Standard deviation < 5% of mean — exact same amount each time */
    FIXED,
    /** Standard deviation 5–30% of mean — similar but not identical */
    VARIABLE,
    /** Standard deviation > 30% of mean — highly variable */
    UNPREDICTABLE;

    fun displayName(): String = when (this) {
        FIXED -> "Fixed amount"
        VARIABLE -> "Varies slightly"
        UNPREDICTABLE -> "Variable"
    }

    companion object {
        /** Classify amount variability from coefficient of variation (SD/mean) */
        fun fromCoefficientOfVariation(cv: Double): AmountPattern = when {
            cv < 0.05 -> FIXED
            cv < 0.30 -> VARIABLE
            else -> UNPREDICTABLE
        }
    }
}

/**
 * Summary of all detected recurring expenses.
 * Top-level result from [com.pesatrack.services.RecurringExpenseService].
 */
data class RecurringExpenseSummary(
    /** Sum of monthly-equivalent amounts across all detected recurring expenses */
    val totalMonthlyRecurring: Double,
    /** Sum of monthly-equivalent amounts for FIXED-amount recurring expenses only */
    val fixedMonthlyTotal: Double,
    /** All detected recurring expenses, sorted by confidence descending */
    val recurringExpenses: List<RecurringExpense>,
    /** Recurring expenses expected within the next 7 days */
    val upcomingThisWeek: List<RecurringExpense>,
    /** Recurring expenses that are overdue (expected date passed, no payment found) */
    val overdueExpenses: List<RecurringExpense>
)

/**
 * Recurring expense info for a specific budget period.
 *
 * Used to separate recurring spending
 * from discretionary spending for more accurate burn rate projections.
 *
 * Example: In a monthly period where rent (KES 35,000) was paid on day 1 and
 * WiFi (KES 4,500) is expected on day 20:
 * - paidThisPeriod = 35,000
 * - upcomingThisPeriod = 4,500
 */
data class RecurringPeriodInfo(
    /** Total amount of recurring expenses already paid in the current period (KES) */
    val paidThisPeriod: Double,
    /** Total amount of recurring expenses expected but not yet paid in the current period (KES) */
    val upcomingThisPeriod: Double
) {
    /** Combined recurring total for the period (paid + upcoming) */
    val totalRecurringForPeriod: Double
        get() = paidThisPeriod + upcomingThisPeriod
}
