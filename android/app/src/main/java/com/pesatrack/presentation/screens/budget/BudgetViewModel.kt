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
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    /** Calendar reference for the currently selected period position. */
    private var periodCalendar: Calendar = Calendar.getInstance()

    init {
        // Initialize period labels from the current calendar
        updatePeriodLabels()
        loadBudgetsForPeriod()
        loadCategoryOptions()
        loadIncome()
    }

    // ==================== Period Navigation ====================

    /**
     * Switch the period type tab (Weekly/Monthly/Yearly/Custom).
     * Resets the calendar to "now" for the new period type and reloads.
     * For CUSTOM, restores the most recent custom date range from the database.
     */
    fun setPeriodType(type: BudgetPeriod) {
        periodCalendar = Calendar.getInstance()
        _uiState.update {
            it.copy(
                selectedPeriodType = type,
                // Clear custom dates when switching away from custom tab
                customStartDate = if (type == BudgetPeriod.CUSTOM) it.customStartDate else null,
                customEndDate = if (type == BudgetPeriod.CUSTOM) it.customEndDate else null
            )
        }

        if (type == BudgetPeriod.CUSTOM) {
            // Restore custom date range from existing custom budgets in the database
            restoreCustomDateRange()
        } else {
            updatePeriodLabels()
            loadBudgetsForPeriod()
            loadIncome()
        }
    }

    /**
     * Restore the custom date range from the most recent custom budget in the database.
     * Called when switching to Custom tab or when ViewModel is initialized with Custom period.
     */
    private fun restoreCustomDateRange() {
        viewModelScope.launch {
            try {
                val dateRange = budgetRepository.getMostRecentCustomDateRange()
                if (dateRange != null) {
                    _uiState.update {
                        it.copy(
                            customStartDate = dateRange.first,
                            customEndDate = dateRange.second
                        )
                    }
                }
            } catch (_: Exception) {
                // Non-critical
            }
            // Always update labels and load budgets after restoring
            updatePeriodLabels()
            loadBudgetsForPeriod()
            loadIncome()
        }
    }

    /**
     * Navigate forward or backward within the selected period type.
     * Not applicable for CUSTOM periods.
     * @param delta +1 for next period, -1 for previous period
     */
    fun navigatePeriod(delta: Int) {
        val currentType = _uiState.value.selectedPeriodType
        if (currentType == BudgetPeriod.CUSTOM) return // No navigation for custom
        periodCalendar = budgetRepository.navigateCalendar(currentType, periodCalendar, delta)
        updatePeriodLabels()
        loadBudgetsForPeriod()
        loadIncome()
    }

    /**
     * Update the period label and key in UI state from the current calendar + period type.
     */
    private fun updatePeriodLabels() {
        val type = _uiState.value.selectedPeriodType
        val customStart = _uiState.value.customStartDate
        val customEnd = _uiState.value.customEndDate
        val label = budgetRepository.getPeriodLabel(type, periodCalendar, customStart, customEnd)
        val key = budgetRepository.getPeriodKey(type, periodCalendar, customStart, customEnd)
        _uiState.update {
            it.copy(
                selectedPeriodLabel = label,
                selectedPeriodKey = key
            )
        }
    }

    // ==================== Custom Period ====================

    /**
     * Set the custom start date.
     */
    fun setCustomStartDate(millis: Long) {
        _uiState.update { it.copy(customStartDate = millis) }
        updatePeriodLabels()
        loadBudgetsForPeriod()
        loadIncome()
    }

    /**
     * Set the custom end date.
     */
    fun setCustomEndDate(millis: Long) {
        _uiState.update { it.copy(customEndDate = millis) }
        updatePeriodLabels()
        loadBudgetsForPeriod()
        loadIncome()
    }

    // ==================== Data Loading ====================

    /**
     * Load active budgets filtered by the selected period type, with progress data.
     * Passes periodCalendar so the repository uses the correct date range.
     */
    private fun loadBudgetsForPeriod() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val periodType = _uiState.value.selectedPeriodType
                val progressList = budgetRepository.getBudgetProgressListForPeriod(
                    periodType,
                    periodCalendar
                )
                val totalBudgeted = budgetRepository.getTotalBudgetedForPeriod(periodType)
                _uiState.update {
                    it.copy(
                        budgetProgressList = progressList.sortedByDescending { bp -> bp.percentage },
                        totalBudgeted = totalBudgeted,
                        isLoading = false,
                        error = null
                    )
                }
                // Update available categories (mark which ones have budgets for this period)
                updateAvailableCategories()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load budgets")
                }
            }
        }
    }

    /**
     * Load income for the selected period key.
     */
    private fun loadIncome() {
        viewModelScope.launch {
            try {
                val periodKey = _uiState.value.selectedPeriodKey
                if (periodKey.isBlank()) return@launch
                val income = budgetRepository.getMonthlyIncome(periodKey)
                _uiState.update {
                    it.copy(monthlyIncome = income)
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    /**
     * Load category groups and their sub-categories for the add/edit picker.
     */
    private fun loadCategoryOptions() {
        viewModelScope.launch {
            categoryRepository.getGroups().collect { _ ->
                updateAvailableCategories()
            }
        }
    }

    /**
     * Update available categories, marking which ones already have budgets.
     * Builds a hierarchical list: Group1 → Sub1a, Sub1b, ... → Group2 → ...
     * No "Total Spending" sentinel option.
     */
    private fun updateAvailableCategories() {
        viewModelScope.launch {
            try {
                categoryRepository.getGroups().collect { groups ->
                    val existingBudgets = _uiState.value.budgetProgressList
                        .map { Pair(it.budget.categoryId, it.budget.isGroupBudget) }
                        .toSet()

                    val options = mutableListOf<BudgetCategoryOption>()

                    // Category groups + their sub-categories (no "Total Spending")
                    for (group in groups) {
                        // Add group-level option
                        options.add(
                            BudgetCategoryOption(
                                id = group.id,
                                name = group.name,
                                color = group.color,
                                isGroup = true,
                                parentGroupId = null,
                                hasExistingBudget = Pair(group.id, true) in existingBudgets
                            )
                        )

                        // Add sub-category options under this group
                        val subCategories = categoryRepository.getSubCategoriesSync(group.id)
                        for (sub in subCategories) {
                            options.add(
                                BudgetCategoryOption(
                                    id = sub.id,
                                    name = sub.name,
                                    color = sub.color,
                                    isGroup = false,
                                    parentGroupId = group.id,
                                    hasExistingBudget = Pair(sub.id, false) in existingBudgets
                                )
                            )
                        }
                    }

                    _uiState.update { it.copy(availableCategories = options) }
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    // ==================== Income Dialog ====================

    /**
     * Show the income dialog, pre-filling with current income if set.
     */
    fun showIncomeDialog() {
        val currentIncome = _uiState.value.monthlyIncome
        _uiState.update {
            it.copy(
                showIncomeDialog = true,
                dialogIncomeAmount = currentIncome?.toLong()?.toString() ?: ""
            )
        }
    }

    /**
     * Dismiss the income dialog.
     */
    fun dismissIncomeDialog() {
        _uiState.update { it.copy(showIncomeDialog = false) }
    }

    /**
     * Update the income dialog amount field.
     */
    fun updateIncomeAmount(amount: String) {
        _uiState.update { it.copy(dialogIncomeAmount = amount) }
    }

    /**
     * Save the income for the selected period key.
     */
    fun saveIncome() {
        val amountStr = _uiState.value.dialogIncomeAmount.replace(",", "").trim()
        val amount = amountStr.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(error = "Enter a valid income amount") }
            return
        }

        viewModelScope.launch {
            try {
                val periodKey = _uiState.value.selectedPeriodKey
                budgetRepository.setMonthlyIncome(periodKey, amount)
                _uiState.update {
                    it.copy(
                        monthlyIncome = amount,
                        showIncomeDialog = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save income") }
            }
        }
    }

    // ==================== Dialog Actions ====================

    /**
     * Show the add budget dialog.
     * Period is inherited from the selected period type — no dialogPeriod field.
     * Optionally pre-select a category (for smart prompt).
     */
    fun showAddDialog(preSelectedCategoryId: Long? = null, isGroupBudget: Boolean = true) {
        _uiState.update {
            it.copy(
                showAddEditDialog = true,
                editingBudget = null,
                dialogCategoryId = preSelectedCategoryId,
                dialogIsGroupBudget = isGroupBudget,
                dialogAmount = "",
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
                dialogCategoryId = budget.categoryId,
                dialogIsGroupBudget = budget.isGroupBudget,
                dialogAmount = budget.amount.toLong().toString(),
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
    fun updateDialogCategory(id: Long?, isGroup: Boolean) {
        _uiState.update { it.copy(dialogCategoryId = id, dialogIsGroupBudget = isGroup) }
    }

    fun updateDialogAmount(amount: String) {
        _uiState.update { it.copy(dialogAmount = amount) }
    }

    /**
     * Save the budget (create or update).
     * Period is taken from the selected period type.
     * For CUSTOM periods, customStartDate and customEndDate are stored on the budget.
     */
    fun saveBudget() {
        val state = _uiState.value
        val amountStr = state.dialogAmount.replace(",", "").trim()
        val amount = amountStr.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(error = "Enter a valid amount") }
            return
        }

        // Category is required (no "Total Spending" option)
        if (state.dialogCategoryId == null) {
            _uiState.update { it.copy(error = "Select a category") }
            return
        }

        // For CUSTOM period, validate date range
        if (state.selectedPeriodType == BudgetPeriod.CUSTOM) {
            if (state.customStartDate == null || state.customEndDate == null) {
                _uiState.update { it.copy(error = "Set both start and end dates for custom period") }
                return
            }
            if (state.customStartDate >= state.customEndDate) {
                _uiState.update { it.copy(error = "End date must be after start date") }
                return
            }
        }

        viewModelScope.launch {
            try {
                val existing = state.editingBudget
                val period = state.selectedPeriodType

                if (existing != null) {
                    // Update existing budget
                    budgetRepository.updateBudget(
                        existing.copy(
                            amount = amount,
                            period = period,
                            customStartDate = if (period == BudgetPeriod.CUSTOM) state.customStartDate else null,
                            customEndDate = if (period == BudgetPeriod.CUSTOM) state.customEndDate else null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    // Create new budget
                    budgetRepository.saveBudget(
                        Budget(
                            categoryId = state.dialogCategoryId,
                            categoryName = null, // will be resolved on reload
                            categoryColor = null,
                            isGroupBudget = state.dialogIsGroupBudget,
                            amount = amount,
                            period = period,
                            customStartDate = if (period == BudgetPeriod.CUSTOM) state.customStartDate else null,
                            customEndDate = if (period == BudgetPeriod.CUSTOM) state.customEndDate else null
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

                // Reload budgets and income allocation
                loadBudgetsForPeriod()
                loadIncome()
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
                loadBudgetsForPeriod()
                loadIncome()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete budget") }
            }
        }
    }

    /**
     * Refresh budgets (called when returning to screen).
     */
    fun refresh() {
        loadBudgetsForPeriod()
        loadIncome()
    }
}
