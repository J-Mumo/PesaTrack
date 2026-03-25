package com.pesatrack

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.pesatrack.services.AppLockLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * PesaTrack Application class
 *
 * Initializes Hilt for dependency injection and registers the
 * app-level lifecycle observer for PIN lock management.
 */
@HiltAndroidApp
class PesaTrackApp : Application() {

    @Inject
    lateinit var appLockLifecycleObserver: AppLockLifecycleObserver

    override fun onCreate() {
        super.onCreate()

        // Set initial lock state before any Activity starts
        appLockLifecycleObserver.initLockState()

        // Register lifecycle observer to detect background/foreground transitions
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockLifecycleObserver)
    }
}
