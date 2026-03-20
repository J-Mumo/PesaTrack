package com.pesatrack.presentation.screens.analytics

import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.DailyTotal
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.database.dao.PaymentTypeTotal
import com.pesatrack.data.local.database.dao.TopSpender
import com.pesatrack.data.local.database.dao.YearMonthTotal
import com.pesatrack.domain.models.CategoryTrend
import com.pesatrack.domain.models.MonthComparison
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

    val error: String? = null
)
