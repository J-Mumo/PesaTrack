package com.pesatrack.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
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
    
    init {
        initializeData()
        loadData()
    }
    
    private fun initializeData() {
        viewModelScope.launch {
            // Initialize default categories
            categoryRepository.initializeDefaultCategories()
        }
    }
    
    private fun loadData() {
        // Load total for current month
        viewModelScope.launch {
            expenseRepository.getTotalForCurrentMonth().collect { total ->
                _uiState.update { it.copy(totalThisMonth = total) }
            }
        }
        
        // Load recent expenses
        viewModelScope.launch {
            expenseRepository.getExpensesForCurrentMonth()
                .map { expenses -> expenses.take(5) } // Show only 5 most recent
                .collect { expenses ->
                    _uiState.update { 
                        it.copy(
                            recentExpenses = expenses,
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
    
    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
    }
}
