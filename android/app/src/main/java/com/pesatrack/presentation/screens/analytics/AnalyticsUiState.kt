package com.pesatrack.presentation.screens.analytics

import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.DailyTotal
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.database.dao.PaymentTypeTotal
import com.pesatrack.data.local.database.dao.TopSpender
import com.pesatrack.domain.models.CategoryTrend
import com.pesatrack.domain.models.MonthComparison

/**
 * UI State for the Analytics screen
 */
data class AnalyticsUiState(
    val isLoading: Boolean = true,

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

    val error: String? = null
)
