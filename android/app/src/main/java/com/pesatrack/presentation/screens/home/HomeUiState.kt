package com.pesatrack.presentation.screens.home

import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.presentation.screens.expenses.ExpenseWithCategory

/**
 * UI State for the Home screen
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val totalThisMonth: Double = 0.0,
    val recentExpenses: List<ExpenseWithCategory> = emptyList(),
    val uncategorizedCount: Int = 0,
    val error: String? = null,
    /** Last 6 months spending trend for mini chart */
    val monthlyTrend: List<MonthlyTotal> = emptyList(),
    /** Month-over-month comparison for trend card */
    val monthComparison: MonthComparison? = null
)
