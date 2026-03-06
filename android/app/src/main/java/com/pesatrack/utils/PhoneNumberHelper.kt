package com.pesatrack.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper to read the phone number from the device's SIM card.
 * 
 * Requires READ_PHONE_STATE permission (and READ_PHONE_NUMBERS on Android 12+).
 * Not all carriers populate the phone number on the SIM, so this may return null.
 */
@Singleton
class PhoneNumberHelper @Inject constructor(
    private val telephonyManager: TelephonyManager
) {
    
    /**
     * Attempt to read the phone number from the SIM card.
     * Returns the phone number in local format (e.g., 0712345678) or null if unavailable.
     */
    @Suppress("MissingPermission")
    fun getPhoneNumber(context: Context): String? {
        return try {
            if (!hasPermission(context)) return null
            
            val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ - use SubscriptionManager approach if needed
                telephonyManager.line1Number
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.line1Number
            }
            
            number?.let { formatPhoneNumber(it) }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if the required permissions are granted
     */
    fun hasPermission(context: Context): Boolean {
        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        
        // Android 12+ also requires READ_PHONE_NUMBERS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPhoneState && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_NUMBERS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasPhoneState
        }
    }
    
    /**
     * Format phone number to local Kenyan format (07XXXXXXXX)
     */
    private fun formatPhoneNumber(number: String): String? {
        if (number.isBlank()) return null
        
        val cleaned = number.replace(Regex("[^0-9+]"), "")
        
        return when {
            // +254712345678 -> 0712345678
            cleaned.startsWith("+254") && cleaned.length == 13 -> {
                "0${cleaned.substring(4)}"
            }
            // 254712345678 -> 0712345678
            cleaned.startsWith("254") && cleaned.length == 12 -> {
                "0${cleaned.substring(3)}"
            }
            // Already in 07XXXXXXXX format
            cleaned.startsWith("0") && cleaned.length == 10 -> {
                cleaned
            }
            // 712345678 -> 0712345678
            cleaned.length == 9 && !cleaned.startsWith("0") -> {
                "0$cleaned"
            }
            else -> cleaned.ifBlank { null }
        }
    }
}
