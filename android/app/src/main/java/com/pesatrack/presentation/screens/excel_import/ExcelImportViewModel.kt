package com.pesatrack.presentation.screens.excel_import

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.services.ExcelImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Excel Import screen.
 *
 * Manages the import flow:
 * 1. User selects one or more .xlsx files via Android SAF file picker
 * 2. ViewModel stores the URIs and shows file names
 * 3. User confirms → triggers import via [ExcelImportService]
 * 4. Tracks progress and shows results
 */
@HiltViewModel
class ExcelImportViewModel @Inject constructor(
    private val application: Application,
    private val excelImportService: ExcelImportService
) : ViewModel() {

    companion object {
        private const val TAG = "ExcelImportVM"
    }

    private val _uiState = MutableStateFlow(ExcelImportUiState())
    val uiState: StateFlow<ExcelImportUiState> = _uiState.asStateFlow()

    /** Stored file URIs from the file picker */
    private var selectedUris: List<Uri> = emptyList()

    /**
     * Called when user selects files from the SAF file picker.
     *
     * @param uris List of content URIs for selected .xlsx files
     */
    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return

        selectedUris = uris
        val fileNames = uris.mapNotNull { uri -> getFileName(uri) }

        Log.d(TAG, "Files selected: $fileNames")

        _uiState.update {
            it.copy(
                phase = ExcelImportPhase.FILES_SELECTED,
                selectedFileNames = fileNames,
                error = null
            )
        }
    }

    /**
     * Start the import process with the selected files.
     */
    fun startImport() {
        if (selectedUris.isEmpty()) return

        _uiState.update {
            it.copy(
                phase = ExcelImportPhase.IMPORTING,
                progressCurrent = 0,
                progressTotal = 0,
                progressPhase = "Opening files...",
                result = null,
                error = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Open InputStreams from content URIs
                val fileInputs = selectedUris.mapNotNull { uri ->
                    try {
                        val inputStream = application.contentResolver.openInputStream(uri)
                        val fileName = getFileName(uri) ?: "unknown.xlsx"
                        if (inputStream != null) {
                            ExcelImportService.ExcelFileInput(inputStream, fileName)
                        } else {
                            Log.w(TAG, "Failed to open input stream for: $uri")
                            null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error opening file $uri: ${e.message}")
                        null
                    }
                }

                if (fileInputs.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            phase = ExcelImportPhase.ERROR,
                            error = "Could not open any of the selected files"
                        )
                    }
                    return@launch
                }

                val result = excelImportService.importExcelFiles(
                    files = fileInputs,
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

                // Close all input streams
                fileInputs.forEach { input ->
                    try {
                        input.inputStream.close()
                    } catch (_: Exception) {}
                }

                _uiState.update {
                    it.copy(
                        phase = ExcelImportPhase.COMPLETED,
                        result = result
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excel import failed", e)
                _uiState.update {
                    it.copy(
                        phase = ExcelImportPhase.ERROR,
                        error = e.message ?: "Import failed"
                    )
                }
            }
        }
    }

    /**
     * Reset to ready state (e.g., to import different files).
     */
    fun reset() {
        selectedUris = emptyList()
        _uiState.update { ExcelImportUiState() }
    }

    /**
     * Get the display file name from a content URI.
     */
    private fun getFileName(uri: Uri): String? {
        return try {
            application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }
}
