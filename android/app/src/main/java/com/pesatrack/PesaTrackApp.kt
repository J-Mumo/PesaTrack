package com.pesatrack

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.services.AppLockLifecycleObserver
import com.pesatrack.services.MonthlyReviewWorker
import com.pesatrack.services.NotificationHelper
import com.pesatrack.services.QuarterlyReviewWorker
import com.pesatrack.services.WeeklyReviewWorker
import com.pesatrack.services.YearInReviewWorker
import com.pesatrack.services.telemetry.TelemetryClient
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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

    @Inject
    lateinit var telemetryClient: TelemetryClient

    override fun onCreate() {
        super.onCreate()

        // Initialize PDFBox for M-PESA statement PDF parsing
        PDFBoxResourceLoader.init(applicationContext)

        // Apply the persisted telemetry opt-in state to the client on cold
        // start. Default is false → analytics collection stays off until the
        // user opts in via the consent sheet or Settings toggle.
        CoroutineScope(Dispatchers.IO).launch {
            telemetryClient.setEnabled(appPreferences.isTelemetryEnabled())
        }

        // Set initial lock state before any Activity starts
        appLockLifecycleObserver.initLockState()

        // Register lifecycle observer to detect background/foreground transitions
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockLifecycleObserver)

        // Record install timestamp (set-once, fire-and-forget)
        CoroutineScope(Dispatchers.IO).launch {
            appPreferences.recordInstallTimestamp()
        }

        // Insights & Reports v1.0 — weekly review channel + Thursday 18:00 scheduler.
        // KEEP policy means this is safe to call on every cold start.
        NotificationHelper.createWeeklyReviewChannel(this)
        WeeklyReviewWorker.scheduleWeekly(this)

        // Insights & Reports v1.1 — monthly review channel + 1st-of-month 09:00 scheduler.
        NotificationHelper.createMonthlyReviewChannel(this)
        MonthlyReviewWorker.scheduleMonthly(this)

        // Insights & Reports v1.3 — quarterly review channel + quarter-start scheduler.
        NotificationHelper.createQuarterlyReviewChannel(this)
        NotificationHelper.createBudgetBurnDownChannel(this)
        QuarterlyReviewWorker.scheduleQuarterly(this)

        // Insights & Reports v1.4 — yearly review channel + Dec 28 scheduler.
        NotificationHelper.createYearlyReviewChannel(this)
        YearInReviewWorker.scheduleYearly(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
