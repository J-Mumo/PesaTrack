package com.pesatrack.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.services.DataManagementService
import com.pesatrack.services.SampleDataService
import com.pesatrack.utils.parsers.SmsParserRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Manages:
 * - Bank SMS tracking preferences (master toggle + individual bank toggles)
 * - PIN lock settings (enabled, biometric toggle, lock timeout)
 * - Data management (reset categories, export CSV, sample data)
 *
 * Bank list is populated from [SmsParserRegistry], excluding M-PESA
 * (which is always enabled and not toggleable).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val dataManagementService: DataManagementService,
    private val sampleDataService: SampleDataService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * Load settings from DataStore and populate UI state.
     */
    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                appPreferences.bankTrackingEnabled,
                appPreferences.enabledBanks,
                appPreferences.pinEnabled,
                appPreferences.biometricEnabled,
                appPreferences.lockTimeoutSeconds,
                appPreferences.monthStartDay
            ) { values ->
                val trackingEnabled = values[0] as Boolean
                val enabledBanks = @Suppress("UNCHECKED_CAST") (values[1] as Set<String>)
                val pinEnabled = values[2] as Boolean
                val biometricEnabled = values[3] as Boolean
                val lockTimeout = values[4] as Int
                val monthStartDay = values[5] as Int

                // Get all non-MPESA parser names from the registry
                val bankNames = SmsParserRegistry.getAllParserNames()
                    .filter { it != "M-PESA" } // M-PESA is always on, not toggleable

                val bankToggles = bankNames.map { name ->
                    BankToggle(
                        displayName = name,
                        enabled = name in enabledBanks
                    )
                }

                _uiState.value.copy(
                    bankTrackingEnabled = trackingEnabled,
                    availableBanks = bankToggles,
                    isLoading = false,
                    pinEnabled = pinEnabled,
                    biometricEnabled = biometricEnabled,
                    lockTimeoutSeconds = lockTimeout,
                    monthStartDay = monthStartDay
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    // ==================== Bank SMS Tracking ====================

    /**
     * Toggle the master bank tracking switch.
     */
    fun setBankTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setBankTrackingEnabled(enabled)
        }
    }

    /**
     * Toggle a specific bank's SMS tracking.
     */
    fun setBankEnabled(bankName: String, enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setBankEnabled(bankName, enabled)
        }
    }

    // ==================== PIN Lock ====================

    /**
     * Toggle biometric unlock.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setBiometricEnabled(enabled)
        }
    }

    /**
     * Set the lock timeout in seconds.
     */
    fun setLockTimeout(seconds: Int) {
        viewModelScope.launch {
            appPreferences.setLockTimeoutSeconds(seconds)
        }
    }

    /**
     * Set biometric availability (called from UI layer after checking BiometricManager).
     */
    fun setBiometricAvailable(available: Boolean) {
        _uiState.value = _uiState.value.copy(biometricAvailable = available)
    }

    // ==================== Budget Settings ====================

    /**
     * Set the day of the month when budget periods start (1–28).
     */
    fun setMonthStartDay(day: Int) {
        viewModelScope.launch {
            appPreferences.setMonthStartDay(day)
        }
    }

    // ==================== Data Management ====================

    /**
     * The last exported CSV file, kept in memory for the share intent.
     */
    private var _lastExportedFile: File? = null
    val lastExportedFile: File? get() = _lastExportedFile

    /**
     * Reset all categories to defaults.
     * Deletes custom categories + all auto-categorization rules.
     * Expenses with custom categories become uncategorized (FK SET_NULL).
     */
    fun resetCategoriesToDefault() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResettingCategories = true, dataManagementMessage = null)
            try {
                dataManagementService.resetCategoriesToDefault()
                _uiState.value = _uiState.value.copy(
                    isResettingCategories = false,
                    dataManagementMessage = "Categories reset to defaults"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResettingCategories = false,
                    dataManagementMessage = "Failed to reset categories: ${e.message}"
                )
            }
            // Auto-clear message after 3 seconds
            delay(3000)
            _uiState.value = _uiState.value.copy(dataManagementMessage = null)
        }
    }

    /**
     * Export all expenses to a CSV file.
     *
     * @param context Needed for cache directory access
     */
    fun exportData(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, dataManagementMessage = null)
            try {
                val file = dataManagementService.exportExpensesToCsv(context)
                if (file != null) {
                    _lastExportedFile = file
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        dataManagementMessage = "Export ready — opening share…"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        dataManagementMessage = "No expenses to export"
                    )
                    delay(3000)
                    _uiState.value = _uiState.value.copy(dataManagementMessage = null)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    dataManagementMessage = "Export failed: ${e.message}"
                )
                delay(3000)
                _uiState.value = _uiState.value.copy(dataManagementMessage = null)
            }
        }
    }

    /**
     * Populate the database with sample data for demo/screenshot purposes.
     */
    fun populateSampleData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPopulatingSampleData = true, dataManagementMessage = null)
            try {
                sampleDataService.populateSampleData()
                _uiState.value = _uiState.value.copy(
                    isPopulatingSampleData = false,
                    dataManagementMessage = "Sample data populated successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPopulatingSampleData = false,
                    dataManagementMessage = "Failed to populate sample data: ${e.message}"
                )
            }
            delay(3000)
            _uiState.value = _uiState.value.copy(dataManagementMessage = null)
        }
    }

    /**
     * Clear all expense, budget, and income data.
     */
    fun clearAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPopulatingSampleData = true, dataManagementMessage = null)
            try {
                sampleDataService.clearAllData()
                _uiState.value = _uiState.value.copy(
                    isPopulatingSampleData = false,
                    dataManagementMessage = "All data cleared successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPopulatingSampleData = false,
                    dataManagementMessage = "Failed to clear data: ${e.message}"
                )
            }
            delay(3000)
            _uiState.value = _uiState.value.copy(dataManagementMessage = null)
        }
    }

    /**
     * Create a share intent for the last exported CSV file.
     */
    fun createShareIntent(context: Context): android.content.Intent? {
        val file = _lastExportedFile ?: return null
        return dataManagementService.createShareIntent(context, file)
    }

    /**
     * Clear the data management message.
     */
    fun clearDataManagementMessage() {
        _uiState.value = _uiState.value.copy(dataManagementMessage = null)
    }
}
