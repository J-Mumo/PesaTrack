package com.pesatrack.utils

/**
 * App-wide constants
 */
object Constants {
    
    // Notification channels
    const val NOTIFICATION_CHANNEL_ID = "pesatrack_expenses"
    const val NOTIFICATION_CHANNEL_NAME = "Expense Notifications"
    
    // Intent extras
    const val EXTRA_EXPENSE_ID = "expense_id"
    
    // Payment status polling
    const val PAYMENT_POLL_INTERVAL_MS = 2000L
    const val PAYMENT_POLL_MAX_ATTEMPTS = 30
    
    // Preferences keys
    const val PREF_SMS_PERMISSION_REQUESTED = "sms_permission_requested"
    const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
}

/**
 * Format amount as KES currency string
 */
fun Double.formatAsCurrency(): String {
    return "KES ${String.format("%,.2f", this)}"
}

/**
 * Format phone number for display
 */
fun String.formatPhoneNumber(): String {
    val cleaned = this.replace(Regex("[^0-9]"), "")
    return when {
        cleaned.length == 12 && cleaned.startsWith("254") -> {
            "+254 ${cleaned.substring(3, 6)} ${cleaned.substring(6, 9)} ${cleaned.substring(9)}"
        }
        cleaned.length == 10 && cleaned.startsWith("0") -> {
            "0${cleaned.substring(1, 4)} ${cleaned.substring(4, 7)} ${cleaned.substring(7)}"
        }
        else -> this
    }
}
