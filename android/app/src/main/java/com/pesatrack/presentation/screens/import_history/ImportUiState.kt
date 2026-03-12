package com.pesatrack.presentation.screens.import_history

import com.pesatrack.services.SmsImportService

/**
 * UI State for the Import History screen
 */
data class ImportUiState(
    /** Current phase of the import process */
    val phase: ImportPhase = ImportPhase.READY,

    /** Selected date range option */
    val selectedRange: DateRange = DateRange.LAST_3_MONTHS,

    /** Progress during import (0..total) */
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,

    /** Import result (available after import completes) */
    val result: SmsImportService.ImportResult? = null,

    /** Error message if import failed */
    val error: String? = null
)

/**
 * Import phases
 */
enum class ImportPhase {
    /** Ready to start import */
    READY,
    /** Import in progress */
    IMPORTING,
    /** Import completed successfully */
    COMPLETED,
    /** Import failed with error */
    ERROR
}

/**
 * Date range options for historical import
 */
enum class DateRange(val displayName: String, val daysBack: Long?) {
    LAST_30_DAYS("Last 30 days", 30),
    LAST_3_MONTHS("Last 3 months", 90),
    LAST_6_MONTHS("Last 6 months", 180),
    LAST_YEAR("Last year", 365),
    ALL_HISTORY("All history", null);

    /**
     * Get the timestamp for the start of this range
     */
    fun getFromTimestamp(): Long? {
        return daysBack?.let {
            System.currentTimeMillis() - (it * 24 * 60 * 60 * 1000)
        }
    }
}
