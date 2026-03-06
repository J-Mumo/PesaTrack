package com.pesatrack.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.domain.models.Category
import com.pesatrack.presentation.screens.expenses.ExpenseWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var categoriesMap: Map<Long, Category> = emptyMap()
    
    init {
        initializeData()
        loadCategories()
        loadData()
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
    
    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
    }
}
