package com.pesatrack.presentation.screens.settings

/**
 * UI state for the Settings screen.
 *
 * @property bankTrackingEnabled Master toggle for bank SMS tracking
 * @property availableBanks List of bank parsers with their enabled state
 * @property isLoading Whether preferences are still loading
 * @property pinEnabled Whether the app is locked with a PIN
 * @property biometricEnabled Whether biometric unlock is enabled
 * @property biometricAvailable Whether the device supports biometric authentication
 * @property lockTimeoutSeconds Seconds before app re-locks after backgrounding
 * @property isBackingUp Whether a database backup is in progress
 * @property isRestoring Whether a database restore is in progress
 */
data class SettingsUiState(
    val bankTrackingEnabled: Boolean = false,
    val availableBanks: List<BankToggle> = emptyList(),
    val isLoading: Boolean = true,
    // Security
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val lockTimeoutSeconds: Int = 30,
    // Budget
    /** Day of the month when budget periods start (1–28, default 1). */
    val monthStartDay: Int = 1,
    // Insights & Reports (v1.0)
    // Data Management
    val isExporting: Boolean = false,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val isResettingCategories: Boolean = false,
    val isPopulatingSampleData: Boolean = false,
    val dataManagementMessage: String? = null
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
