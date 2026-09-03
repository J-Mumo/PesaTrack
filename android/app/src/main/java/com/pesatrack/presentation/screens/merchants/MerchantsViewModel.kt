package com.pesatrack.presentation.screens.merchants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.ExpenseRepository.MerchantGroupSummary
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.services.telemetry.TelemetryClient
import com.pesatrack.services.telemetry.TelemetryEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backing state for the Merchants (re-categorization) screen.
 *
 * On reassign the VM:
 * 1. Bulk-updates every expense in the merchant group to the new category
 *    (via [ExpenseRepository.reassignMerchantGroupCategory]).
 * 2. Drops previous mapping(s) for the group's recipient key so the old
 *    dominant category no longer wins the auto-cat lookup.
 * 3. Saves a fresh mapping so future SMS to the same merchant + account go
 *    to the newly chosen category.
 *
 * Step 2 matters: without it, one call to [RecipientMappingRepository.saveMapping]
 * on a heavily-used key just increments the usage count for the *new* category
 * and the old one still wins the 80% confidence check.
 */
@HiltViewModel
class MerchantsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val recipientMappingRepository: RecipientMappingRepository,
    private val telemetryClient: TelemetryClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(MerchantsUiState())
    val uiState: StateFlow<MerchantsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val groups = categoryRepository.getCategoryGroups().first()
                val merchants = expenseRepository.getMerchantGroupsWithDominantCategory()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        merchants = merchants,
                        categoryGroups = groups
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load merchants")
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openMerchant(group: MerchantGroupSummary) {
        _uiState.update {
            it.copy(
                selectedGroup = group,
                selectedGroupExpenses = emptyList(),
                isLoadingSelected = true
            )
        }
        viewModelScope.launch {
            val expenses = expenseRepository.getExpensesForMerchantGroup(group.groupKey)
            _uiState.update {
                if (it.selectedGroup?.groupKey == group.groupKey) {
                    it.copy(selectedGroupExpenses = expenses, isLoadingSelected = false)
                } else it
            }
        }
    }

    fun closeMerchant() {
        _uiState.update {
            it.copy(
                selectedGroup = null,
                selectedGroupExpenses = emptyList(),
                isLoadingSelected = false,
                showCategoryPicker = false
            )
        }
    }

    fun requestCategoryPicker() {
        if (_uiState.value.selectedGroup == null) return
        _uiState.update { it.copy(showCategoryPicker = true) }
    }

    fun dismissCategoryPicker() {
        _uiState.update { it.copy(showCategoryPicker = false) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Reassign every expense in [group] to [categoryId] and update the recipient
     * mapping so future SMS follow suit.
     */
    fun reassign(group: MerchantGroupSummary, categoryId: Long, categoryName: String) {
        viewModelScope.launch {
            try {
                val updated = expenseRepository.reassignMerchantGroupCategory(
                    groupKey = group.groupKey,
                    categoryId = categoryId
                )

                // Update the auto-categorization mapping. Delete the previous
                // record first so the new category isn't drowned out by usage
                // counts on the old one.
                when (group.paymentType) {
                    PaymentType.PAY_BILL -> {
                        val composite = RecipientMappingRepository.composePaybillKey(
                            paybillName = group.recipientName,
                            account = group.recipient
                        )
                        if (composite != null) {
                            recipientMappingRepository.deleteMapping(composite)
                            recipientMappingRepository.savePaybillMapping(
                                paybillName = group.recipientName,
                                account = group.recipient,
                                categoryId = categoryId,
                                displayName = group.recipientName
                            )
                        }
                    }
                    else -> {
                        val key = (group.recipientName ?: group.recipient).ifBlank { null }
                        if (key != null) {
                            recipientMappingRepository.deleteMapping(key)
                            recipientMappingRepository.saveMapping(
                                recipientKey = key,
                                categoryId = categoryId,
                                displayName = group.recipientName
                            )
                        }
                    }
                }

                // Refresh list so the dominant category column reflects the new choice.
                val refreshed = expenseRepository.getMerchantGroupsWithDominantCategory()
                _uiState.update {
                    it.copy(
                        merchants = refreshed,
                        selectedGroup = null,
                        selectedGroupExpenses = emptyList(),
                        isLoadingSelected = false,
                        showCategoryPicker = false,
                        snackbarMessage = "Reassigned $updated transaction${if (updated == 1) "" else "s"} to $categoryName"
                    )
                }
                telemetryClient.logEvent(
                    TelemetryEvents.MERCHANT_RECATEGORIZED,
                    mapOf(TelemetryEvents.PARAM_COUNT_BUCKET to TelemetryEvents.countBucket(updated))
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showCategoryPicker = false,
                        error = e.message ?: "Failed to reassign"
                    )
                }
            }
        }
    }
}
