package com.pesatrack.presentation.screens.home

import com.pesatrack.presentation.screens.expenses.ExpenseWithCategory

/**
 * UI State for the Home screen
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val totalThisMonth: Double = 0.0,
    val recentExpenses: List<ExpenseWithCategory> = emptyList(),
    val uncategorizedCount: Int = 0,
    val error: String? = null
)
