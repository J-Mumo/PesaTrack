package com.pesatrack.services

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pesatrack.data.local.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes app-level lifecycle (via ProcessLifecycleOwner) to manage PIN lock state.
 *
 * - ON_STOP: records the background timestamp
 * - ON_START: checks if enough time has passed to require re-authentication
 *
 * Exposes [isLocked] as a StateFlow that the UI layer observes.
 */
@Singleton
class AppLockLifecycleObserver @Inject constructor(
    private val appPreferences: AppPreferences,
    private val pinManager: PinManager
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isLocked = MutableStateFlow(false)

    /** Whether the app is currently locked and requires PIN/biometric to access. */
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    /** Set to true after the first onStart to distinguish cold start from resume. */
    private var hasStartedBefore = false

    /**
     * Call once during Application.onCreate() to set initial lock state
     * before any Activity starts.
     */
    fun initLockState() {
        scope.launch {
            val pinEnabled = appPreferences.isPinEnabled()
            _isLocked.value = pinEnabled // Lock on cold start if PIN is set
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (!hasStartedBefore) {
            // Cold start — already handled by initLockState()
            hasStartedBefore = true
            return
        }
        // Resumed from background — check timeout
        scope.launch {
            val pinEnabled = appPreferences.isPinEnabled()
            if (!pinEnabled) {
                _isLocked.value = false
                return@launch
            }
            val lastBackground = appPreferences.getLastBackgroundTimestamp()
            val timeout = appPreferences.getLockTimeoutSeconds()
            if (pinManager.shouldLock(lastBackground, timeout)) {
                _isLocked.value = true
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // Record when the app went to background
        scope.launch {
            appPreferences.setLastBackgroundTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Called by the PIN entry screen after successful authentication.
     */
    fun unlock() {
        _isLocked.value = false
    }

    /**
     * Force lock (e.g., from a "Lock Now" action).
     */
    fun lock() {
        scope.launch {
            val pinEnabled = appPreferences.isPinEnabled()
            if (pinEnabled) {
                _isLocked.value = true
            }
        }
    }
}
