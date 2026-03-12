package com.pesatrack.presentation.screens.batch_categorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.database.dao.RecipientGroup
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Batch Categorize screen.
 *
 * Supports two modes per recipient group:
 * - Quick mode: tap recipient → pick category → apply to ALL transactions
 * - Review mode: expand recipient → see individual transactions → override per-transaction
 *
 * Saves recipient→category mappings for future auto-categorization.
 * Multi-category mappings are supported: one recipient can map to multiple categories.
 */
@HiltViewModel
class BatchCategorizeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val recipientMappingRepository: RecipientMappingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchCategorizeUiState())
    val uiState: StateFlow<BatchCategorizeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        // Load uncategorized groups
        viewModelScope.launch {
            try {
                val groups = expenseRepository.getUncategorizedGroupedByRecipient()
                _uiState.update {
                    it.copy(
                        recipientGroups = groups,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load expenses"
                    )
                }
            }
        }

        // Load category groups
        viewModelScope.launch {
            categoryRepository.getCategoryGroups().collect { groups ->
                _uiState.update { it.copy(categoryGroups = groups) }
            }
        }
    }

    // ==================== Quick Mode (Apply to All) ====================

    /**
     * Open the category picker for a specific recipient group (quick mode).
     * This applies the chosen category to ALL expenses from that recipient.
     */
    fun selectRecipientGroup(group: RecipientGroup) {
        _uiState.update {
            it.copy(
                selectedRecipientGroup = group,
                showCategoryPicker = true
            )
        }
    }

    /**
     * Close the category picker (quick mode)
     */
    fun dismissCategoryPicker() {
        _uiState.update {
            it.copy(
                selectedRecipientGroup = null,
                showCategoryPicker = false
            )
        }
    }

    /**
     * Apply a category to ALL expenses from the selected recipient (quick mode).
     * Saves multiple categories per recipient in the mapping table.
     */
    fun applyCategory(category: Category) {
        val recipientGroup = _uiState.value.selectedRecipientGroup ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                // 1. Bulk update expenses by recipient
                val recipientName = recipientGroup.recipientName
                val recipient = recipientGroup.recipient
                var updated = 0

                if (!recipientName.isNullOrBlank()) {
                    updated = expenseRepository.updateCategoryByRecipientName(
                        recipientName, category.id
                    )
                }
                // Also try by recipient field (phone/till) if recipientName didn't match all
                if (recipient.isNotBlank()) {
                    updated += expenseRepository.updateCategoryByRecipient(
                        recipient, category.id
                    )
                }

                // 2. Save the recipient→category mapping (multi-category aware)
                val mappingKey = recipientName ?: recipient
                recipientMappingRepository.saveMapping(
                    recipientKey = mappingKey,
                    categoryId = category.id,
                    displayName = recipientName
                )

                // Also save the alternate key if both exist
                if (!recipientName.isNullOrBlank() && recipient.isNotBlank() && recipientName != recipient) {
                    recipientMappingRepository.saveMapping(
                        recipientKey = recipient,
                        categoryId = category.id,
                        displayName = recipientName
                    )
                }

                // 3. Refresh the list
                val remainingGroups = expenseRepository.getUncategorizedGroupedByRecipient()

                _uiState.update {
                    it.copy(
                        recipientGroups = remainingGroups,
                        selectedRecipientGroup = null,
                        showCategoryPicker = false,
                        isSaving = false,
                        categorizedCount = it.categorizedCount + 1
                    )
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

    // ==================== Review Mode (Individual Override) ====================

    /**
     * Toggle expanded/collapsed state for a recipient group.
     * When expanding, loads individual expenses for that group.
     */
    fun toggleExpandGroup(group: RecipientGroup) {
        val currentExpanded = _uiState.value.expandedGroupKey
        if (currentExpanded == group.recipientKey) {
            // Collapse
            _uiState.update {
                it.copy(
                    expandedGroupKey = null,
                    expandedGroupExpenses = emptyList()
                )
            }
        } else {
            // Expand — load individual expenses
            _uiState.update {
                it.copy(
                    expandedGroupKey = group.recipientKey,
                    isLoadingExpanded = true
                )
            }
            viewModelScope.launch {
                try {
                    val expenses = expenseRepository.getUncategorizedByRecipientKey(
                        group.recipientKey
                    )
                    _uiState.update {
                        it.copy(
                            expandedGroupExpenses = expenses,
                            isLoadingExpanded = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoadingExpanded = false,
                            error = e.message ?: "Failed to load expenses"
                        )
                    }
                }
            }
        }
    }

    /**
     * Open category picker for a single expense (individual override in review mode).
     */
    fun selectExpenseForCategorize(expenseId: Long) {
        _uiState.update {
            it.copy(
                selectedExpenseId = expenseId,
                showIndividualCategoryPicker = true
            )
        }
    }

    /**
     * Close category picker for individual expense
     */
    fun dismissIndividualCategoryPicker() {
        _uiState.update {
            it.copy(
                selectedExpenseId = null,
                showIndividualCategoryPicker = false
            )
        }
    }

    /**
     * Apply a category to a single expense (individual override in review mode).
     * Also learns the recipient→category mapping.
     */
    fun applyCategoryToExpense(expenseId: Long, category: Category) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                // 1. Update the single expense
                expenseRepository.updateCategory(expenseId, category.id)

                // 2. Find the expense to learn the mapping
                val expense = expenseRepository.getExpenseById(expenseId)
                if (expense != null) {
                    val mappingKey = expense.recipientName ?: expense.recipient
                    recipientMappingRepository.saveMapping(
                        recipientKey = mappingKey,
                        categoryId = category.id,
                        displayName = expense.recipientName
                    )
                }

                // 3. Refresh expanded group and groups list
                val expandedKey = _uiState.value.expandedGroupKey
                val updatedExpenses = if (expandedKey != null) {
                    expenseRepository.getUncategorizedByRecipientKey(expandedKey)
                } else emptyList()

                val remainingGroups = expenseRepository.getUncategorizedGroupedByRecipient()

                // If the expanded group is now empty, collapse it
                val shouldCollapse = updatedExpenses.isEmpty()

                _uiState.update {
                    it.copy(
                        recipientGroups = remainingGroups,
                        expandedGroupExpenses = updatedExpenses,
                        expandedGroupKey = if (shouldCollapse) null else it.expandedGroupKey,
                        selectedExpenseId = null,
                        showIndividualCategoryPicker = false,
                        isSaving = false,
                        individualCategorizedCount = it.individualCategorizedCount + 1
                    )
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

    // ==================== General ====================

    /**
     * Refresh the data
     */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
    }

    /**
     * Dismiss error
     */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
