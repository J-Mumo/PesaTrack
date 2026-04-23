package com.pesatrack

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.services.AppLockLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory

/**
 * PesaTrack Application class
 *
 * Initializes Hilt for dependency injection, registers the
 * app-level lifecycle observer for PIN lock management, and
 * configures WorkManager with the Hilt worker factory so that
 * `@HiltWorker`-annotated workers receive their injected deps.
 */
@HiltAndroidApp
class PesaTrackApp : Application(), Configuration.Provider {

    @Inject
    lateinit var appLockLifecycleObserver: AppLockLifecycleObserver

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()

        // Set initial lock state before any Activity starts
        appLockLifecycleObserver.initLockState()

        // Register lifecycle observer to detect background/foreground transitions
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockLifecycleObserver)

        // Record install timestamp (set-once, fire-and-forget)
        CoroutineScope(Dispatchers.IO).launch {
            appPreferences.recordInstallTimestamp()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
