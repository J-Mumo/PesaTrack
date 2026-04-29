package com.pesatrack.presentation.screens.statement_import

import com.pesatrack.services.StatementImportService

/**
 * UI State for the M-PESA Statement Import screen.
 */
data class StatementImportUiState(
    /** Current phase of the import process */
    val phase: StatementImportPhase = StatementImportPhase.READY,

    /** Selected file name (display only) */
    val selectedFileName: String? = null,

    /** Whether the password dialog is showing */
    val showPasswordDialog: Boolean = false,

    /** Password input value */
    val passwordInput: String = "",

    /** Whether to remember the password for future imports */
    val rememberPassword: Boolean = false,

    /** Progress during import (0..total) */
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressPhase: String = "",

    /** Import result (available after import completes) */
    val result: StatementImportService.StatementImportResult? = null,

    /** Error message if import failed */
    val error: String? = null
)

/**
 * Statement import phases.
 */
enum class StatementImportPhase {
    /** Ready — waiting for user to select a file */
    READY,
    /** File selected — waiting for password (if needed) */
    FILE_SELECTED,
    /** Entering password */
    PASSWORD_ENTRY,
    /** Import in progress */
    IMPORTING,
    /** Import completed successfully */
    COMPLETED,
    /** Import failed with error */
    ERROR
}
