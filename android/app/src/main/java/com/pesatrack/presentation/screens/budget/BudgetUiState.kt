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

    /** Available category options for the add/edit dialog (hierarchical: groups + sub-categories) */
    val availableCategories: List<BudgetCategoryOption> = emptyList(),

    /** Whether the add/edit dialog is visible */
    val showAddEditDialog: Boolean = false,

    /** Budget being edited (null = adding new) */
    val editingBudget: Budget? = null,

    /** Form fields for the add/edit dialog */
    val dialogCategoryId: Long? = null, // null = Total Spending
    val dialogIsGroupBudget: Boolean = true, // true = group-level, false = sub-category
    val dialogAmount: String = "",
    val dialogPeriod: BudgetPeriod = BudgetPeriod.MONTHLY,

    /** Confirmation dialog for delete */
    val showDeleteConfirmation: Boolean = false,
    val budgetToDelete: Budget? = null,

    val error: String? = null,
    val saveSuccess: Boolean = false
)

/**
 * Represents a category option in the budget picker.
 * Can be a "Total Spending" sentinel, a group, or a sub-category.
 */
data class BudgetCategoryOption(
    /** Category ID. Null = "Total Spending". */
    val id: Long?,
    /** Display name */
    val name: String,
    /** Hex colour */
    val color: String?,
    /** Whether this is a group (true) or sub-category (false). Null for "Total Spending". */
    val isGroup: Boolean?,
    /** Parent group ID for sub-categories. Null for groups and "Total Spending". */
    val parentGroupId: Long?,
    /** Whether a budget already exists for this category at the same level (prevents duplicates in UI) */
    val hasExistingBudget: Boolean
)
