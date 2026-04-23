package com.pesatrack.presentation.screens.analytics

import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.DailyTotal
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.database.dao.PaymentTypeTotal
import com.pesatrack.data.local.database.dao.TopSpender
import com.pesatrack.data.local.database.dao.YearMonthTotal
import com.pesatrack.domain.models.CategoryTrend
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.domain.models.RecurringExpense
import com.pesatrack.domain.models.YearComparison

/**
 * Tab selection for the Analytics screen
 */
enum class AnalyticsTab {
    MONTHLY,
    YEARLY
}

/**
 * UI State for the Analytics screen
 */
data class AnalyticsUiState(
    val isLoading: Boolean = true,

    /** Selected tab: Monthly or Yearly */
    val selectedTab: AnalyticsTab = AnalyticsTab.MONTHLY,

    // ==================== Monthly Tab ====================

    /** Selected month (1-based) and year for filtering */
    val selectedYear: Int = 0,
    val selectedMonth: Int = 0,
    val selectedMonthLabel: String = "",

    // Chart data
    val monthlyTrend: List<MonthlyTotal> = emptyList(),
    val categoryBreakdown: List<CategoryTotal> = emptyList(),
    val dailySpending: List<DailyTotal> = emptyList(),
    val topSpenders: List<TopSpender> = emptyList(),
    val paymentTypeBreakdown: List<PaymentTypeTotal> = emptyList(),

    // Variable-spend category trends (CV-detected)
    val categoryTrends: List<CategoryTrend> = emptyList(),

    // Month-over-month comparison
    val monthComparison: MonthComparison? = null,

    // Summary stats for selected month
    val totalForMonth: Double = 0.0,
    val transactionCountForMonth: Int = 0,
    val avgDailySpend: Double = 0.0,

    // ==================== Yearly Tab ====================

    /** Selected year for yearly analytics */
    val selectedYearForYearly: Int = 0,
    val yearlyIsLoading: Boolean = false,

    // YoY comparison
    val yearComparison: YearComparison? = null,

    // Yearly summary stats
    val yearlyTotalForYear: Double = 0.0,
    val yearlyTransactionCount: Int = 0,
    val yearlyAvgMonthlySpend: Double = 0.0,

    // 12-month overlay chart data (current year vs previous year)
    val currentYearMonthlyTotals: List<YearMonthTotal> = emptyList(),
    val previousYearMonthlyTotals: List<YearMonthTotal> = emptyList(),

    // Yearly breakdowns (reuse same types as monthly)
    val yearlyCategoryBreakdown: List<CategoryTotal> = emptyList(),
    val yearlyTopSpenders: List<TopSpender> = emptyList(),
    val yearlyPaymentTypeBreakdown: List<PaymentTypeTotal> = emptyList(),

    // ==================== Recipient Search ====================

    /** Current search query for recipient lookup (shared between monthly/yearly tabs) */
    val recipientSearchQuery: String = "",

    /** Search results for the monthly tab (null = not searching, empty = no matches) */
    val recipientSearchResults: List<TopSpender>? = null,

    /** Aggregate total across all monthly search results */
    val recipientSearchTotal: Double = 0.0,

    /** Search results for the yearly tab (null = not searching, empty = no matches) */
    val yearlyRecipientSearchResults: List<TopSpender>? = null,

    /** Aggregate total across all yearly search results */
    val yearlyRecipientSearchTotal: Double = 0.0,

    /** Whether a search is currently loading */
    val recipientSearchLoading: Boolean = false,

    // ==================== Recurring Expense Detection ====================

    /** Total recurring spending for the selected month (KES) */
    val recurringTotal: Double = 0.0,
    /** Total one-time (non-recurring) spending for the selected month (KES) */
    val oneTimeTotal: Double = 0.0,
    /** Top recurring expense names for display (e.g. "Rent, SACCO, WiFi") */
    val topRecurringNames: String = "",
    /** Whether recurring data has been loaded (hides the card until ready) */
    val hasRecurringData: Boolean = false,

    // ==================== Budget Integration ====================

    /** Whether the user has any active budgets (used to show/hide budget banner) */
    val hasActiveBudgets: Boolean = false,

    // ==================== Forecast Projection ====================

    /**
     * Projected cumulative daily spending from today to month-end.
     * Each entry: day number (1-based) → projected cumulative total.
     * Only populated when viewing the current month and total budget exists.
     */
    val projectionLine: List<DailyTotal> = emptyList(),

    /** Budget ceiling value for the total budget (null if no total budget exists) */
    val budgetCeiling: Double? = null,

    val error: String? = null
)
