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
        viewModelScope.launch {
            incomeRepository.refreshMonthStartDay()
            seedAnchorToNow(_uiState.value.period)
            reload()
        }
    }

    fun setPeriod(period: IncomePeriod) {
        if (period == _uiState.value.period) return
        // Reset anchor to "now" whenever the user swaps periods so they don't
        // land on a stale month/quarter that no longer exists in the new mode.
        _uiState.update { it.copy(period = period) }
        seedAnchorToNow(period)
        reload()
    }

    fun refresh() = reload()

    /** Step one period earlier (older) than the current anchor. */
    fun previousPeriod() {
        val s = _uiState.value
        when (s.period) {
            IncomePeriod.MONTH -> {
                val cal = Calendar.getInstance().apply {
                    clear()
                    set(Calendar.YEAR, s.anchorYear)
                    set(Calendar.MONTH, s.anchorMonth1Based - 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                }
                _uiState.update {
                    it.copy(
                        anchorYear = cal.get(Calendar.YEAR),
                        anchorMonth1Based = cal.get(Calendar.MONTH) + 1,
                    )
                }
            }

            IncomePeriod.QUARTER -> {
                var y = s.anchorYear
                var q = s.anchorQuarter1Based - 1
                if (q < 1) { q = 4; y -= 1 }
                _uiState.update { it.copy(anchorYear = y, anchorQuarter1Based = q) }
            }

            IncomePeriod.YEAR -> {
                _uiState.update { it.copy(anchorYear = s.anchorYear - 1) }
            }
        }
        reload()
    }

    /** Step one period later; no-op when already at the current period. */
    fun nextPeriod() {
        if (!_uiState.value.canGoNext) return
        val s = _uiState.value
        when (s.period) {
            IncomePeriod.MONTH -> {
                val cal = Calendar.getInstance().apply {
                    clear()
                    set(Calendar.YEAR, s.anchorYear)
                    set(Calendar.MONTH, s.anchorMonth1Based - 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, 1)
                }
                _uiState.update {
                    it.copy(
                        anchorYear = cal.get(Calendar.YEAR),
                        anchorMonth1Based = cal.get(Calendar.MONTH) + 1,
                    )
                }
            }

            IncomePeriod.QUARTER -> {
                var y = s.anchorYear
                var q = s.anchorQuarter1Based + 1
                if (q > 4) { q = 1; y += 1 }
                _uiState.update { it.copy(anchorYear = y, anchorQuarter1Based = q) }
            }

            IncomePeriod.YEAR -> {
                _uiState.update { it.copy(anchorYear = s.anchorYear + 1) }
            }
        }
        reload()
    }

    /**
     * Seed the anchor state to the period that contains "now". Called on init
     * and whenever the user swaps period so navigation always starts from the
     * current view.
     */
    private fun seedAnchorToNow(period: IncomePeriod) {
        val now = Calendar.getInstance()
        val monthAnchor = Calendar.getInstance().apply {
            timeInMillis = MonthPeriod.currentRange(incomeRepository.monthStartDay).first
        }
        val year = when (period) {
            IncomePeriod.MONTH -> monthAnchor.get(Calendar.YEAR)
            IncomePeriod.QUARTER, IncomePeriod.YEAR -> now.get(Calendar.YEAR)
        }
        _uiState.update {
            it.copy(
                anchorYear = year,
                anchorMonth1Based = monthAnchor.get(Calendar.MONTH) + 1,
                anchorQuarter1Based = now.get(Calendar.MONTH) / 3 + 1,
            )
        }
    }

    private fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                incomeRepository.refreshMonthStartDay()
                val (start, end, label) = boundsForPeriod(_uiState.value)
                val txs = incomeRepository.getForRange(start, end)
                val breakdown = incomeRepository.sourceBreakdown(start, end)
                val inflow = txs
                    .filter { !it.isExcluded && it.source.isInflow }
                    .sumOf { it.amount }
                // Reconciliation source (Detected / Manual override) only makes
                // sense for the current-month view — for past months there's no
                // "using your set income vs detected" tension worth surfacing.
                val effective = if (_uiState.value.period == IncomePeriod.MONTH &&
                    isCurrentAnchor(_uiState.value)
                ) {
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
                        canGoNext = !isCurrentAnchor(_uiState.value),
                        error = null,
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

    /** True when the anchor points at the period containing "now". */
    private fun isCurrentAnchor(state: IncomeUiState): Boolean {
        val now = Calendar.getInstance()
        return when (state.period) {
            IncomePeriod.MONTH -> {
                val currentStart = Calendar.getInstance().apply {
                    timeInMillis = MonthPeriod.currentRange(incomeRepository.monthStartDay).first
                }
                state.anchorYear == currentStart.get(Calendar.YEAR) &&
                    state.anchorMonth1Based == currentStart.get(Calendar.MONTH) + 1
            }

            IncomePeriod.QUARTER -> {
                val curYear = now.get(Calendar.YEAR)
                val curQuarter = now.get(Calendar.MONTH) / 3 + 1
                state.anchorYear == curYear && state.anchorQuarter1Based == curQuarter
            }

            IncomePeriod.YEAR -> state.anchorYear == now.get(Calendar.YEAR)
        }
    }

    private fun boundsForPeriod(state: IncomeUiState): Triple<Long, Long, String> {
        return when (state.period) {
            IncomePeriod.MONTH -> {
                val (start, end) = MonthPeriod.rangeForPeriodStart(
                    state.anchorYear,
                    state.anchorMonth1Based,
                    incomeRepository.monthStartDay,
                )
                val label = MonthPeriod.labelForRange(start, end, incomeRepository.monthStartDay)
                Triple(start, end, label)
            }

            IncomePeriod.QUARTER -> {
                val startCal = Calendar.getInstance().apply {
                    clear()
                    set(Calendar.YEAR, state.anchorYear)
                    set(Calendar.MONTH, (state.anchorQuarter1Based - 1) * 3)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val start = startCal.timeInMillis
                val end = startCal.apply { add(Calendar.MONTH, 3) }.timeInMillis
                Triple(start, end, "Q${state.anchorQuarter1Based} ${state.anchorYear}")
            }

            IncomePeriod.YEAR -> {
                val startCal = Calendar.getInstance().apply {
                    clear()
                    set(Calendar.YEAR, state.anchorYear)
                    set(Calendar.MONTH, 0)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val start = startCal.timeInMillis
                val end = startCal.apply { add(Calendar.YEAR, 1) }.timeInMillis
                Triple(start, end, state.anchorYear.toString())
            }
        }
    }
}
