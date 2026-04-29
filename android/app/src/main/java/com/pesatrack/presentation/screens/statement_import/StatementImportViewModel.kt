package com.pesatrack.presentation.screens.statement_import

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.services.StatementImportService
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the M-PESA Statement Import screen.
 *
 * Manages file selection, password entry, import orchestration, and result display.
 */
@HiltViewModel
class StatementImportViewModel @Inject constructor(
    private val application: Application,
    private val statementImportService: StatementImportService
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StatementImportUiState())
    val uiState: StateFlow<StatementImportUiState> = _uiState.asStateFlow()

    /** The selected file URI (set after file picker returns) */
    private var selectedFileUri: Uri? = null

    init {
        // Initialize PDFBox resources (required for pdfbox-android)
        PDFBoxResourceLoader.init(application)
    }

    /**
     * Called when a file is selected from the document picker.
     */
    fun onFileSelected(uri: Uri, fileName: String) {
        selectedFileUri = uri
        _uiState.update {
            it.copy(
                selectedFileName = fileName,
                phase = StatementImportPhase.PASSWORD_ENTRY,
                showPasswordDialog = true,
                error = null
            )
        }
    }

    /**
     * Update the password input field.
     */
    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(passwordInput = password) }
    }

    /**
     * Toggle "remember password" checkbox.
     */
    fun onRememberPasswordChanged(remember: Boolean) {
        _uiState.update { it.copy(rememberPassword = remember) }
    }

    /**
     * Dismiss the password dialog — go back to ready state.
     */
    fun onPasswordDialogDismissed() {
        _uiState.update {
            it.copy(
                showPasswordDialog = false,
                phase = StatementImportPhase.READY,
                passwordInput = ""
            )
        }
    }

    /**
     * Start the import with the entered password.
     */
    fun onStartImport() {
        val uri = selectedFileUri ?: return
        val password = _uiState.value.passwordInput.trim()

        _uiState.update {
            it.copy(
                showPasswordDialog = false,
                phase = StatementImportPhase.IMPORTING,
                progressCurrent = 0,
                progressTotal = 0,
                progressPhase = "Opening PDF...",
                error = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = application.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update {
                        it.copy(
                            phase = StatementImportPhase.ERROR,
                            error = "Could not open the selected file."
                        )
                    }
                    return@launch
                }

                val result = statementImportService.importStatement(
                    inputStream = inputStream,
                    password = password.ifEmpty { null },
                    onProgress = { current, total, phase ->
                        _uiState.update {
                            it.copy(
                                progressCurrent = current,
                                progressTotal = total,
                                progressPhase = phase
                            )
                        }
                    }
                )

                inputStream.close()

                if (result.error != null && result.imported == 0) {
                    _uiState.update {
                        it.copy(
                            phase = StatementImportPhase.ERROR,
                            error = result.error,
                            result = result
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            phase = StatementImportPhase.COMPLETED,
                            result = result
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        phase = StatementImportPhase.ERROR,
                        error = e.message ?: "An unexpected error occurred."
                    )
                }
            }
        }
    }

    /**
     * Reset to the initial state for another import.
     */
    fun onReset() {
        selectedFileUri = null
        _uiState.update { StatementImportUiState() }
    }
}
