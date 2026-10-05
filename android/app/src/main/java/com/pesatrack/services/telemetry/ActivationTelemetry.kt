package com.pesatrack.services.telemetry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pesatrack.data.local.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Activation & retention diagnostics (items A–F of the usage analysis).
 *
 * Problem this solves: onboarding (incl. the SMS permission decision) always
 * runs *before* the telemetry consent sheet, so those events were dropped by
 * the opt-in gate. Choices are already recorded locally in [AppPreferences];
 * this class replays them — **only after the user opts in** — as coarse enums.
 *
 * Privacy: every value sent is an allow-listed enum from [TelemetryEvents].
 * No SMS content, amounts, counterparties or identifiers. When telemetry is
 * disabled, nothing is consumed or sent; pending items wait until opt-in.
 */
@Singleton
class ActivationTelemetry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val telemetryClient: TelemetryClient
) {

    /** Call right after the user opts in (consent sheet or Settings). */
    suspend fun onTelemetryEnabled() {
        if (appPreferences.claimOnboardingSnapshot()) {
            val choices = appPreferences.getOnboardingChoices()
            telemetryClient.logEvent(
                TelemetryEvents.ONBOARDING_SNAPSHOT,
                snapshotParams(choices, System.currentTimeMillis())
            )
        }
        // Force a fresh permission_state for the consenting session.
        appPreferences.resetPermissionStateDay()
        flushPending()
        onAppOpened()
    }

    /** Call once per cold start. Emits permission_state at most once per day. */
    suspend fun onAppOpened() {
        if (!appPreferences.isTelemetryEnabled()) return
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val sms = smsState()
        val notif = notificationState()
        // User properties are cheap and idempotent — keep them current every open.
        telemetryClient.setUserProperty(TelemetryEvents.USER_PROP_SMS_PERM, sms)
        telemetryClient.setUserProperty(TelemetryEvents.USER_PROP_NOTIF_PERM, notif)
        if (appPreferences.claimPermissionStateForDay(today)) {
            telemetryClient.logEvent(
                TelemetryEvents.PERMISSION_STATE,
                mapOf(TelemetryEvents.PARAM_SMS to sms, TelemetryEvents.PARAM_NOTIF to notif)
            )
        }
        flushPending()
    }

    /**
     * (D) Record a completed import/scan. Only the first one with ≥1 new
     * transaction per install is reported. Stored locally until opt-in.
     */
    suspend fun recordScanCompleted(source: String, newTransactions: Int) {
        if (newTransactions <= 0) return
        appPreferences.recordFirstScanPending(source, TelemetryEvents.scanBucket(newTransactions))
        dataSourceFor(source)?.let {
            telemetryClient.setUserProperty(TelemetryEvents.USER_PROP_DATA_SOURCE, it)
        }
        flushPending()
    }

    /** (E) First time a Findings card (real or example) is rendered. */
    suspend fun onInsightShown(kind: String, source: String) {
        if (!appPreferences.isTelemetryEnabled()) return
        if (!appPreferences.claimFirstInsight()) return
        telemetryClient.logEvent(
            TelemetryEvents.FIRST_INSIGHT_SHOWN,
            mapOf(TelemetryEvents.PARAM_KIND to kind, TelemetryEvents.PARAM_SOURCE to source)
        )
    }

    private suspend fun flushPending() {
        if (!appPreferences.isTelemetryEnabled()) return
        appPreferences.consumeFirstScanPending()?.let { (source, bucket) ->
            telemetryClient.logEvent(
                TelemetryEvents.FIRST_SCAN_COMPLETED,
                mapOf(
                    TelemetryEvents.PARAM_SOURCE to source,
                    TelemetryEvents.PARAM_COUNT_BUCKET to bucket
                )
            )
        }
        appPreferences.consumeOnboardingAbandonedStep()?.let { step ->
            telemetryClient.logEvent(
                TelemetryEvents.ONBOARDING_ABANDONED,
                mapOf(TelemetryEvents.PARAM_STEP to TelemetryEvents.onboardingStep(step))
            )
        }
    }

    private fun smsState(): String {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
        return if (granted) TelemetryEvents.STATE_GRANTED else TelemetryEvents.STATE_DENIED
    }

    private fun notificationState(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return TelemetryEvents.STATE_NOT_APPLICABLE
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) TelemetryEvents.STATE_GRANTED else TelemetryEvents.STATE_DENIED
    }

    companion object {
        /** Pure mapping used by (A). Exposed for unit tests. */
        fun snapshotParams(
            choices: AppPreferences.OnboardingChoices,
            nowMs: Long
        ): Map<String, Any> {
            val sms = when {
                choices.smsGranted -> TelemetryEvents.STATE_GRANTED
                choices.smsDenied -> TelemetryEvents.STATE_DENIED
                choices.smsSkipped -> TelemetryEvents.STATE_SKIPPED
                else -> TelemetryEvents.STATE_UNKNOWN
            }
            val import = when {
                choices.importChosen -> TelemetryEvents.STATE_CHOSEN
                choices.importSkipped -> TelemetryEvents.STATE_SKIPPED
                else -> TelemetryEvents.STATE_UNKNOWN
            }
            val days = if (choices.installTimestamp > 0L) {
                TimeUnit.MILLISECONDS.toDays((nowMs - choices.installTimestamp).coerceAtLeast(0L))
            } else 0L
            return mapOf(
                TelemetryEvents.PARAM_SMS to sms,
                TelemetryEvents.PARAM_IMPORT to import,
                TelemetryEvents.PARAM_DAYS_SINCE_INSTALL to TelemetryEvents.daysSinceInstallBucket(days)
            )
        }

        /** Map an import source to the coarse `data_source` user property. */
        fun dataSourceFor(source: String): String? = when (source) {
            TelemetryEvents.SOURCE_SMS -> TelemetryEvents.DATA_SOURCE_SMS
            TelemetryEvents.SOURCE_STATEMENT, TelemetryEvents.SOURCE_EXCEL ->
                TelemetryEvents.DATA_SOURCE_STATEMENT
            else -> null
        }
    }
}
