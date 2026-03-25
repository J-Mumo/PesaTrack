package com.pesatrack.presentation.screens.pin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.services.AppLockLifecycleObserver
import com.pesatrack.services.PinManager
import com.pesatrack.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for PIN lock and setup screens.
 *
 * Handles:
 * - PIN digit input and validation
 * - Setup flow (enter → confirm → save)
 * - Change flow (verify current → enter new → confirm → save)
 * - Disable flow (verify current → clear PIN)
 * - Brute force protection (5 attempts → 30s cooldown)
 * - Biometric availability detection
 */
@HiltViewModel
class PinViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val appPreferences: AppPreferences,
    private val appLockLifecycleObserver: AppLockLifecycleObserver,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val MAX_PIN_LENGTH = 4
        private const val MAX_ATTEMPTS = 5
        private const val COOLDOWN_SECONDS = 30
    }

    private val _uiState = MutableStateFlow(PinUiState())
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    /**
     * Initialize the ViewModel for a specific mode.
     * Call this from the composable after determining the flow.
     */
    fun initialize(mode: PinMode) {
        viewModelScope.launch {
            val biometricEnabled = appPreferences.isBiometricEnabled()
            _uiState.value = PinUiState(
                mode = mode,
                title = getTitleForMode(mode),
                biometricEnabled = biometricEnabled
            )
        }
    }

    /**
     * Set whether biometric hardware is available on this device.
     * Called from the Activity/Composable after checking BiometricManager.
     */
    fun setBiometricAvailable(available: Boolean) {
        _uiState.value = _uiState.value.copy(biometricAvailable = available)
    }

    /**
     * Append a digit to the PIN entry.
     * Auto-submits when 4 digits are entered.
     */
    fun onDigitEntered(digit: Int) {
        val current = _uiState.value
        if (current.isInputBlocked) return
        if (current.enteredDigits.length >= MAX_PIN_LENGTH) return

        val newDigits = current.enteredDigits + digit.toString()
        _uiState.value = current.copy(
            enteredDigits = newDigits,
            isError = false,
            errorMessage = null
        )

        // Auto-submit when 4 digits entered
        if (newDigits.length == MAX_PIN_LENGTH) {
            viewModelScope.launch {
                // Small delay so the user sees the 4th dot fill
                delay(150)
                onPinComplete(newDigits)
            }
        }
    }

    /**
     * Delete the last entered digit.
     */
    fun onBackspace() {
        val current = _uiState.value
        if (current.isInputBlocked) return
        if (current.enteredDigits.isEmpty()) return

        _uiState.value = current.copy(
            enteredDigits = current.enteredDigits.dropLast(1),
            isError = false,
            errorMessage = null
        )
    }

    /**
     * Called when biometric authentication succeeds.
     */
    fun onBiometricSuccess() {
        appLockLifecycleObserver.unlock()
        _uiState.value = _uiState.value.copy(isSuccess = true)
    }

    /**
     * Handle a completed 4-digit PIN based on the current mode.
     */
    private suspend fun onPinComplete(pin: String) {
        when (_uiState.value.mode) {
            PinMode.UNLOCK -> verifyUnlock(pin)
            PinMode.SETUP_ENTER -> setupEnter(pin)
            PinMode.SETUP_CONFIRM -> setupConfirm(pin)
            PinMode.VERIFY_CURRENT -> verifyCurrent(pin)
            PinMode.VERIFY_DISABLE -> verifyDisable(pin)
            PinMode.CHANGE_ENTER -> changeEnter(pin)
            PinMode.CHANGE_CONFIRM -> changeConfirm(pin)
        }
    }

    // ==================== Unlock Flow ====================

    private suspend fun verifyUnlock(pin: String) {
        val correct = pinManager.verifyStoredPin(pin)
        if (correct) {
            appLockLifecycleObserver.unlock()
            _uiState.value = _uiState.value.copy(isSuccess = true)
        } else {
            handleWrongPin()
        }
    }

    // ==================== Setup Flow ====================

    private fun setupEnter(pin: String) {
        // Store the first entry and move to confirm step
        _uiState.value = _uiState.value.copy(
            mode = PinMode.SETUP_CONFIRM,
            title = "Confirm your PIN",
            enteredDigits = "",
            pendingPin = pin
        )
    }

    private suspend fun setupConfirm(pin: String) {
        val pendingPin = _uiState.value.pendingPin
        if (pin == pendingPin) {
            // PINs match — save
            pinManager.savePin(pin)
            _uiState.value = _uiState.value.copy(isSuccess = true)
        } else {
            // PINs don't match — restart setup
            _uiState.value = _uiState.value.copy(
                mode = PinMode.SETUP_ENTER,
                title = "Enter a 4-digit PIN",
                enteredDigits = "",
                pendingPin = null,
                isError = true,
                errorMessage = "PINs don't match. Try again."
            )
            clearErrorAfterDelay()
        }
    }

    // ==================== Disable Flow ====================

    private suspend fun verifyDisable(pin: String) {
        val correct = pinManager.verifyStoredPin(pin)
        if (correct) {
            // Current PIN verified — clear PIN and disable lock
            pinManager.clearPin()
            _uiState.value = _uiState.value.copy(isSuccess = true)
        } else {
            handleWrongPin()
        }
    }

    // ==================== Change Flow ====================

    private suspend fun verifyCurrent(pin: String) {
        val correct = pinManager.verifyStoredPin(pin)
        if (correct) {
            // Current PIN verified — move to enter new PIN
            _uiState.value = _uiState.value.copy(
                mode = PinMode.CHANGE_ENTER,
                title = "Enter new PIN",
                enteredDigits = "",
                isError = false,
                errorMessage = null
            )
        } else {
            handleWrongPin()
        }
    }

    private fun changeEnter(pin: String) {
        _uiState.value = _uiState.value.copy(
            mode = PinMode.CHANGE_CONFIRM,
            title = "Confirm new PIN",
            enteredDigits = "",
            pendingPin = pin
        )
    }

    private suspend fun changeConfirm(pin: String) {
        val pendingPin = _uiState.value.pendingPin
        if (pin == pendingPin) {
            pinManager.savePin(pin)
            _uiState.value = _uiState.value.copy(isSuccess = true)
        } else {
            _uiState.value = _uiState.value.copy(
                mode = PinMode.CHANGE_ENTER,
                title = "Enter new PIN",
                enteredDigits = "",
                pendingPin = null,
                isError = true,
                errorMessage = "PINs don't match. Try again."
            )
            clearErrorAfterDelay()
        }
    }

    // ==================== Brute Force Protection ====================

    private fun handleWrongPin() {
        val current = _uiState.value
        val newAttempts = current.attemptsRemaining - 1

        if (newAttempts <= 0) {
            // Start cooldown
            _uiState.value = current.copy(
                enteredDigits = "",
                isError = true,
                errorMessage = "Too many attempts",
                attemptsRemaining = 0,
                cooldownSeconds = COOLDOWN_SECONDS
            )
            startCooldown()
        } else {
            _uiState.value = current.copy(
                enteredDigits = "",
                isError = true,
                errorMessage = "Incorrect PIN",
                attemptsRemaining = newAttempts
            )
            clearErrorAfterDelay()
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var remaining = COOLDOWN_SECONDS
            while (remaining > 0) {
                _uiState.value = _uiState.value.copy(cooldownSeconds = remaining)
                delay(1000)
                remaining--
            }
            // Cooldown complete — reset attempts
            _uiState.value = _uiState.value.copy(
                cooldownSeconds = 0,
                attemptsRemaining = MAX_ATTEMPTS,
                isError = false,
                errorMessage = null
            )
        }
    }

    private fun clearErrorAfterDelay() {
        viewModelScope.launch {
            delay(1500)
            _uiState.value = _uiState.value.copy(
                isError = false,
                errorMessage = null
            )
        }
    }

    // ==================== Helpers ====================

    private fun getTitleForMode(mode: PinMode): String = when (mode) {
        PinMode.UNLOCK -> "Enter your PIN"
        PinMode.SETUP_ENTER -> "Enter a 4-digit PIN"
        PinMode.SETUP_CONFIRM -> "Confirm your PIN"
        PinMode.VERIFY_CURRENT -> "Enter current PIN"
        PinMode.VERIFY_DISABLE -> "Enter current PIN to disable"
        PinMode.CHANGE_ENTER -> "Enter new PIN"
        PinMode.CHANGE_CONFIRM -> "Confirm new PIN"
    }
}
