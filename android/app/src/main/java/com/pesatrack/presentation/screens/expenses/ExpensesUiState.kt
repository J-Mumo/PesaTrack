package com.pesatrack.presentation.screens.expenses

import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.Expense

/**
 * UI State for the Expenses screen
 */
data class ExpensesUiState(
    val isLoading: Boolean = true,
    val expenses: List<ExpenseWithCategory> = emptyList(),
    val totalThisMonth: Double = 0.0,
    val error: String? = null
)

/**
 * Expense with category info for display
 */
data class ExpenseWithCategory(
    val expense: Expense,
    val categoryName: String?,
    val categoryColor: String?
)
