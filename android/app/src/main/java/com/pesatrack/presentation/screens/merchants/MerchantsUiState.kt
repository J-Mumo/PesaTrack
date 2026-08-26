package com.pesatrack.presentation.screens.merchants

import com.pesatrack.data.repository.ExpenseRepository.MerchantGroupSummary
import com.pesatrack.domain.models.CategoryGroup
import com.pesatrack.domain.models.Expense

/**
 * UI state for the Merchants (re-categorization) screen.
 *
 * Each row surfaces a distinct `(recipientName, account?)` pair so the user
 * can reassign every transaction to a paybill / merchant / account combo in
 * one action — the fix for aggregator paybills (e.g. one M-PESA paybill
 * shared by many unrelated businesses) that auto-categorization poisoned
 * with a single wrong category.
 */
data class MerchantsUiState(
    val isLoading: Boolean = true,
    val merchants: List<MerchantGroupSummary> = emptyList(),
    val categoryGroups: List<CategoryGroup> = emptyList(),
    /**
     * Case-insensitive substring filter across merchant name, account, and
     * current dominant category. Empty = show everything.
     */
    val searchQuery: String = "",
    /** Group whose detail sheet is currently open, or null. */
    val selectedGroup: MerchantGroupSummary? = null,
    /** Expenses inside [selectedGroup]. Populated when the sheet opens. */
    val selectedGroupExpenses: List<Expense> = emptyList(),
    val isLoadingSelected: Boolean = false,
    /** True when the category picker is open on top of the detail sheet. */
    val showCategoryPicker: Boolean = false,
    /** One-shot success text — e.g. "Reassigned 12 transactions to Groceries". */
    val snackbarMessage: String? = null,
    val error: String? = null
)
