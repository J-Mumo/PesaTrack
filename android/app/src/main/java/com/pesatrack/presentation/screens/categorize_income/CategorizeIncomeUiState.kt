package com.pesatrack.presentation.screens.categorize_income

import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.domain.models.IncomeTransaction

/**
 * UiState for [CategorizeIncomeScreen].
 *
 * Loaded once on entry from [com.pesatrack.data.repository.IncomeRepository.getById].
 * The user can change [selectedSource] / [isExcluded] before saving.
 */
data class CategorizeIncomeUiState(
    val isLoading: Boolean = true,
    val income: IncomeTransaction? = null,
    val selectedSource: IncomeSource = IncomeSource.UNCATEGORIZED,
    val isExcluded: Boolean = false,
    val isSaving: Boolean = false,
    /** True while the delete request is in-flight, so the button can spin and the confirm dialog can lock. */
    val isDeleting: Boolean = false,
    /** True after either save or delete completes — the screen listens on this to pop back. */
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)
