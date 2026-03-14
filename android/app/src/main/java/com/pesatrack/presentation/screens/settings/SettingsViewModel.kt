package com.pesatrack.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.utils.parsers.SmsParserRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Manages bank SMS tracking preferences:
 * - Master toggle for bank tracking
 * - Individual bank parser toggles (NCBA, etc.)
 *
 * Bank list is populated from [SmsParserRegistry], excluding M-PESA
 * (which is always enabled and not toggleable).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
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
            // Combine the two preference flows
            combine(
                appPreferences.bankTrackingEnabled,
                appPreferences.enabledBanks
            ) { trackingEnabled, enabledBanks ->
                // Get all non-MPESA parser names from the registry
                val bankNames = SmsParserRegistry.getAllParserNames()
                    .filter { it != "M-PESA" } // M-PESA is always on, not toggleable

                val bankToggles = bankNames.map { name ->
                    BankToggle(
                        displayName = name,
                        enabled = name in enabledBanks
                    )
                }

                SettingsUiState(
                    bankTrackingEnabled = trackingEnabled,
                    availableBanks = bankToggles,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

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
}
