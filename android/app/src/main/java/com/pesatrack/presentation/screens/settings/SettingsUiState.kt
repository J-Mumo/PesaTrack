package com.pesatrack.presentation.screens.settings

/**
 * UI state for the Settings screen.
 *
 * @property bankTrackingEnabled Master toggle for bank SMS tracking
 * @property availableBanks List of bank parsers with their enabled state
 * @property aiCategorizationEnabled Whether AI-powered categorization is enabled
 * @property geminiApiKey User-provided Gemini API key (empty = use built-in key)
 * @property hasBuiltInApiKey Whether a built-in API key exists in BuildConfig
 * @property isLoading Whether preferences are still loading
 */
data class SettingsUiState(
    val bankTrackingEnabled: Boolean = false,
    val availableBanks: List<BankToggle> = emptyList(),
    val aiCategorizationEnabled: Boolean = true,
    val geminiApiKey: String = "",
    val hasBuiltInApiKey: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * Represents a single bank parser toggle in the settings UI.
 *
 * @property displayName Human-readable name (e.g., "NCBA Bank")
 * @property enabled Whether this bank's SMS tracking is currently enabled
 */
data class BankToggle(
    val displayName: String,
    val enabled: Boolean
)
