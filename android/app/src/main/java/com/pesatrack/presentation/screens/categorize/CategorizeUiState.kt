package com.pesatrack.presentation.screens.categorize

import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.CategoryGroup
import com.pesatrack.domain.models.Expense

/**
 * UI State for the Categorize screen
 */
data class CategorizeUiState(
    val isLoading: Boolean = true,
    val expense: Expense? = null,
    val categoryGroups: List<CategoryGroup> = emptyList(),
    val selectedCategory: Category? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)
