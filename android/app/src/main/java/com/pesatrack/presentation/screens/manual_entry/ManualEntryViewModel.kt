package com.pesatrack.presentation.screens.manual_entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val recipientMappingRepository: RecipientMappingRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualEntryUiState())
    val uiState: StateFlow<ManualEntryUiState> = _uiState.asStateFlow()

    init {
        loadCategoryGroups()
    }

    private fun loadCategoryGroups() {
        viewModelScope.launch {
            categoryRepository.getCategoryGroups().collect { groups ->
                _uiState.update { it.copy(categoryGroups = groups) }
            }
        }
    }

    // ==================== Form Field Updates ====================

    fun updateAmount(value: String) {
        // Allow only valid decimal input
        val filtered = value.filter { it.isDigit() || it == '.' }
        // Prevent multiple decimal points
        val dotCount = filtered.count { it == '.' }
        val sanitized = if (dotCount > 1) {
            val firstDot = filtered.indexOf('.')
            filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
        } else filtered

        _uiState.update {
            it.copy(
                amount = sanitized,
                amountError = null
            )
        }
    }

    fun updateRecipient(value: String) {
        // Only allow digits (phone/till/paybill numbers)
        val filtered = value.filter { it.isDigit() }
        _uiState.update {
            it.copy(
                recipient = filtered,
                recipientError = null
            )
        }
    }

    fun updateRecipientName(value: String) {
        _uiState.update {
            it.copy(
                recipientName = value,
                recipientNameError = null
            )
        }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun updatePaymentType(paymentType: PaymentType) {
        _uiState.update { it.copy(paymentType = paymentType) }
    }

    fun updateDate(dateMillis: Long) {
        _uiState.update { it.copy(selectedDate = dateMillis) }
    }

    fun selectCategory(category: Category) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    // ==================== Validation ====================

    private fun validate(): Boolean {
        val state = _uiState.value
        var valid = true

        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(amountError = "Enter a valid amount") }
            valid = false
        }

        if (state.recipientName.isBlank()) {
            _uiState.update { it.copy(recipientNameError = "Enter a recipient name") }
            valid = false
        }

        // Recipient number is optional, but if provided must be digits only
        if (state.recipient.isNotBlank() && !state.recipient.all { it.isDigit() }) {
            _uiState.update { it.copy(recipientError = "Must be numbers only") }
            valid = false
        }

        return valid
    }

    // ==================== Save ====================

    fun saveExpense() {
        if (!validate()) return

        val state = _uiState.value
        val amount = state.amount.toDoubleOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                // Use recipient number if provided, otherwise fall back to recipient name
                val recipientValue = state.recipient.trim().ifBlank { state.recipientName.trim() }
                val recipientNameValue = state.recipientName.trim().ifBlank { null }

                val expense = Expense(
                    amount = amount,
                    recipient = recipientValue,
                    recipientName = recipientNameValue,
                    categoryId = state.selectedCategory?.id,
                    paymentType = state.paymentType,
                    source = ExpenseSource.MANUAL,
                    notes = state.notes.trim().ifBlank { null },
                    timestamp = state.selectedDate,
                    isCategorized = state.selectedCategory != null
                )

                expenseRepository.saveExpense(expense)

                // Save recipient→category mapping if category was selected
                if (state.selectedCategory != null) {
                    // Save by number if provided
                    if (state.recipient.isNotBlank()) {
                        recipientMappingRepository.saveMapping(
                            recipientKey = state.recipient.trim(),
                            categoryId = state.selectedCategory.id,
                            displayName = recipientNameValue
                        )
                    }
                    // Also save by name
                    if (state.recipientName.isNotBlank()) {
                        recipientMappingRepository.saveMapping(
                            recipientKey = state.recipientName.trim(),
                            categoryId = state.selectedCategory.id,
                            displayName = state.recipientName.trim()
                        )
                    }
                }

                _uiState.update { it.copy(isSaving = false, isSaved = true) }

                // Track manual entry milestone and counter (fire-and-forget)
                launch {
                    appPreferences.recordFirstManualEntry()
                    appPreferences.incrementManualEntriesCount()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save expense"
                    )
                }
            }
        }
    }
}
