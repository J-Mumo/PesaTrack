package com.pesatrack.utils

import com.pesatrack.BuildConfig
import com.pesatrack.data.local.preferences.AppPreferences
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageSummaryGenerator @Inject constructor(
    private val appPreferences: AppPreferences
) {

    suspend fun generate(): String {
        val metrics = appPreferences.getUsageMetricsSnapshot()
        val now = System.currentTimeMillis()
        val installedDays = if (metrics.installTimestamp > 0L) {
            TimeUnit.MILLISECONDS.toDays((now - metrics.installTimestamp).coerceAtLeast(0L))
        } else {
            0L
        }

        val smsStatus = when {
            metrics.onboardingSmsGranted -> "granted"
            metrics.onboardingSmsSkipped -> "skipped"
            else -> "unknown"
        }
        val importStatus = when {
            metrics.onboardingImportChosen || metrics.firstImportCompleted -> "completed"
            metrics.onboardingImportSkipped -> "skipped"
            else -> "unknown"
        }

        val features = mutableListOf<String>()
        if (metrics.countSmsParsed > 0) features += "SMS"
        if (metrics.countBudgetsCreated > 0) features += "Budgets"
        if (metrics.countAnalyticsViews > 0) features += "Analytics"
        if (metrics.countExcelImports > 0) features += "Excel"

        val featuresLabel = if (features.isEmpty()) "None yet" else features.joinToString(", ")

        return buildString {
            appendLine("--- PesaTrack Usage Context ---")
            appendLine("v${BuildConfig.VERSION_NAME} | Installed ${installedDays}d ago | ${metrics.qualifiedSessions} qualified sessions")
            appendLine("Onboarding: SMS=$smsStatus, Import=$importStatus")
            appendLine(
                "Activity: ${metrics.countSmsParsed} SMS parsed, ${metrics.countManualEntries} manual, " +
                    "${metrics.countCategorizations} categorized, ${metrics.countBudgetsCreated} budgets, " +
                    "${metrics.countAnalyticsViews} analytics views"
            )
            appendLine(
                "Return signals: D1=${yesNo(metrics.returnDay1)} D7=${yesNo(metrics.returnDay7)} D30=${yesNo(metrics.returnDay30)}"
            )
            append("Features: $featuresLabel")
        }
    }

    suspend fun asJson(): JSONObject {
        val metrics = appPreferences.getUsageMetricsSnapshot()
        return JSONObject().apply {
            put("installTimestamp", metrics.installTimestamp)
            put("qualifiedSessions", metrics.qualifiedSessions)
            put("onboardingSmsGranted", metrics.onboardingSmsGranted)
            put("onboardingSmsSkipped", metrics.onboardingSmsSkipped)
            put("onboardingImportChosen", metrics.onboardingImportChosen)
            put("onboardingImportSkipped", metrics.onboardingImportSkipped)
            put("firstSmsParsed", metrics.firstSmsParsed)
            put("firstImportCompleted", metrics.firstImportCompleted)
            put("firstManualEntry", metrics.firstManualEntry)
            put("firstCategorization", metrics.firstCategorization)
            put("firstBudgetCreated", metrics.firstBudgetCreated)
            put("firstAnalyticsViewed", metrics.firstAnalyticsViewed)
            put("returnDay1", metrics.returnDay1)
            put("returnDay7", metrics.returnDay7)
            put("returnDay30", metrics.returnDay30)
            put("countSmsParsed", metrics.countSmsParsed)
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

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
}
