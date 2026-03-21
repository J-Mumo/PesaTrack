package com.pesatrack.presentation.screens.budget

import com.pesatrack.domain.models.Budget
import com.pesatrack.domain.models.BudgetPeriod
import com.pesatrack.domain.models.BudgetProgress

/**
 * UI State for the Budget screen.
 */
data class BudgetUiState(
    val isLoading: Boolean = true,

    /** Active budgets with progress information */
    val budgetProgressList: List<BudgetProgress> = emptyList(),

    /** Available category groups for the add/edit dialog */
    val availableGroups: List<CategoryGroupOption> = emptyList(),

    /** Whether the add/edit dialog is visible */
    val showAddEditDialog: Boolean = false,

    /** Budget being edited (null = adding new) */
    val editingBudget: Budget? = null,

    /** Form fields for the add/edit dialog */
    val dialogCategoryGroupId: Long? = null, // null = Total Spending
    val dialogAmount: String = "",
    val dialogPeriod: BudgetPeriod = BudgetPeriod.MONTHLY,

    /** Confirmation dialog for delete */
    val showDeleteConfirmation: Boolean = false,
    val budgetToDelete: Budget? = null,

    val error: String? = null,
    val saveSuccess: Boolean = false
)

/**
 * Represents a category group option in the budget picker.
 */
data class CategoryGroupOption(
    /** Category group ID. Null = "Total Spending". */
    val id: Long?,
    /** Display name */
    val name: String,
    /** Hex colour */
    val color: String?,
    /** Whether a budget already exists for this group (prevents duplicates in UI) */
    val hasExistingBudget: Boolean
)
