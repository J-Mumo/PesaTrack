package com.pesatrack.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
 * - User phone number (payment auto-fill)
 * - Enabled bank SMS parsers (M-PESA always on, banks toggleable)
 * - AI categorization settings (enabled toggle, Gemini API key)
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_PHONE_NUMBER = stringPreferencesKey("user_phone_number")

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
         * Whether AI-powered categorization is enabled.
         * When true, the "AI Suggest" button appears in Batch Categorize.
         */
        private val KEY_AI_CATEGORIZATION_ENABLED = booleanPreferencesKey("ai_categorization_enabled")

        /**
         * User-provided Gemini API key (entered at runtime via Settings).
         * Takes priority over BuildConfig.GEMINI_API_KEY.
         */
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }

    // ==================== Phone Number ====================

    /**
     * Get the stored phone number as a Flow
     */
    val phoneNumber: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_PHONE_NUMBER]
    }

    /**
     * Save the user's phone number
     */
    suspend fun savePhoneNumber(phoneNumber: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PHONE_NUMBER] = phoneNumber
        }
    }

    /**
     * Clear the stored phone number
     */
    suspend fun clearPhoneNumber() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_PHONE_NUMBER)
        }
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

    // ==================== AI Categorization ====================

    /**
     * Whether AI-powered categorization is enabled.
     * Default: true — the AI Suggest button is shown in Batch Categorize.
     */
    val aiCategorizationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AI_CATEGORIZATION_ENABLED] ?: true
    }

    /**
     * Toggle AI categorization on/off.
     */
    suspend fun setAiCategorizationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AI_CATEGORIZATION_ENABLED] = enabled
        }
    }

    /**
     * User-provided Gemini API key as a Flow.
     * Null/empty means use BuildConfig.GEMINI_API_KEY as fallback.
     */
    val geminiApiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_GEMINI_API_KEY]
    }

    /**
     * Save a user-provided Gemini API key.
     */
    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GEMINI_API_KEY] = key
        }
    }

    /**
     * Clear the user-provided Gemini API key (reverts to BuildConfig key).
     */
    suspend fun clearGeminiApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_GEMINI_API_KEY)
        }
    }

    /**
     * Get the Gemini API key snapshot (not Flow).
     * Returns user-provided key if set, otherwise null.
     */
    suspend fun getGeminiApiKeySnapshot(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_GEMINI_API_KEY]
    }
}
