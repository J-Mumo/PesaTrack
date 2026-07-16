package com.pesatrack.presentation.screens.batch_categorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.database.dao.RecipientGroup
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.services.CategorizationService
import com.pesatrack.services.RecipientInfo
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
 * Supports three modes per recipient group:
 * - Quick mode: tap recipient → pick category → apply to ALL transactions
 * - Review mode: expand recipient → see individual transactions → override per-transaction
 * - Auto mode: request rules engine suggestions → show confidence chips → confirm/override
 *
 * Additionally supports a multi-select mode:
 * - Long-press a group to enter selection mode
 * - Select multiple groups → apply one category to all of them at once
 *
 * Saves recipient→category mappings for future auto-categorization.
 * Multi-category mappings are supported: one recipient can map to multiple categories.
 */
@HiltViewModel
class BatchCategorizeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val recipientMappingRepository: RecipientMappingRepository,
    private val categorizationService: CategorizationService
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
     * Create a custom category inline from any of the pickers on this screen.
     * `parentId == null` creates a top-level group; otherwise a sub-category under that group.
     */
    fun createCategory(
        name: String,
        icon: String,
        color: String,
        parentId: Long?,
        onCreated: (Category) -> Unit
    ) {
        viewModelScope.launch {
            val id = if (parentId == null) {
                categoryRepository.addCategoryGroup(name, icon, color)
            } else {
                categoryRepository.addCategory(name, icon, color, parentId)
            }
            onCreated(
                Category(
                    id = id,
                    name = name,
                    icon = icon,
                    color = color,
                    parentId = parentId,
                    isGroup = parentId == null,
                    isDefault = false
                )
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
                //    Paybill groups save composite (paybill, account) keys so aggregator
                //    paybills don't cross-fire between merchants sharing the paybill number.
                if (recipientGroup.paymentType == PaymentType.PAY_BILL.name) {
                    savePaybillMappingsForGroup(recipientGroup, category.id)
                } else {
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
                }

                // 3. Refresh the list and remove applied suggestion
                val remainingGroups = expenseRepository.getUncategorizedGroupedByRecipient()
                val updatedSuggestions = _uiState.value.autoSuggestions.toMutableMap()
                updatedSuggestions.remove(recipientGroup.recipientKey)

                _uiState.update {
                    it.copy(
                        recipientGroups = remainingGroups,
                        selectedRecipientGroup = null,
                        showCategoryPicker = false,
                        isSaving = false,
                        categorizedCount = it.categorizedCount + 1,
                        autoSuggestions = updatedSuggestions
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
                    if (expense.paymentType == PaymentType.PAY_BILL) {
                        recipientMappingRepository.savePaybillMapping(
                            paybillName = expense.recipientName,
                            account = expense.recipient,
                            categoryId = category.id,
                            displayName = expense.recipientName
                        )
                    } else {
                        val mappingKey = expense.recipientName ?: expense.recipient
                        recipientMappingRepository.saveMapping(
                            recipientKey = mappingKey,
                            categoryId = category.id,
                            displayName = expense.recipientName
                        )
                    }
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

    // ==================== Auto-Categorization (Rules Engine) ====================

    /**
     * Request category suggestions from the on-device rules engine
     * for all uncategorized recipient groups.
     */
    fun requestAutoSuggestions() {
        val groups = _uiState.value.recipientGroups
        if (groups.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAutoSuggestLoading = true, autoSuggestError = null) }

            try {
                // Convert RecipientGroup to RecipientInfo for the categorization service
                val recipientInfoList = groups.map { group ->
                    RecipientInfo(
                        recipientKey = group.recipientKey,
                        displayName = group.recipientName ?: group.recipient,
                        paymentType = group.paymentType,
                        totalAmount = group.totalAmount,
                        transactionCount = group.transactionCount
                    )
                }

                val result = categorizationService.suggestCategories(recipientInfoList)

                _uiState.update {
                    it.copy(
                        autoSuggestions = result.suggestions,
                        isAutoSuggestLoading = false,
                        autoSuggestError = result.error
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAutoSuggestLoading = false,
                        autoSuggestError = e.message ?: "Auto-categorization failed"
                    )
                }
            }
        }
    }

    /**
     * Apply an auto-suggestion for a specific recipient group.
     * Finds the matching RecipientGroup and category, then applies it.
     */
    fun applyAutoSuggestion(recipientKey: String) {
        val suggestion = _uiState.value.autoSuggestions[recipientKey] ?: return
        val group = _uiState.value.recipientGroups.find { it.recipientKey == recipientKey } ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                // 1. Bulk update expenses by recipient
                val recipientName = group.recipientName
                val recipient = group.recipient

                if (!recipientName.isNullOrBlank()) {
                    expenseRepository.updateCategoryByRecipientName(
                        recipientName, suggestion.categoryId
                    )
                }
                if (recipient.isNotBlank()) {
                    expenseRepository.updateCategoryByRecipient(
                        recipient, suggestion.categoryId
                    )
                }

                // 2. Save recipient→category mapping
                if (group.paymentType == PaymentType.PAY_BILL.name) {
                    savePaybillMappingsForGroup(group, suggestion.categoryId)
                } else {
                    val mappingKey = recipientName ?: recipient
                    recipientMappingRepository.saveMapping(
                        recipientKey = mappingKey,
                        categoryId = suggestion.categoryId,
                        displayName = recipientName
                    )

                    if (!recipientName.isNullOrBlank() && recipient.isNotBlank() && recipientName != recipient) {
                        recipientMappingRepository.saveMapping(
                            recipientKey = recipient,
                            categoryId = suggestion.categoryId,
                            displayName = recipientName
                        )
                    }
                }

                // 3. Refresh and remove suggestion
                val remainingGroups = expenseRepository.getUncategorizedGroupedByRecipient()
                val updatedSuggestions = _uiState.value.autoSuggestions.toMutableMap()
                updatedSuggestions.remove(recipientKey)

                _uiState.update {
                    it.copy(
                        recipientGroups = remainingGroups,
                        autoSuggestions = updatedSuggestions,
                        isSaving = false,
                        categorizedCount = it.categorizedCount + 1
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to apply suggestion"
                    )
                }
            }
        }
    }

    /**
     * Apply ALL auto-suggestions at once.
     * Iterates through each suggestion and applies them sequentially.
     */
    fun applyAllAutoSuggestions() {
        val suggestions = _uiState.value.autoSuggestions
        if (suggestions.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                var appliedCount = 0

                for ((recipientKey, suggestion) in suggestions) {
                    val group = _uiState.value.recipientGroups.find { it.recipientKey == recipientKey }
                        ?: continue

                    // Bulk update expenses by recipient
                    if (!group.recipientName.isNullOrBlank()) {
                        expenseRepository.updateCategoryByRecipientName(
                            group.recipientName, suggestion.categoryId
                        )
                    }
                    if (group.recipient.isNotBlank()) {
                        expenseRepository.updateCategoryByRecipient(
                            group.recipient, suggestion.categoryId
                        )
                    }

                    // Save recipient→category mapping
                    if (group.paymentType == PaymentType.PAY_BILL.name) {
                        savePaybillMappingsForGroup(group, suggestion.categoryId)
                    } else {
                        val mappingKey = group.recipientName ?: group.recipient
                        recipientMappingRepository.saveMapping(
                            recipientKey = mappingKey,
                            categoryId = suggestion.categoryId,
                            displayName = group.recipientName
                        )

                        if (!group.recipientName.isNullOrBlank() && group.recipient.isNotBlank() && group.recipientName != group.recipient) {
                            recipientMappingRepository.saveMapping(
                                recipientKey = group.recipient,
                                categoryId = suggestion.categoryId,
                                displayName = group.recipientName
                            )
                        }
                    }

                    appliedCount++
                }

                // Refresh
                val remainingGroups = expenseRepository.getUncategorizedGroupedByRecipient()

                _uiState.update {
                    it.copy(
                        recipientGroups = remainingGroups,
                        autoSuggestions = emptyMap(),
                        isSaving = false,
                        categorizedCount = it.categorizedCount + appliedCount
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to apply suggestions"
                    )
                }
            }
        }
    }

    /**
     * Dismiss auto-suggest error message
     */
    fun dismissAutoSuggestError() {
        _uiState.update { it.copy(autoSuggestError = null) }
    }

    // ==================== Multi-Select Mode ====================

    /**
     * Enter selection mode with the first long-pressed group selected.
     * If already in selection mode, this acts as a toggle for that group.
     */
    fun enterSelectionMode(recipientKey: String) {
        _uiState.update {
            it.copy(
                isSelectionMode = true,
                selectedGroupKeys = it.selectedGroupKeys + recipientKey,
                // Collapse any expanded group when entering selection mode
                expandedGroupKey = null,
                expandedGroupExpenses = emptyList()
            )
        }
    }

    /**
     * Exit selection mode and clear all selections.
     */
    fun exitSelectionMode() {
        _uiState.update {
            it.copy(
                isSelectionMode = false,
                selectedGroupKeys = emptySet(),
                showBulkCategoryPicker = false
            )
        }
    }

    /**
     * Toggle a group's selection state within selection mode.
     */
    fun toggleGroupSelection(recipientKey: String) {
        _uiState.update {
            val newKeys = if (recipientKey in it.selectedGroupKeys) {
                it.selectedGroupKeys - recipientKey
            } else {
                it.selectedGroupKeys + recipientKey
            }
            // If no selections remain, exit selection mode
            if (newKeys.isEmpty()) {
                it.copy(
                    isSelectionMode = false,
                    selectedGroupKeys = emptySet()
                )
            } else {
                it.copy(selectedGroupKeys = newKeys)
            }
        }
    }

    /**
     * Select all currently visible recipient groups.
     */
    fun selectAllGroups() {
        _uiState.update {
            it.copy(
                selectedGroupKeys = it.recipientGroups.map { g -> g.recipientKey }.toSet()
            )
        }
    }

    /**
     * Deselect all groups (but stay in selection mode).
     */
    fun deselectAllGroups() {
        _uiState.update {
            it.copy(selectedGroupKeys = emptySet())
        }
    }

    /**
     * Open the bulk category picker for all selected groups.
     */
    fun showBulkCategoryPicker() {
        if (_uiState.value.selectedGroupKeys.isEmpty()) return
        _uiState.update { it.copy(showBulkCategoryPicker = true) }
    }

    /**
     * Dismiss the bulk category picker.
     */
    fun dismissBulkCategoryPicker() {
        _uiState.update { it.copy(showBulkCategoryPicker = false) }
    }

    /**
     * Apply a category to ALL expenses from ALL selected recipient groups.
     * Saves recipient→category mappings for each unique recipient.
     * Reuses the same logic as [applyCategory] but loops through all selected groups.
     */
    fun applyBulkCategory(category: Category) {
        val selectedKeys = _uiState.value.selectedGroupKeys
        if (selectedKeys.isEmpty()) return

        val groups = _uiState.value.recipientGroups.filter { it.recipientKey in selectedKeys }
        if (groups.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, showBulkCategoryPicker = false) }

            try {
                var appliedCount = 0

                for (group in groups) {
                    // 1. Bulk update expenses by recipient
                    val recipientName = group.recipientName
                    val recipient = group.recipient

                    if (!recipientName.isNullOrBlank()) {
                        expenseRepository.updateCategoryByRecipientName(
                            recipientName, category.id
                        )
                    }
                    if (recipient.isNotBlank()) {
                        expenseRepository.updateCategoryByRecipient(
                            recipient, category.id
                        )
                    }

                    // 2. Save recipient→category mapping (multi-category aware)
                    if (group.paymentType == PaymentType.PAY_BILL.name) {
                        savePaybillMappingsForGroup(group, category.id)
                    } else {
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
                    }

                    appliedCount++
                }

                // 3. Refresh the list and remove applied suggestions
                val remainingGroups = expenseRepository.getUncategorizedGroupedByRecipient()
                val updatedSuggestions = _uiState.value.autoSuggestions.toMutableMap()
                selectedKeys.forEach { key -> updatedSuggestions.remove(key) }

                _uiState.update {
                    it.copy(
                        recipientGroups = remainingGroups,
                        isSaving = false,
                        categorizedCount = it.categorizedCount + appliedCount,
                        autoSuggestions = updatedSuggestions,
                        // Exit selection mode after bulk apply
                        isSelectionMode = false,
                        selectedGroupKeys = emptySet(),
                        showBulkCategoryPicker = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to apply bulk category"
                    )
                }
            }
        }
    }

    // ==================== Ignore/Exclude ====================

    /**
     * Ignore/exclude all expenses from a recipient group.
     * Marks them as isExcluded = true so they won't appear in
     * batch categorize, totals, or analytics.
     */
    fun ignoreRecipientGroup(group: RecipientGroup) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                expenseRepository.excludeByRecipientGroup(
                    recipient = group.recipient,
                    recipientName = group.recipientName
                )

                // Refresh and remove any suggestion for this group
                val remainingGroups = expenseRepository.getUncategorizedGroupedByRecipient()
                val updatedSuggestions = _uiState.value.autoSuggestions.toMutableMap()
                updatedSuggestions.remove(group.recipientKey)

                _uiState.update {
                    it.copy(
                        recipientGroups = remainingGroups,
                        autoSuggestions = updatedSuggestions,
                        isSaving = false,
                        // Collapse expanded group if it was the ignored one
                        expandedGroupKey = if (it.expandedGroupKey == group.recipientKey) null else it.expandedGroupKey,
                        expandedGroupExpenses = if (it.expandedGroupKey == group.recipientKey) emptyList() else it.expandedGroupExpenses
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to ignore expenses"
                    )
                }
            }
        }
    }

    /**
     * Ignore/exclude a single expense from the review list.
     */
    fun ignoreExpense(expenseId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                expenseRepository.setExcluded(expenseId, true)

                // Refresh expanded group and groups list
                val expandedKey = _uiState.value.expandedGroupKey
                val updatedExpenses = if (expandedKey != null) {
                    expenseRepository.getUncategorizedByRecipientKey(expandedKey)
                } else emptyList()

                val remainingGroups = expenseRepository.getUncategorizedGroupedByRecipient()
                val shouldCollapse = updatedExpenses.isEmpty()

                _uiState.update {
                    it.copy(
                        recipientGroups = remainingGroups,
                        expandedGroupExpenses = updatedExpenses,
                        expandedGroupKey = if (shouldCollapse) null else it.expandedGroupKey,
                        isSaving = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to ignore expense"
                    )
                }
            }
        }
    }

    // ==================== General ====================

    /**
     * Save composite (paybill, account) mappings for every distinct paybill account
     * in a recipient group. Aggregator paybills (e.g. NCBA Loop 247247) can group
     * multiple unrelated merchants under one paybill name; each account gets its own
     * mapping so future auto-categorization stays account-specific.
     * No-op for non-paybill groups (caller handles those with the generic saveMapping).
     */
    private suspend fun savePaybillMappingsForGroup(group: RecipientGroup, categoryId: Long) {
        if (group.paymentType != PaymentType.PAY_BILL.name) return
        val expenses = expenseRepository.getUncategorizedByRecipientKey(group.recipientKey)
        val distinct = expenses
            .filter { it.paymentType == PaymentType.PAY_BILL }
            .map { (it.recipientName ?: group.recipientName) to it.recipient }
            .filter { (name, acct) -> !name.isNullOrBlank() && acct.isNotBlank() }
            .distinct()
        for ((paybillName, account) in distinct) {
            recipientMappingRepository.savePaybillMapping(
                paybillName = paybillName,
                account = account,
                categoryId = categoryId,
                displayName = paybillName
            )
        }
    }

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
