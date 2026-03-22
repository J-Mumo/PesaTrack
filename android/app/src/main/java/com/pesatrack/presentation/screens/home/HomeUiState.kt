package com.pesatrack.presentation.screens.home

import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.domain.models.BudgetProgress
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.presentation.screens.expenses.ExpenseWithCategory

/**
 * UI State for the Home screen
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val totalThisMonth: Double = 0.0,
    /** Total invested this month (Investment & Savings group 18) */
    val investmentThisMonth: Double = 0.0,
    val recentExpenses: List<ExpenseWithCategory> = emptyList(),
    val uncategorizedCount: Int = 0,
    val error: String? = null,
    /** Last 6 months spending trend for mini chart */
    val monthlyTrend: List<MonthlyTotal> = emptyList(),
    /** Month-over-month comparison for trend card */
    val monthComparison: MonthComparison? = null,

    // ==================== Budget ====================

    /** Top budget progress items (sorted by % used, max 4) — shown when user has budgets */
    val budgetProgressList: List<BudgetProgress> = emptyList(),

    /** Whether to show the data-driven budget setup prompt (no budgets + ≥20 categorized expenses + not dismissed) */
    val showBudgetPrompt: Boolean = false,
    /** Top spending category name for the prompt (e.g. "Food & Dining") */
    val budgetPromptCategoryName: String? = null,
    /** Top spending amount for the prompt (e.g. 14200.0) */
    val budgetPromptAmount: Double? = null,
    /** Category group ID to pre-select when navigating to budget screen from prompt */
    val budgetPromptGroupId: Long? = null
)
