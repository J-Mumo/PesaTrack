package com.pesatrack.presentation.screens.home

import com.pesatrack.domain.models.Expense

/**
 * UI State for the Home screen
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val totalThisMonth: Double = 0.0,
    val recentExpenses: List<Expense> = emptyList(),
    val uncategorizedCount: Int = 0,
    val error: String? = null
)
