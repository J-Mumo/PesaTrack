package com.pesatrack.presentation.screens.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.domain.models.Category
import com.pesatrack.services.telemetry.TelemetryClient
import com.pesatrack.services.telemetry.TelemetryEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val telemetryClient: TelemetryClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ExpensesUiState())
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()
    
    private var categoriesMap: Map<Long, Category> = emptyMap()
    
    init {
        loadCategories()
        loadExpenses()
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
    
    private fun loadExpenses() {
        viewModelScope.launch {
            expenseRepository.getAllExpenses().collect { expenses ->
                val expensesWithCategory = expenses.map { expense ->
                    val category = expense.categoryId?.let { categoriesMap[it] }
                    ExpenseWithCategory(
                        expense = expense,
                        categoryName = category?.name,
                        categoryColor = category?.color
                    )
                }
                
                _uiState.update { 
                    it.copy(
                        expenses = expensesWithCategory,
                        isLoading = false
                    )
                }
            }
        }
        
        // Load total
        viewModelScope.launch {
            expenseRepository.getTotalForCurrentMonth().collect { total ->
                _uiState.update { it.copy(totalThisMonth = total) }
            }
        }
    }
    
    private fun refreshExpensesWithCategories() {
        val currentExpenses = _uiState.value.expenses
        val updated = currentExpenses.map { ewc ->
            val category = ewc.expense.categoryId?.let { categoriesMap[it] }
            ewc.copy(
                categoryName = category?.name,
                categoryColor = category?.color
            )
        }
        _uiState.update { it.copy(expenses = updated) }
    }
    
    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(expenseId)
            if (expense != null) {
                expenseRepository.deleteExpense(expense)
                telemetryClient.logEvent(TelemetryEvents.EXPENSE_DELETED)
            }
        }
    }

    /**
     * Toggle the isExcluded flag on an expense (pass-through money)
     */
    fun toggleExcluded(expenseId: Long, currentlyExcluded: Boolean) {
        viewModelScope.launch {
            expenseRepository.setExcluded(expenseId, !currentlyExcluded)
        }
    }

    /**
     * Update the client-side search query. Filtering happens in the screen
     * so the ViewModel doesn't have to hold a second list, but the query
     * itself lives here so it survives configuration changes.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}
