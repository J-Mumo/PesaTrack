package com.pesatrack.presentation.screens.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.IncomeRepository
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.domain.models.IncomeTransaction
import com.pesatrack.utils.MonthPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs
@HiltViewModel
class IncomeViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomeUiState())
    val uiState: StateFlow<IncomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { incomeRepository.refreshMonthStartDay() }
        reload()
    }

    fun setPeriod(period: IncomePeriod) {
        if (period == _uiState.value.period) return
        _uiState.update { it.copy(period = period) }
        reload()
    }

    fun refresh() = reload()

    private fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                incomeRepository.refreshMonthStartDay()
                val (start, end, label) = boundsForPeriod(_uiState.value.period)
                val txs = incomeRepository.getForRange(start, end)
                val breakdown = incomeRepository.sourceBreakdown(start, end)
                val inflow = txs
                    .filter { !it.isExcluded && it.source.isInflow }
                    .sumOf { it.amount }
                val effective = if (_uiState.value.period == IncomePeriod.MONTH) {
                    incomeRepository.effectiveIncomeForCurrentMonth().source
                } else EffectiveIncomeSource.NONE
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        periodLabel = label,
                        totalInflow = inflow,
                        breakdown = breakdown,
                        transactions = txs,
                        effectiveIncomeSource = effective,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load income") }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //                         Manual entry dialog
    // ──────────────────────────────────────────────────────────────────────

    fun showManualEntryDialog() {
        _uiState.update {
            it.copy(
                showManualEntryDialog = true,
                dialogAmount = "",
                dialogSender = "",
                dialogSource = IncomeSource.OTHER,
                dialogDateMillis = System.currentTimeMillis(),
                dialogNote = "",
                dialogError = null
            )
        }
    }

    fun dismissManualEntryDialog() {
        _uiState.update { it.copy(showManualEntryDialog = false, dialogError = null) }
    }

    fun updateDialogAmount(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val dotCount = filtered.count { it == '.' }
        val sanitized = if (dotCount > 1) {
            val firstDot = filtered.indexOf('.')
            filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
        } else filtered
        _uiState.update { it.copy(dialogAmount = sanitized, dialogError = null) }
    }

    fun updateDialogSender(value: String) {
        _uiState.update { it.copy(dialogSender = value, dialogError = null) }
    }

    fun updateDialogSource(source: IncomeSource) {
        _uiState.update { it.copy(dialogSource = source) }
    }

    fun updateDialogDate(millis: Long) {
        _uiState.update { it.copy(dialogDateMillis = millis) }
    }

    fun updateDialogNote(value: String) {
        _uiState.update { it.copy(dialogNote = value) }
    }

    /**
     * Mark an income row as "not income" — sets the `isExcluded` flag so the
     * row is filtered out of totals, savings-rate, and source breakdowns but
     * is kept on disk for SMS-replay dedupe and audit.
     */
    fun markAsNotIncome(id: Long) {
        viewModelScope.launch {
            try {
                incomeRepository.setExcluded(id, true)
                reload()
            } catch (_: Exception) {
                _uiState.update { it.copy(error = "Could not update") }
            }
        }
    }

    /** Restore a previously-excluded income row. */
    fun restoreIncome(id: Long) {
        viewModelScope.launch {
            try {
                incomeRepository.setExcluded(id, false)
                reload()
            } catch (_: Exception) {
                _uiState.update { it.copy(error = "Could not update") }
            }
        }
    }

    fun saveManualEntry() {
        val state = _uiState.value
        val amount = state.dialogAmount.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.update { it.copy(dialogError = "Enter a valid amount") }
            return
        }
        viewModelScope.launch {
            try {
                val sender = state.dialogSender.trim().takeIf { it.isNotBlank() }
                val tx = IncomeTransaction(
                    transactionId = "MANUAL-${System.currentTimeMillis()}-${abs(amount.hashCode())}",
                    amount = amount,
                    timestamp = state.dialogDateMillis,
                    source = state.dialogSource,
                    sender = sender,
                    rawSms = null,
                    parserSource = "MANUAL",
                    note = state.dialogNote.trim().takeIf { it.isNotBlank() },
                    isExcluded = false,
                    isCategorized = state.dialogSource != IncomeSource.UNCATEGORIZED,
                )
                incomeRepository.insertIfNew(tx)
                _uiState.update { it.copy(showManualEntryDialog = false, dialogError = null) }
                reload()
            } catch (e: Exception) {
                _uiState.update { it.copy(dialogError = "Failed to save income") }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //                          Period helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun boundsForPeriod(period: IncomePeriod): Triple<Long, Long, String> {
        val cal = Calendar.getInstance()
        return when (period) {
            IncomePeriod.MONTH -> {
                val (start, end) = incomeRepository.currentMonthBounds()
                val label = MonthPeriod.labelForRange(start, end, incomeRepository.monthStartDay)
                Triple(start, end, label)
            }

            IncomePeriod.QUARTER -> {
                val now = Calendar.getInstance()
                val year = now.get(Calendar.YEAR)
                val quarter = now.get(Calendar.MONTH) / 3
                val startCal = Calendar.getInstance().apply {
                    clear()
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, quarter * 3)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val start = startCal.timeInMillis
                val end = startCal.apply { add(Calendar.MONTH, 3) }.timeInMillis
                Triple(start, end, "Q${quarter + 1} $year")
            }

            IncomePeriod.YEAR -> {
                val year = cal.get(Calendar.YEAR)
                val startCal = Calendar.getInstance().apply {
                    clear()
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, 0)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val start = startCal.timeInMillis
                val end = startCal.apply { add(Calendar.YEAR, 1) }.timeInMillis
                Triple(start, end, year.toString())
            }
        }
    }
}
