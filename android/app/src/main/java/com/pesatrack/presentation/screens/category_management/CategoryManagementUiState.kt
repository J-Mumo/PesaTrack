package com.pesatrack.presentation.screens.category_management

import com.pesatrack.data.local.database.entities.CategoryRuleEntity
import com.pesatrack.data.local.database.entities.RuleMatchType
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.CategoryGroup

/**
 * UI state for the Category Management screen
 */
data class CategoryManagementUiState(
    val isLoading: Boolean = true,
    val categoryGroups: List<CategoryGroup> = emptyList(),
    val rules: List<CategoryRuleEntity> = emptyList(),
    val allCategories: List<Category> = emptyList(),

    // Dialog state
    val dialogState: CategoryDialogState = CategoryDialogState.Hidden,

    // Snackbar
    val message: String? = null
)

/**
 * Represents the state of the add/edit dialog
 */
sealed class CategoryDialogState {
    object Hidden : CategoryDialogState()

    /** Adding a new sub-category under a group */
    data class AddSubCategory(
        val parentGroup: Category
    ) : CategoryDialogState()

    /** Adding a new top-level group */
    object AddGroup : CategoryDialogState()

    /** Editing an existing category or group */
    data class EditCategory(
        val category: Category
    ) : CategoryDialogState()

    /** Confirming deletion of a category */
    data class ConfirmDelete(
        val category: Category,
        val expenseCount: Int
    ) : CategoryDialogState()

    /** Adding a new auto-categorization rule */
    data class AddRule(
        val preSelectedCategoryId: Long? = null
    ) : CategoryDialogState()

    /** Editing an existing rule */
    data class EditRule(
        val rule: CategoryRuleEntity
    ) : CategoryDialogState()

    /** Confirming deletion of a rule */
    data class ConfirmDeleteRule(
        val rule: CategoryRuleEntity
    ) : CategoryDialogState()
}

/**
 * Form data for add/edit category dialog
 */
data class CategoryFormData(
    val name: String = "",
    val icon: String = "category",
    val color: String = "#9E9E9E"
)

/**
 * Form data for add/edit rule dialog
 */
data class RuleFormData(
    val pattern: String = "",
    val matchType: RuleMatchType = RuleMatchType.CONTAINS,
    val categoryId: Long? = null,
    val priority: Int = 0,
    val isActive: Boolean = true
)
