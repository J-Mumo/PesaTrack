package com.pesatrack.presentation.screens.income

import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.domain.models.IncomeSourceTotal
import com.pesatrack.domain.models.IncomeTransaction

/** Time period switcher on the [IncomeScreen]. */
enum class IncomePeriod {
    MONTH, QUARTER, YEAR
}

/** UI state for the Income list screen (Income tracking Phase 3). */
data class IncomeUiState(
    val isLoading: Boolean = true,
    val period: IncomePeriod = IncomePeriod.MONTH,
    /** Human-readable label for the active period, e.g. "June 2026" or "Q2 2026" or "2026". */
    val periodLabel: String = "",
    /** Total detected inflow income for the active period (excludes self-transfers). */
    val totalInflow: Double = 0.0,
    /** Per-source breakdown for the stacked bar / legend. */
    val breakdown: List<IncomeSourceTotal> = emptyList(),
    /** Reconciliation source for the active month (only meaningful when [period] == MONTH). */
    val effectiveIncomeSource: EffectiveIncomeSource = EffectiveIncomeSource.NONE,
    /** Income transactions for the active period (descending by timestamp). */
    val transactions: List<IncomeTransaction> = emptyList(),

    // ── Manual entry dialog ─────────────────────────────────────────────────
    val showManualEntryDialog: Boolean = false,
    val dialogAmount: String = "",
    val dialogSender: String = "",
    val dialogSource: IncomeSource = IncomeSource.OTHER,
    val dialogDateMillis: Long = System.currentTimeMillis(),
    val dialogNote: String = "",
    val dialogError: String? = null,

    val error: String? = null
)
