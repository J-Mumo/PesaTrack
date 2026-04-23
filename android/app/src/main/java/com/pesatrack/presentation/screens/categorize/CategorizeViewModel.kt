package com.pesatrack.presentation.screens.categorize

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.domain.models.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategorizeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val expenseId: Long = savedStateHandle.get<Long>("expenseId") ?: 0L
    
    private val _uiState = MutableStateFlow(CategorizeUiState())
    val uiState: StateFlow<CategorizeUiState> = _uiState.asStateFlow()
    
    init {
        loadExpense()
        loadCategoryGroups()
    }
    
    private fun loadExpense() {
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(expenseId)
            _uiState.update { 
                it.copy(
                    expense = expense,
                    isLoading = false
                )
            }
            
            // Load selected category if expense has one
            expense?.categoryId?.let { categoryId ->
                val category = categoryRepository.getCategoryById(categoryId)
                _uiState.update { it.copy(selectedCategory = category) }
            }
        }
    }
    
    private fun loadCategoryGroups() {
        viewModelScope.launch {
            categoryRepository.getCategoryGroups().collect { groups ->
                _uiState.update { it.copy(categoryGroups = groups) }
            }
        }
    }
    
    fun selectCategory(category: Category) {
        _uiState.update { it.copy(selectedCategory = category) }
    }
    
    fun saveCategory() {
        val category = _uiState.value.selectedCategory ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            try {
                expenseRepository.updateCategory(expenseId, category.id)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }

                // Track categorization milestone and counter (fire-and-forget)
                launch {
                    appPreferences.recordFirstCategorization()
                    appPreferences.incrementCategorizationsCount()
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save category"
                    )
                }
            }
        }
    }
}
