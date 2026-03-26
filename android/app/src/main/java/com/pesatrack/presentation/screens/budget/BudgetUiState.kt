package com.pesatrack.presentation.screens.budget

import com.pesatrack.domain.models.Budget
import com.pesatrack.domain.models.BudgetPeriod
import com.pesatrack.domain.models.BudgetProgress

/**
 * UI State for the Budget screen (period-first flow).
 *
 * The screen is organized around a selected period: the user picks a period type
 * (Weekly/Monthly/Yearly/Custom) and navigates forward/backward. All budgets and income
 * are shown for that selected period.
 */
data class BudgetUiState(
    val isLoading: Boolean = true,

    // ==================== Period Navigation ====================

    /** Currently selected period type tab */
    val selectedPeriodType: BudgetPeriod = BudgetPeriod.MONTHLY,

    /** Human-readable label for the selected period, e.g. "March 2026" */
    val selectedPeriodLabel: String = "",

    /** Key string used for income lookup, e.g. "2026-03", "2026-W13", "2026", "custom-xxx-yyy" */
    val selectedPeriodKey: String = "",

    // ==================== Custom Period ====================

    /** Start date millis for custom period (null = not set) */
    val customStartDate: Long? = null,

    /** End date millis for custom period (null = not set) */
    val customEndDate: Long? = null,

    /** Whether the date picker for custom start is showing */
    val showCustomStartPicker: Boolean = false,

    /** Whether the date picker for custom end is showing */
    val showCustomEndPicker: Boolean = false,

    // ==================== Budgets ====================

    /** Active budgets with progress information, filtered to the selected period type */
    val budgetProgressList: List<BudgetProgress> = emptyList(),

    /** Available category options for the add/edit dialog (hierarchical: groups + sub-categories) */
    val availableCategories: List<BudgetCategoryOption> = emptyList(),

    // ==================== Income & Allocation ====================

    /** User's income for the selected period (null = not set) */
    val monthlyIncome: Double? = null,

    /** Sum of active budget amounts for the selected period type */
    val totalBudgeted: Double = 0.0,

    /** Whether the income dialog is visible */
    val showIncomeDialog: Boolean = false,

    /** Form field for income dialog */
    val dialogIncomeAmount: String = "",

    // ==================== Add/Edit Budget Dialog ====================

    /** Whether the add/edit dialog is visible */
    val showAddEditDialog: Boolean = false,

    /** Budget being edited (null = adding new) */
    val editingBudget: Budget? = null,

    /** Form fields for the add/edit dialog */
    val dialogCategoryId: Long? = null,
    val dialogIsGroupBudget: Boolean = true, // true = group-level, false = sub-category
    val dialogAmount: String = "",
    // Note: period is inherited from selectedPeriodType — no dialogPeriod needed
    // For CUSTOM: customStartDate/customEndDate from the screen state are used

    /** Confirmation dialog for delete */
    val showDeleteConfirmation: Boolean = false,
    val budgetToDelete: Budget? = null,

    val error: String? = null,
    val saveSuccess: Boolean = false
)

/**
 * Represents a category option in the budget picker.
 * Can be a group or a sub-category.
 */
data class BudgetCategoryOption(
    /** Category ID. */
    val id: Long?,
    /** Display name */
    val name: String,
    /** Hex colour */
    val color: String?,
    /** Whether this is a group (true) or sub-category (false). */
    val isGroup: Boolean?,
    /** Parent group ID for sub-categories. Null for groups. */
    val parentGroupId: Long?,
    /** Whether a budget already exists for this category at the same level (prevents duplicates in UI) */
    val hasExistingBudget: Boolean
)
