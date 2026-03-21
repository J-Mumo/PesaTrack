package com.pesatrack.presentation.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.BudgetRepository
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.domain.models.Budget
import com.pesatrack.domain.models.BudgetPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        loadBudgets()
        loadCategoryGroups()
    }

    /**
     * Load active budgets with progress data.
     */
    private fun loadBudgets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val progressList = budgetRepository.getBudgetProgressList()
                _uiState.update {
                    it.copy(
                        budgetProgressList = progressList.sortedByDescending { bp -> bp.percentage },
                        isLoading = false,
                        error = null
                    )
                }
                // Update available groups (mark which ones have budgets)
                updateAvailableGroups()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load budgets")
                }
            }
        }
    }

    /**
     * Load category groups for the add/edit picker.
     */
    private fun loadCategoryGroups() {
        viewModelScope.launch {
            categoryRepository.getGroups().collect { groups ->
                updateAvailableGroups()
            }
        }
    }

    /**
     * Update available groups, marking which ones already have budgets.
     */
    private fun updateAvailableGroups() {
        viewModelScope.launch {
            try {
                categoryRepository.getGroups().collect { groups ->
                    val existingBudgetGroupIds = _uiState.value.budgetProgressList
                        .map { it.budget.categoryGroupId }
                        .toSet()

                    val options = mutableListOf<CategoryGroupOption>()

                    // "Total Spending" option first
                    options.add(
                        CategoryGroupOption(
                            id = null,
                            name = "Total Spending",
                            color = null,
                            hasExistingBudget = null in existingBudgetGroupIds
                        )
                    )

                    // Category groups
                    for (group in groups) {
                        options.add(
                            CategoryGroupOption(
                                id = group.id,
                                name = group.name,
                                color = group.color,
                                hasExistingBudget = group.id in existingBudgetGroupIds
                            )
                        )
                    }

                    _uiState.update { it.copy(availableGroups = options) }
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    // ==================== Dialog Actions ====================

    /**
     * Show the add budget dialog.
     * Optionally pre-select a category group (for smart prompt).
     */
    fun showAddDialog(preSelectedGroupId: Long? = null) {
        _uiState.update {
            it.copy(
                showAddEditDialog = true,
                editingBudget = null,
                dialogCategoryGroupId = preSelectedGroupId,
                dialogAmount = "",
                dialogPeriod = BudgetPeriod.MONTHLY,
                saveSuccess = false
            )
        }
    }

    /**
     * Show the edit dialog for an existing budget.
     */
    fun showEditDialog(budget: Budget) {
        _uiState.update {
            it.copy(
                showAddEditDialog = true,
                editingBudget = budget,
                dialogCategoryGroupId = budget.categoryGroupId,
                dialogAmount = budget.amount.toLong().toString(),
                dialogPeriod = budget.period,
                saveSuccess = false
            )
        }
    }

    /**
     * Dismiss the add/edit dialog.
     */
    fun dismissDialog() {
        _uiState.update {
            it.copy(
                showAddEditDialog = false,
                editingBudget = null,
                error = null,
                saveSuccess = false
            )
        }
    }

    /**
     * Update form fields.
     */
    fun updateDialogCategoryGroupId(id: Long?) {
        _uiState.update { it.copy(dialogCategoryGroupId = id) }
    }

    fun updateDialogAmount(amount: String) {
        _uiState.update { it.copy(dialogAmount = amount) }
    }

    fun updateDialogPeriod(period: BudgetPeriod) {
        _uiState.update { it.copy(dialogPeriod = period) }
    }

    /**
     * Save the budget (create or update).
     */
    fun saveBudget() {
        val state = _uiState.value
        val amountStr = state.dialogAmount.replace(",", "").trim()
        val amount = amountStr.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(error = "Enter a valid amount") }
            return
        }

        viewModelScope.launch {
            try {
                val existing = state.editingBudget
                if (existing != null) {
                    // Update existing budget
                    budgetRepository.updateBudget(
                        existing.copy(
                            amount = amount,
                            period = state.dialogPeriod,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    // Create new budget
                    budgetRepository.saveBudget(
                        Budget(
                            categoryGroupId = state.dialogCategoryGroupId,
                            categoryGroupName = null, // will be resolved on reload
                            categoryGroupColor = null,
                            amount = amount,
                            period = state.dialogPeriod
                        )
                    )
                }

                _uiState.update {
                    it.copy(
                        showAddEditDialog = false,
                        editingBudget = null,
                        saveSuccess = true,
                        error = null
                    )
                }

                // Reload budgets
                loadBudgets()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save budget") }
            }
        }
    }

    // ==================== Delete ====================

    /**
     * Show delete confirmation dialog.
     */
    fun showDeleteConfirmation(budget: Budget) {
        _uiState.update {
            it.copy(showDeleteConfirmation = true, budgetToDelete = budget)
        }
    }

    /**
     * Dismiss delete confirmation.
     */
    fun dismissDeleteConfirmation() {
        _uiState.update {
            it.copy(showDeleteConfirmation = false, budgetToDelete = null)
        }
    }

    /**
     * Confirm delete.
     */
    fun confirmDelete() {
        val budget = _uiState.value.budgetToDelete ?: return
        viewModelScope.launch {
            try {
                budgetRepository.deleteBudget(budget)
                _uiState.update {
                    it.copy(showDeleteConfirmation = false, budgetToDelete = null)
                }
                loadBudgets()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete budget") }
            }
        }
    }

    /**
     * Refresh budgets (called when returning to screen).
     */
    fun refresh() {
        loadBudgets()
    }
}
