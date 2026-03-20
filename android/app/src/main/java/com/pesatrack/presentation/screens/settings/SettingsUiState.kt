package com.pesatrack.presentation.screens.settings

/**
 * UI state for the Settings screen.
 *
 * @property bankTrackingEnabled Master toggle for bank SMS tracking
 * @property availableBanks List of bank parsers with their enabled state
 * @property isLoading Whether preferences are still loading
 */
data class SettingsUiState(
    val bankTrackingEnabled: Boolean = false,
    val availableBanks: List<BankToggle> = emptyList(),
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
