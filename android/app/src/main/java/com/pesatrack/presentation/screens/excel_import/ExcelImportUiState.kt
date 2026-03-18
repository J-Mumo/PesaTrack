package com.pesatrack.presentation.screens.excel_import

import com.pesatrack.services.ExcelImportService

/**
 * UI State for the Excel Import screen.
 */
data class ExcelImportUiState(
    /** Current phase of the import process */
    val phase: ExcelImportPhase = ExcelImportPhase.READY,

    /** Selected file names (display only) */
    val selectedFileNames: List<String> = emptyList(),

    /** Progress during import (0..total) */
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressPhase: String = "",

    /** Import result (available after import completes) */
    val result: ExcelImportService.ExcelImportResult? = null,

    /** Error message if import failed */
    val error: String? = null
)

/**
 * Excel import phases.
 */
enum class ExcelImportPhase {
    /** Ready — waiting for user to select files */
    READY,
    /** Files selected — waiting for user to confirm import */
    FILES_SELECTED,
    /** Import in progress */
    IMPORTING,
    /** Import completed successfully */
    COMPLETED,
    /** Import failed with error */
    ERROR
}
