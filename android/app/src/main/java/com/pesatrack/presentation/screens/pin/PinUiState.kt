package com.pesatrack.presentation.screens.pin

/**
 * PIN screen mode — determines the UI behavior and title.
 */
enum class PinMode {
    /** Unlock the app — verify against stored PIN. */
    UNLOCK,
    /** First step of setup — enter a new PIN. */
    SETUP_ENTER,
    /** Second step of setup — confirm the new PIN. */
    SETUP_CONFIRM,
    /** Verify current PIN before allowing change. */
    VERIFY_CURRENT,
    /** Verify current PIN before disabling PIN lock. */
    VERIFY_DISABLE,
    /** Enter new PIN after verifying the current one. */
    CHANGE_ENTER,
    /** Confirm new PIN after entering it. */
    CHANGE_CONFIRM
}

/**
 * UI state for the PIN lock / setup screens.
 *
 * @property mode Current PIN flow mode
 * @property enteredDigits Digits entered so far (0–4)
 * @property isError Whether the last PIN entry was wrong (triggers shake + red dots)
 * @property errorMessage Error message to display (e.g., "Incorrect PIN", "PINs don't match")
 * @property attemptsRemaining Remaining attempts before cooldown (max 5)
 * @property cooldownSeconds Seconds remaining in cooldown (0 = no cooldown)
 * @property isSuccess Whether the flow completed successfully (triggers dismiss/navigation)
 * @property biometricAvailable Whether the device supports biometric authentication
 * @property biometricEnabled Whether the user has enabled biometric unlock
 * @property title Title text to show above the dots
 * @property pendingPin The first PIN entry during setup/change (stored temporarily for confirmation)
 */
data class PinUiState(
    val mode: PinMode = PinMode.UNLOCK,
    val enteredDigits: String = "",
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val attemptsRemaining: Int = 5,
    val cooldownSeconds: Int = 0,
    val isSuccess: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val title: String = "Enter your PIN",
    val pendingPin: String? = null
) {
    /** Number of filled dots to show. */
    val filledDots: Int get() = enteredDigits.length

    /** Whether digit input is currently blocked (cooldown active). */
    val isInputBlocked: Boolean get() = cooldownSeconds > 0

    /** Whether to show the biometric button on the unlock screen. */
    val showBiometricButton: Boolean get() = mode == PinMode.UNLOCK && biometricAvailable && biometricEnabled
}
