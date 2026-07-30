package com.pesatrack.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.pesatrack.BuildConfig
import com.pesatrack.data.local.database.dao.BudgetDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a compact, PII-free usage snapshot used by the About screen "share"
 * flow and by the in-app feedback prompt. Everything reported here is a count,
 * a boolean, or a permission state — no amounts, no recipient names, no
 * category names, no raw SMS bodies. See AGENTS.md privacy principle.
 *
 * Snapshot fields chosen for feedback triage:
 *  - Retention (D1/D7/D30) reports `pending` when the window hasn't elapsed
 *    yet, so a fresh install doesn't look like a churner.
 *  - `SMS` splits live vs bulk-imported so we can tell whether an inactive
 *    user just onboarded via import and hasn't received a live SMS yet.
 *  - `SMS permission (now)` reflects the *current* OS state, not just what
 *    happened during onboarding — users often change their mind later.
 *  - `First value` = ≥1 non-Miscellaneous categorized expense exists (auto or
 *    manual). Answers "did they get anything useful out of the app?"
 *  - `Backlog / Misc` surface the two most common failure modes: a growing
 *    uncategorized pile and over-aggressive fallback to Miscellaneous.
 */
@Singleton
class UsageSummaryGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao
) {

    suspend fun generate(): String {
        val metrics = appPreferences.getUsageMetricsSnapshot()
        val now = System.currentTimeMillis()
        val elapsedMs = if (metrics.installTimestamp > 0L) {
            (now - metrics.installTimestamp).coerceAtLeast(0L)
        } else {
            -1L
        }
        val installedDays = if (elapsedMs >= 0L) TimeUnit.MILLISECONDS.toDays(elapsedMs) else 0L

        val currentSmsPermission = smsPermissionState()
        val smsStatus = when {
            metrics.onboardingSmsGranted -> "granted"
            metrics.onboardingSmsSkipped -> "skipped"
            else -> "unknown"
        }
        val smsStatusWithCurrent = if (smsStatus == currentSmsPermission) {
            smsStatus
        } else {
            "$smsStatus (now: $currentSmsPermission)"
        }
        val importStatus = when {
            metrics.onboardingImportChosen || metrics.firstImportCompleted -> "completed"
            metrics.onboardingImportSkipped -> "skipped"
            else -> "unknown"
        }

        val liveSms = metrics.countSmsParsed
        val importedSms = metrics.countSmsImported
        val totalSms = liveSms + importedSms

        val firstValueCount = expenseDao.getFirstValueExpenseCount()
        val backlogCount = expenseDao.getUncategorizedBacklogCount()
        val miscCount = expenseDao.getMiscellaneousAutoCatCount()
        val activeBudgetCount = budgetDao.getActiveBudgetCount()

        // Restored users may have imported budgets, categories, and history
        // without ever firing the create-action counters. Only surface the
        // restore line when applicable so the snapshot stays terse for
        // fresh installs.
        val restoreDaysAgo = if (metrics.lastRestoreTimestamp > 0L) {
            TimeUnit.MILLISECONDS.toDays(
                (now - metrics.lastRestoreTimestamp).coerceAtLeast(0L)
            )
        } else {
            -1L
        }

        val features = mutableListOf<String>()
        if (totalSms > 0) features += "SMS"
        if (activeBudgetCount > 0) features += "Budgets"
        if (metrics.countAnalyticsViews > 0) features += "Analytics"
        if (metrics.countExcelImports > 0) features += "Excel"

        val featuresLabel = if (features.isEmpty()) "None yet" else features.joinToString(", ")

        return buildString {
            appendLine("--- PesaTrack Usage Context ---")
            appendLine("v${BuildConfig.VERSION_NAME} | Installed ${installedDays}d ago | ${metrics.qualifiedSessions} qualified sessions")
            appendLine("Onboarding: SMS=$smsStatusWithCurrent, Import=$importStatus")
            if (restoreDaysAgo >= 0L) {
                appendLine("Restored: ${restoreDaysAgo}d ago (create-action counters may under-report)")
            }
            appendLine(
                "Activity: $totalSms SMS ($liveSms live, $importedSms import), ${metrics.countManualEntries} manual, " +
                    "${metrics.countCategorizations} categorized, $activeBudgetCount budgets, " +
                    "${metrics.countAnalyticsViews} analytics views"
            )
            appendLine("First value: ${yesNo(firstValueCount > 0)} (backlog=$backlogCount, misc=$miscCount)")
            appendLine(
                "Return signals: " +
                    "D1=${retentionLabel(metrics.returnDay1, elapsedMs, DAY_MS)} " +
                    "D7=${retentionLabel(metrics.returnDay7, elapsedMs, WEEK_MS)} " +
                    "D30=${retentionLabel(metrics.returnDay30, elapsedMs, MONTH_MS)}"
            )
            append("Features: $featuresLabel")
        }
    }

    suspend fun asJson(): JSONObject {
        val metrics = appPreferences.getUsageMetricsSnapshot()
        val now = System.currentTimeMillis()
        val elapsedMs = if (metrics.installTimestamp > 0L) {
            (now - metrics.installTimestamp).coerceAtLeast(0L)
        } else {
            -1L
        }
        val firstValueCount = expenseDao.getFirstValueExpenseCount()
        val backlogCount = expenseDao.getUncategorizedBacklogCount()
        val miscCount = expenseDao.getMiscellaneousAutoCatCount()
        val activeBudgetCount = budgetDao.getActiveBudgetCount()
        return JSONObject().apply {
            put("installTimestamp", metrics.installTimestamp)
            put("qualifiedSessions", metrics.qualifiedSessions)
            put("onboardingSmsGranted", metrics.onboardingSmsGranted)
            put("onboardingSmsSkipped", metrics.onboardingSmsSkipped)
            put("onboardingImportChosen", metrics.onboardingImportChosen)
            put("onboardingImportSkipped", metrics.onboardingImportSkipped)
            put("smsPermissionCurrent", smsPermissionState())
            put("firstSmsParsed", metrics.firstSmsParsed)
            put("firstImportCompleted", metrics.firstImportCompleted)
            put("firstManualEntry", metrics.firstManualEntry)
            put("firstCategorization", metrics.firstCategorization)
            put("firstBudgetCreated", metrics.firstBudgetCreated)
            put("firstAnalyticsViewed", metrics.firstAnalyticsViewed)
            put("firstValue", firstValueCount > 0)
            put("firstValueCount", firstValueCount)
            put("uncategorizedBacklog", backlogCount)
            put("miscellaneousAutoCat", miscCount)
            put("activeBudgetCount", activeBudgetCount)
            put("lastRestoreTimestamp", metrics.lastRestoreTimestamp)
            put("returnDay1", retentionLabel(metrics.returnDay1, elapsedMs, DAY_MS))
            put("returnDay7", retentionLabel(metrics.returnDay7, elapsedMs, WEEK_MS))
            put("returnDay30", retentionLabel(metrics.returnDay30, elapsedMs, MONTH_MS))
            put("countSmsParsed", metrics.countSmsParsed)
            put("countSmsImported", metrics.countSmsImported)
            put("countImports", metrics.countImports)
            put("countManualEntries", metrics.countManualEntries)
            put("countCategorizations", metrics.countCategorizations)
            put("countBudgetsCreated", metrics.countBudgetsCreated)
            put("countAnalyticsViews", metrics.countAnalyticsViews)
            put("countExcelImports", metrics.countExcelImports)
            put("countExports", metrics.countExports)
            put("countBackups", metrics.countBackups)
        }
    }

    private fun smsPermissionState(): String {
        val read = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val receive = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        return when {
            read && receive -> "granted"
            read || receive -> "partial"
            else -> "denied"
        }
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    /**
     * Report `yes` when the retention flag has fired. Otherwise report
     * `pending` if the retention window hasn't elapsed yet (so a Day-0
     * install doesn't look like a churner), and only report `no` once the
     * window has passed without the user returning.
     */
    private fun retentionLabel(flagFired: Boolean, elapsedMs: Long, windowMs: Long): String {
        if (flagFired) return "yes"
        if (elapsedMs < 0L) return "pending"
        return if (elapsedMs >= windowMs) "no" else "pending"
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val WEEK_MS = 7L * DAY_MS
        private const val MONTH_MS = 30L * DAY_MS
    }
}
