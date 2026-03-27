package com.pesatrack.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pesatrack.utils.parsers.SmsParserRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pesatrack_preferences")

/**
 * DataStore-based preferences for persisting user settings.
 *
 * Stores:
 * - Enabled bank SMS parsers (M-PESA always on, banks toggleable)
 * - Budget prompt dismissal state
 * - PIN lock settings (hash, enabled, biometric, timeout)
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /**
         * Set of enabled bank parser display names (e.g., "NCBA Bank").
         * M-PESA is always enabled and not stored here.
         */
        private val KEY_ENABLED_BANKS = stringSetPreferencesKey("enabled_bank_parsers")

        /**
         * Master toggle for bank SMS tracking.
         * When false, only M-PESA SMS are processed regardless of individual bank toggles.
         */
        private val KEY_BANK_TRACKING_ENABLED = booleanPreferencesKey("bank_tracking_enabled")

        /**
         * Whether the user has dismissed the budget setup prompt on the Home screen.
         * Once dismissed, the prompt does not reappear.
         */
        private val KEY_BUDGET_PROMPT_DISMISSED = booleanPreferencesKey("budget_prompt_dismissed")

        // ── PIN Lock ──

        /** SHA-256 hash of the PIN in format "salt:hash", or null if no PIN set. */
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")

        /** Whether PIN lock is active. */
        private val KEY_PIN_ENABLED = booleanPreferencesKey("pin_enabled")

        /** Whether biometric unlock is enabled (requires PIN to also be enabled). */
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")

        /** Seconds the app must be backgrounded before re-locking. Default 30. */
        private val KEY_LOCK_TIMEOUT_SECONDS = intPreferencesKey("lock_timeout_seconds")

        // ── Onboarding ──

        /** Whether the first-launch onboarding flow has been completed. */
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        /** Timestamp (epoch millis) when the app last went to background. */
        private val KEY_LAST_BACKGROUND_TIMESTAMP = longPreferencesKey("last_background_timestamp")

        // ── Budget ──

        /**
         * Day of the month when the user's budget period starts (1–28).
         * Default 1 = standard calendar month. 25 = "salary on 25th" use case.
         * Capped at 28 to avoid issues with short months.
         */
        private val KEY_MONTH_START_DAY = intPreferencesKey("month_start_day")
    }

    // ==================== Bank SMS Tracking ====================

    /**
     * Whether bank SMS tracking is enabled (master toggle).
     * Default: true — all supported bank parsers are active out of the box.
     * Users can disable in Settings if they don't want bank SMS tracking.
     */
    val bankTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BANK_TRACKING_ENABLED] ?: true
    }

    /**
     * Set of enabled bank parser display names.
     * Default: all non-M-PESA parser names from the registry (all banks enabled).
     */
    val enabledBanks: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENABLED_BANKS] ?: defaultEnabledBanks()
    }

    /**
     * Toggle the master bank tracking switch.
     */
    suspend fun setBankTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BANK_TRACKING_ENABLED] = enabled
        }
    }

    /**
     * Enable or disable a specific bank parser.
     *
     * @param bankName Display name of the bank parser (e.g., "NCBA Bank")
     * @param enabled Whether to enable or disable
     */
    suspend fun setBankEnabled(bankName: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val currentBanks = preferences[KEY_ENABLED_BANKS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                currentBanks.add(bankName)
            } else {
                currentBanks.remove(bankName)
            }
            preferences[KEY_ENABLED_BANKS] = currentBanks
        }
    }

    /**
     * Check if a specific bank is enabled.
     * Default: true for all banks (bank tracking enabled by default).
     */
    suspend fun isBankEnabled(bankName: String): Boolean {
        val prefs = context.dataStore.data.first()
        val bankTrackingOn = prefs[KEY_BANK_TRACKING_ENABLED] ?: true
        if (!bankTrackingOn) return false
        val enabledSet = prefs[KEY_ENABLED_BANKS] ?: defaultEnabledBanks()
        return bankName in enabledSet
    }

    /**
     * Get the set of enabled bank names (snapshot, not Flow).
     * Returns empty set if bank tracking is disabled.
     * Default: all banks enabled.
     */
    suspend fun getEnabledBanksSnapshot(): Set<String> {
        val prefs = context.dataStore.data.first()
        val bankTrackingOn = prefs[KEY_BANK_TRACKING_ENABLED] ?: true
        if (!bankTrackingOn) return emptySet()
        return prefs[KEY_ENABLED_BANKS] ?: defaultEnabledBanks()
    }

    // ==================== Budget Prompt ====================

    /**
     * Whether the budget prompt has been dismissed by the user.
     */
    val budgetPromptDismissed: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BUDGET_PROMPT_DISMISSED] ?: false
    }

    /**
     * Check if budget prompt was dismissed (snapshot).
     */
    suspend fun isBudgetPromptDismissed(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_BUDGET_PROMPT_DISMISSED] ?: false
    }

    /**
     * Dismiss the budget prompt permanently.
     */
    suspend fun dismissBudgetPrompt() {
        context.dataStore.edit { preferences ->
            preferences[KEY_BUDGET_PROMPT_DISMISSED] = true
        }
    }

    // ==================== PIN Lock ====================

    /** Whether PIN lock is enabled. */
    val pinEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PIN_ENABLED] ?: false }

    /** The stored PIN hash ("salt:hash") or null. */
    val pinHash: Flow<String?> = context.dataStore.data.map { it[KEY_PIN_HASH] }

    /** Whether biometric unlock is enabled. */
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BIOMETRIC_ENABLED] ?: false }

    /** Lock timeout in seconds (0 = immediate). */
    val lockTimeoutSeconds: Flow<Int> = context.dataStore.data.map { it[KEY_LOCK_TIMEOUT_SECONDS] ?: 30 }

    /** Last background timestamp (epoch millis). */
    val lastBackgroundTimestamp: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_BACKGROUND_TIMESTAMP] ?: 0L }

    /** Snapshot: is PIN enabled? */
    suspend fun isPinEnabled(): Boolean {
        return context.dataStore.data.first()[KEY_PIN_ENABLED] ?: false
    }

    /** Snapshot: get PIN hash. */
    suspend fun getPinHash(): String? {
        return context.dataStore.data.first()[KEY_PIN_HASH]
    }

    /** Snapshot: is biometric enabled? */
    suspend fun isBiometricEnabled(): Boolean {
        return context.dataStore.data.first()[KEY_BIOMETRIC_ENABLED] ?: false
    }

    /** Snapshot: lock timeout in seconds. */
    suspend fun getLockTimeoutSeconds(): Int {
        return context.dataStore.data.first()[KEY_LOCK_TIMEOUT_SECONDS] ?: 30
    }

    /** Snapshot: last background timestamp. */
    suspend fun getLastBackgroundTimestamp(): Long {
        return context.dataStore.data.first()[KEY_LAST_BACKGROUND_TIMESTAMP] ?: 0L
    }

    /** Save PIN hash and enable PIN lock. */
    suspend fun setPinHash(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hash
            prefs[KEY_PIN_ENABLED] = true
        }
    }

    /** Clear PIN hash and disable PIN lock + biometric. */
    suspend fun clearPin() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PIN_HASH)
            prefs[KEY_PIN_ENABLED] = false
            prefs[KEY_BIOMETRIC_ENABLED] = false
        }
    }

    /** Toggle biometric unlock. */
    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = enabled
        }
    }

    /** Set lock timeout in seconds. */
    suspend fun setLockTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_TIMEOUT_SECONDS] = seconds
        }
    }

    /** Record when the app went to background. */
    suspend fun setLastBackgroundTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKGROUND_TIMESTAMP] = timestamp
        }
    }

    // ==================== Budget ====================

    /**
     * Day of the month when budget periods start (1–28, default 1).
     * Setting to 25 means a "monthly" budget runs from the 25th to the 24th of the next month.
     */
    val monthStartDay: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MONTH_START_DAY] ?: 1
    }

    /** Snapshot: get month start day. */
    suspend fun getMonthStartDay(): Int {
        return context.dataStore.data.first()[KEY_MONTH_START_DAY] ?: 1
    }

    /** Set month start day (1–28). */
    suspend fun setMonthStartDay(day: Int) {
        val clamped = day.coerceIn(1, 28)
        context.dataStore.edit { prefs ->
            prefs[KEY_MONTH_START_DAY] = clamped
        }
    }

    // ==================== Onboarding ====================

    /**
     * Whether the onboarding flow has been completed.
     * Default: false — onboarding shows on first launch.
     */
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    /**
     * Mark onboarding as completed (called when user finishes or skips onboarding).
     */
    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    /**
     * Default set of enabled banks — all non-M-PESA parsers from the registry.
     * Used when the user hasn't explicitly configured bank preferences yet.
     */
    private fun defaultEnabledBanks(): Set<String> {
        return SmsParserRegistry.getAllParserNames()
            .filter { it != "M-PESA" }
            .toSet()
    }
}
