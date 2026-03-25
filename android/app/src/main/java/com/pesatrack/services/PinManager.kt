package com.pesatrack.services

import com.pesatrack.data.local.preferences.AppPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages PIN hashing, verification, and lock timeout logic.
 *
 * PIN is stored as "salt:hash" where:
 * - salt = 16 random hex characters
 * - hash = SHA-256(salt + pin) as hex string
 *
 * This prevents rainbow-table attacks on the 4-digit PIN space.
 */
@Singleton
class PinManager @Inject constructor(
    private val appPreferences: AppPreferences
) {

    companion object {
        private const val SALT_LENGTH = 8 // 8 bytes = 16 hex chars
    }

    /**
     * Hash a PIN with a random salt.
     * @return "salt:hash" string for storage
     */
    fun hashPin(pin: String): String {
        val salt = generateSalt()
        val hash = sha256(salt + pin)
        return "$salt:$hash"
    }

    /**
     * Verify a PIN against a stored "salt:hash" string.
     * @param pin The PIN to verify
     * @param storedHash The stored "salt:hash" value
     * @return true if the PIN matches
     */
    fun verifyPin(pin: String, storedHash: String): Boolean {
        val parts = storedHash.split(":", limit = 2)
        if (parts.size != 2) return false
        val salt = parts[0]
        val expectedHash = parts[1]
        val actualHash = sha256(salt + pin)
        return actualHash == expectedHash
    }

    /**
     * Check whether the app should be locked based on background duration.
     *
     * @param lastBackgroundTimestamp When the app went to background (epoch millis)
     * @param timeoutSeconds Lock timeout in seconds (0 = immediate)
     * @return true if enough time has passed that the app should be locked
     */
    fun shouldLock(lastBackgroundTimestamp: Long, timeoutSeconds: Int): Boolean {
        if (lastBackgroundTimestamp == 0L) return true // First launch or no record → lock
        val elapsed = System.currentTimeMillis() - lastBackgroundTimestamp
        return elapsed >= timeoutSeconds * 1000L
    }

    /**
     * Save a new PIN hash to preferences.
     */
    suspend fun savePin(pin: String) {
        val hash = hashPin(pin)
        appPreferences.setPinHash(hash)
    }

    /**
     * Verify a PIN against the stored hash.
     * @return true if correct, false if wrong or no PIN set
     */
    suspend fun verifyStoredPin(pin: String): Boolean {
        val storedHash = appPreferences.getPinHash() ?: return false
        return verifyPin(pin, storedHash)
    }

    /**
     * Remove pin and disable lock.
     */
    suspend fun clearPin() {
        appPreferences.clearPin()
    }

    // ==================== Private Helpers ====================

    private fun generateSalt(): String {
        val bytes = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
