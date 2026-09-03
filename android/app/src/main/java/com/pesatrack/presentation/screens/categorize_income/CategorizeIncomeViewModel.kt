package com.pesatrack.presentation.screens.categorize_income

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.IncomeRepository
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.services.telemetry.TelemetryClient
import com.pesatrack.services.telemetry.TelemetryEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the income categorization screen (Income tracking Phase 2).
 *
 * Loads the [com.pesatrack.domain.models.IncomeTransaction] by row id and
 * lets the user pick a source / toggle the exclude flag before persisting
 * via [IncomeRepository.updateSource] + [IncomeRepository.setExcluded].
 */
@HiltViewModel
class CategorizeIncomeViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val telemetryClient: TelemetryClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val incomeId: Long = savedStateHandle.get<Long>("incomeId") ?: 0L

    private val _uiState = MutableStateFlow(CategorizeIncomeUiState())
    val uiState: StateFlow<CategorizeIncomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val income = incomeRepository.getById(incomeId)
            _uiState.value = if (income == null) {
                CategorizeIncomeUiState(
                    isLoading = false,
                    errorMessage = "Income not found"
                )
            } else {
                CategorizeIncomeUiState(
                    isLoading = false,
                    income = income,
                    selectedSource = income.source,
                    isExcluded = income.isExcluded,
                )
            }
        }
    }

    fun selectSource(source: IncomeSource) {
        _uiState.update { it.copy(selectedSource = source) }
    }

    fun toggleExcluded() {
        val newValue = !_uiState.value.isExcluded
        _uiState.update { it.copy(isExcluded = newValue) }
        telemetryClient.logEvent(
            TelemetryEvents.INCOME_EXCLUDED_TOGGLED,
            mapOf(TelemetryEvents.PARAM_ENABLED to newValue)
        )
    }

    fun save() {
        val current = _uiState.value
        val income = current.income ?: return
        if (current.isSaving) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            incomeRepository.updateSource(income.id, current.selectedSource)
            incomeRepository.setExcluded(income.id, current.isExcluded)
            // Learn this sender → source mapping so future income auto-classifies.
            incomeRepository.learnSenderSource(income.sender, current.selectedSource)
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
            telemetryClient.logEvent(TelemetryEvents.INCOME_CATEGORIZED_MANUAL)
        }
    }

    /**
     * Permanently remove this income row. Used when the row was created in
     * error (typically via [ManualIncomeEntryDialog]). Uses the same
     * `isSaved = true` completion flag as [save] so the screen's existing
     * `LaunchedEffect` navigates back without a second listener.
     */
    fun delete() {
        val current = _uiState.value
        val income = current.income ?: return
        if (current.isDeleting || current.isSaving) return

        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            incomeRepository.delete(income.id)
            _uiState.update { it.copy(isDeleting = false, isSaved = true) }
            telemetryClient.logEvent(TelemetryEvents.INCOME_DELETED)
        }
    }
}
