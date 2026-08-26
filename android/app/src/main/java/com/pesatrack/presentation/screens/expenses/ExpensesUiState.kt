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
    /**
     * Free-text search query applied client-side across recipient / category /
     * notes / amount. Empty string means "show everything" and mirrors the
     * un-filtered list. Kept on the ViewModel so screen rotation doesn't drop
     * the query.
     */
    val searchQuery: String = "",
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
