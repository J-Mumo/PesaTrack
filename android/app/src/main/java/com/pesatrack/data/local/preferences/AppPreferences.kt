package com.pesatrack.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pesatrack_preferences")

/**
 * DataStore-based preferences for persisting user settings.
 * Currently stores the user's phone number for payment auto-fill.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private val KEY_PHONE_NUMBER = stringPreferencesKey("user_phone_number")
    }
    
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
}
