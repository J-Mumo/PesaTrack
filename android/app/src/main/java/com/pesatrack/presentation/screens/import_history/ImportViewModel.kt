package com.pesatrack.presentation.screens.import_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.services.SmsImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Import History screen.
 *
 * Manages the import flow:
 * 1. User selects date range
 * 2. Triggers import via SmsImportService
 * 3. Tracks progress
 * 4. Shows results
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val smsImportService: SmsImportService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * Update the selected date range
     */
    fun selectDateRange(range: DateRange) {
        _uiState.update { it.copy(selectedRange = range) }
    }

    /**
     * Start the import process
     */
    fun startImport() {
        val range = _uiState.value.selectedRange

        _uiState.update {
            it.copy(
                phase = ImportPhase.IMPORTING,
                progressCurrent = 0,
                progressTotal = 0,
                result = null,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val result = smsImportService.importHistoricalSms(
                    fromTimestamp = range.getFromTimestamp(),
                    toTimestamp = null, // up to now
                    onProgress = { current, total ->
                        _uiState.update {
                            it.copy(
                                progressCurrent = current,
                                progressTotal = total
                            )
                        }
                    }
                )

                _uiState.update {
                    it.copy(
                        phase = ImportPhase.COMPLETED,
                        result = result
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        phase = ImportPhase.ERROR,
                        error = e.message ?: "Import failed"
                    )
                }
            }
        }
    }

    /**
     * Reset to ready state (e.g., to import again with different range)
     */
    fun reset() {
        _uiState.update { ImportUiState() }
    }
}
