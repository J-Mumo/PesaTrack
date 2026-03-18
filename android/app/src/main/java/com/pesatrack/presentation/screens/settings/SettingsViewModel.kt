package com.pesatrack.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.BuildConfig
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
 * - AI categorization preferences (enabled toggle + Gemini API key)
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
            // Combine all preference flows
            combine(
                appPreferences.bankTrackingEnabled,
                appPreferences.enabledBanks,
                appPreferences.aiCategorizationEnabled,
                appPreferences.geminiApiKey
            ) { trackingEnabled, enabledBanks, aiEnabled, apiKey ->
                // Get all non-MPESA parser names from the registry
                val bankNames = SmsParserRegistry.getAllParserNames()
                    .filter { it != "M-PESA" } // M-PESA is always on, not toggleable

                val bankToggles = bankNames.map { name ->
                    BankToggle(
                        displayName = name,
                        enabled = name in enabledBanks
                    )
                }

                // Check if a built-in API key exists
                val hasBuiltInKey = BuildConfig.GEMINI_API_KEY.isNotBlank()

                SettingsUiState(
                    bankTrackingEnabled = trackingEnabled,
                    availableBanks = bankToggles,
                    aiCategorizationEnabled = aiEnabled,
                    geminiApiKey = apiKey ?: "",
                    hasBuiltInApiKey = hasBuiltInKey,
                    isLoading = false
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

    // ==================== AI Categorization ====================

    /**
     * Toggle AI-powered categorization on/off.
     */
    fun setAiCategorizationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAiCategorizationEnabled(enabled)
        }
    }

    /**
     * Save a user-provided Gemini API key.
     * If empty, clears the stored key (reverts to BuildConfig key).
     */
    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch {
            if (key.isBlank()) {
                appPreferences.clearGeminiApiKey()
            } else {
                appPreferences.saveGeminiApiKey(key.trim())
            }
        }
    }
}
