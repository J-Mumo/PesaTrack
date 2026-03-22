package com.pesatrack.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.BudgetRepository
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.presentation.screens.expenses.ExpenseWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var categoriesMap: Map<Long, Category> = emptyMap()
    
    init {
        initializeData()
        loadCategories()
        loadData()
        loadTrendData()
        loadBudgetData()
    }
    
    private fun initializeData() {
        viewModelScope.launch {
            // Initialize default categories
            categoryRepository.initializeDefaultCategories()
        }
    }
    
    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                categoriesMap = categories.associateBy { it.id }
                // Refresh expenses with updated category info
                refreshExpensesWithCategories()
            }
        }
    }
    
    private fun loadData() {
        // Load total for current month
        viewModelScope.launch {
            expenseRepository.getTotalForCurrentMonth().collect { total ->
                _uiState.update { it.copy(totalThisMonth = total) }
            }
        }

        // Load investment total for current month
        viewModelScope.launch {
            expenseRepository.getInvestmentTotalForCurrentMonth().collect { investmentTotal ->
                _uiState.update { it.copy(investmentThisMonth = investmentTotal) }
            }
        }
        
        // Load recent expenses with category info
        viewModelScope.launch {
            expenseRepository.getExpensesForCurrentMonth()
                .map { expenses ->
                    expenses.take(5).map { expense ->
                        val category = expense.categoryId?.let { categoriesMap[it] }
                        ExpenseWithCategory(
                            expense = expense,
                            categoryName = category?.name,
                            categoryColor = category?.color
                        )
                    }
                }
                .collect { expensesWithCategory ->
                    _uiState.update {
                        it.copy(
                            recentExpenses = expensesWithCategory,
                            isLoading = false
                        )
                    }
                }
        }
        
        // Load uncategorized count
        viewModelScope.launch {
            expenseRepository.getUncategorizedExpenses()
                .map { it.size }
                .collect { count ->
                    _uiState.update { it.copy(uncategorizedCount = count) }
                }
        }
    }

    /**
     * Load 6-month spending trend and month-over-month comparison
     */
    private fun loadTrendData() {
        viewModelScope.launch {
            try {
                val trend = expenseRepository.getMonthlyTotals(6)
                val filledTrend = fillMissingMonths(trend, 6)

                // Compute MoM comparison
                val now = Calendar.getInstance()
                val currentYear = now.get(Calendar.YEAR)
                val currentMonth = now.get(Calendar.MONTH) + 1
                val currentTotal = expenseRepository.getTotalForMonth(currentYear, currentMonth)

                val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                val prevYear = prevCal.get(Calendar.YEAR)
                val prevMonth = prevCal.get(Calendar.MONTH) + 1
                val prevTotal = expenseRepository.getTotalForMonth(prevYear, prevMonth)

                val pctChange = if (prevTotal > 0) {
                    ((currentTotal - prevTotal) / prevTotal) * 100.0
                } else if (currentTotal > 0) 100.0 else 0.0

                val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val comparison = MonthComparison(
                    currentMonthTotal = currentTotal,
                    previousMonthTotal = prevTotal,
                    percentageChange = pctChange,
                    currentMonthLabel = fmt.format(now.time),
                    previousMonthLabel = fmt.format(prevCal.time)
                )

                _uiState.update {
                    it.copy(monthlyTrend = filledTrend, monthComparison = comparison)
                }
            } catch (_: Exception) {
                // Silently fail — trend is non-critical
            }
        }
    }

    private fun fillMissingMonths(data: List<MonthlyTotal>, count: Int): List<MonthlyTotal> {
        val result = mutableListOf<MonthlyTotal>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -(count - 1))
        val existing = data.associateBy { it.monthKey }
        val fmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        repeat(count) {
            val key = fmt.format(cal.time)
            result.add(existing[key] ?: MonthlyTotal(monthKey = key, total = 0.0))
            cal.add(Calendar.MONTH, 1)
        }
        return result
    }
    
    private fun refreshExpensesWithCategories() {
        val currentExpenses = _uiState.value.recentExpenses
        val updated = currentExpenses.map { ewc ->
            val category = ewc.expense.categoryId?.let { categoriesMap[it] }
            ewc.copy(
                categoryName = category?.name,
                categoryColor = category?.color
            )
        }
        _uiState.update { it.copy(recentExpenses = updated) }
    }
    
    /**
     * Load budget progress (if budgets exist) or budget prompt (if conditions met).
     */
    private fun loadBudgetData() {
        viewModelScope.launch {
            try {
                val hasBudgets = budgetRepository.hasActiveBudgets()

                if (hasBudgets) {
                    // Show budget summary card
                    val progressList = budgetRepository.getBudgetProgressList()
                    _uiState.update {
                        it.copy(
                            budgetProgressList = progressList
                                .sortedByDescending { bp -> bp.percentage }
                                .take(4),
                            showBudgetPrompt = false
                        )
                    }
                } else {
                    // Check if we should show the budget prompt
                    _uiState.update { it.copy(budgetProgressList = emptyList()) }
                    checkBudgetPrompt()
                }
            } catch (_: Exception) {
                // Non-critical — silently fail
            }
        }
    }

    /**
     * Check if the data-driven budget prompt should be shown.
     * Conditions:
     * 1. No active budgets exist
     * 2. ≥20 categorized expenses
     * 3. User hasn't dismissed the prompt
     */
    private suspend fun checkBudgetPrompt() {
        try {
            val dismissed = appPreferences.isBudgetPromptDismissed()
            if (dismissed) {
                _uiState.update { it.copy(showBudgetPrompt = false) }
                return
            }

            val categorizedCount = budgetRepository.getCategorizedExpenseCount()
            if (categorizedCount < 20) {
                _uiState.update { it.copy(showBudgetPrompt = false) }
                return
            }

            // Get top spending category from last month
            val topGroup = budgetRepository.getTopSpendingGroupLastMonth()
            _uiState.update {
                it.copy(
                    showBudgetPrompt = true,
                    budgetPromptGroupId = topGroup?.first,
                    budgetPromptCategoryName = topGroup?.second,
                    budgetPromptAmount = topGroup?.third
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(showBudgetPrompt = false) }
        }
    }

    /**
     * Dismiss the budget prompt permanently.
     */
    fun dismissBudgetPrompt() {
        viewModelScope.launch {
            appPreferences.dismissBudgetPrompt()
            _uiState.update { it.copy(showBudgetPrompt = false) }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
        loadBudgetData()
    }
}
