package com.pesatrack.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
 * - User phone number (payment auto-fill)
 * - Enabled bank SMS parsers (M-PESA always on, banks toggleable)
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
