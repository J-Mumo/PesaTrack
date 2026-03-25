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
 * Manages:
 * - Bank SMS tracking preferences (master toggle + individual bank toggles)
 * - PIN lock settings (enabled, biometric toggle, lock timeout)
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
            combine(
                appPreferences.bankTrackingEnabled,
                appPreferences.enabledBanks,
                appPreferences.pinEnabled,
                appPreferences.biometricEnabled,
                appPreferences.lockTimeoutSeconds
            ) { trackingEnabled, enabledBanks, pinEnabled, biometricEnabled, lockTimeout ->
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
                    isLoading = false,
                    pinEnabled = pinEnabled,
                    biometricEnabled = biometricEnabled,
                    lockTimeoutSeconds = lockTimeout
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
}
