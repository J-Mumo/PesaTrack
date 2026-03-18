package com.pesatrack.presentation.screens.batch_categorize

import com.pesatrack.data.local.database.dao.RecipientGroup
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.CategoryGroup
import com.pesatrack.domain.models.Expense
import com.pesatrack.services.AiCategorySuggestion

/**
 * UI State for the Batch Categorize screen.
 *
 * Supports three modes per recipient group:
 * - Quick mode: "Apply to All" — one category for the entire group
 * - Review mode: Expand to see individual transactions, each with category override
 * - AI mode: "AI Suggest" — Gemini suggests categories with confidence levels
 */
data class BatchCategorizeUiState(
    val isLoading: Boolean = true,

    /** Uncategorized expenses grouped by recipient */
    val recipientGroups: List<RecipientGroup> = emptyList(),

    /** Available category groups for selection */
    val categoryGroups: List<CategoryGroup> = emptyList(),

    /** Currently selected recipient group (for category picker in quick mode) */
    val selectedRecipientGroup: RecipientGroup? = null,

    /** Show category picker dialog (for quick "apply to all" mode) */
    val showCategoryPicker: Boolean = false,

    /** Currently expanded recipient group key (for review mode) */
    val expandedGroupKey: String? = null,

    /** Individual expenses loaded for the expanded group */
    val expandedGroupExpenses: List<Expense> = emptyList(),

    /** Currently selected expense ID (for individual category override) */
    val selectedExpenseId: Long? = null,

    /** Show category picker for an individual expense */
    val showIndividualCategoryPicker: Boolean = false,

    /** Number of groups categorized in this session */
    val categorizedCount: Int = 0,

    /** Number of individual expenses categorized in review mode this session */
    val individualCategorizedCount: Int = 0,

    /** Saving in progress */
    val isSaving: Boolean = false,

    /** Loading expanded group details */
    val isLoadingExpanded: Boolean = false,

    /** Error message */
    val error: String? = null,

    // ==================== AI Categorization ====================

    /** AI suggestion results — Map<recipientKey, AiCategorySuggestion> */
    val aiSuggestions: Map<String, AiCategorySuggestion> = emptyMap(),

    /** Whether AI suggestion request is in progress */
    val isAiLoading: Boolean = false,

    /** AI-specific error message (shown as inline card/snackbar) */
    val aiError: String? = null,

    /** Whether AI categorization is enabled in preferences */
    val aiEnabled: Boolean = false
)
