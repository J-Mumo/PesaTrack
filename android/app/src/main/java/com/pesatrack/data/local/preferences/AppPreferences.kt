package com.pesatrack.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pesatrack.utils.parsers.SmsParserRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pesatrack_preferences")

/**
 * DataStore-based preferences for persisting user settings.
 *
 * Stores:
 * - Enabled bank SMS parsers (M-PESA always on, banks toggleable)
 * - Budget prompt dismissal state
 * - PIN lock settings (hash, enabled, biometric, timeout)
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val QUALIFIED_SESSION_GAP_MS = 5 * 60 * 1000L

        /**
         * Set of enabled bank parser display names (e.g., "NCBA Bank").
         * M-PESA is always enabled and not stored here.
         */
        private val KEY_ENABLED_BANKS = stringSetPreferencesKey("enabled_bank_parsers")

        /**
         * Master toggle for bank SMS tracking.
         * When false, only M-PESA SMS are processed regardless of individual bank toggles.
         */
        private val KEY_BANK_TRACKING_ENABLED = booleanPreferencesKey("bank_tracking_enabled")

        /**
         * Whether the user has dismissed the budget setup prompt on the Home screen.
         * Once dismissed, the prompt does not reappear.
         */
        private val KEY_BUDGET_PROMPT_DISMISSED = booleanPreferencesKey("budget_prompt_dismissed")

        // ── PIN Lock ──

        /** SHA-256 hash of the PIN in format "salt:hash", or null if no PIN set. */
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")

        /** Whether PIN lock is active. */
        private val KEY_PIN_ENABLED = booleanPreferencesKey("pin_enabled")

        /** Whether biometric unlock is enabled (requires PIN to also be enabled). */
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")

        /** Seconds the app must be backgrounded before re-locking. Default 30. */
        private val KEY_LOCK_TIMEOUT_SECONDS = intPreferencesKey("lock_timeout_seconds")

        // ── Onboarding ──

        /** Whether the first-launch onboarding flow has been completed. */
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        /** Timestamp (epoch millis) when the app last went to background. */
        private val KEY_LAST_BACKGROUND_TIMESTAMP = longPreferencesKey("last_background_timestamp")

        // ── Budget ──

        /**
         * Day of the month when the user's budget period starts (1–28).
         * Default 1 = standard calendar month. 25 = "salary on 25th" use case.
         * Capped at 28 to avoid issues with short months.
         */
        private val KEY_MONTH_START_DAY = intPreferencesKey("month_start_day")

        // ── SMS Permission Banner ──

        /**
         * Whether the user has permanently dismissed the SMS permission banner on the Home screen.
         * Respects manual-only users who don't want SMS tracking.
         */
        private val KEY_SMS_BANNER_DISMISSED = booleanPreferencesKey("sms_banner_dismissed")

        // ── Forecast Notifications ──

        /**
         * Key prefix for forecast notification throttle.
         * Stores the last timestamp (epoch millis) a forecast notification was sent for each budget.
         * Dynamic key: "forecast_notif_{budgetId}"
         */
        private const val FORECAST_NOTIF_PREFIX = "forecast_notif_"

        // ── Recurring Expense Reminders ──

        /**
         * Whether recurring expense reminder notifications are enabled.
         * Default: true — users can disable in Settings.
         */
        private val KEY_RECURRING_REMINDERS_ENABLED = booleanPreferencesKey("recurring_reminders_enabled")

        /**
         * Whether the Weekly Review (Insights & Reports v1.0) notification is enabled.
         * Default: true — users can disable in Settings under *Reports & Insights*.
         * See plans/insights-and-reports-plan.md.
         */
        private val KEY_WEEKLY_REVIEW_ENABLED = booleanPreferencesKey("weekly_review_enabled")

        /**
         * Key prefix for recurring notification throttle.
         * Stores the last timestamp (epoch millis) a recurring reminder was sent for each expense.
         * Dynamic key: "recurring_notif_{throttleKey}"
         */
        private const val RECURRING_NOTIF_PREFIX = "recurring_notif_"

        // ── Usage Milestones (Funnel) ──

        /** Epoch millis of first install. Set once, never overwritten. */
        val KEY_INSTALL_TIMESTAMP = longPreferencesKey("install_timestamp")

        val KEY_ONBOARDING_STARTED = longPreferencesKey("onboarding_started")
        val KEY_ONBOARDING_SMS_GRANTED = longPreferencesKey("onboarding_sms_granted")
        val KEY_ONBOARDING_SMS_SKIPPED = longPreferencesKey("onboarding_sms_skipped")
        val KEY_ONBOARDING_IMPORT_CHOSEN = longPreferencesKey("onboarding_import_chosen")
        val KEY_ONBOARDING_IMPORT_SKIPPED = longPreferencesKey("onboarding_import_skipped")
        val KEY_FIRST_SMS_PARSED = longPreferencesKey("first_sms_parsed")
        val KEY_FIRST_IMPORT_COMPLETED = longPreferencesKey("first_import_completed")
        val KEY_FIRST_MANUAL_ENTRY = longPreferencesKey("first_manual_entry")
        val KEY_FIRST_CATEGORIZATION = longPreferencesKey("first_categorization")
        val KEY_FIRST_BUDGET_CREATED = longPreferencesKey("first_budget_created")
        val KEY_FIRST_ANALYTICS_VIEWED = longPreferencesKey("first_analytics_viewed")

        // ── Re-engagement Markers ──

        val KEY_LAST_APP_OPEN = longPreferencesKey("last_app_open")
        val KEY_QUALIFIED_SESSION_COUNT = intPreferencesKey("qualified_session_count")
        val KEY_RAW_LAUNCH_COUNT = intPreferencesKey("raw_launch_count")
        val KEY_RETURN_DAY_1 = longPreferencesKey("return_day_1")
        val KEY_RETURN_DAY_7 = longPreferencesKey("return_day_7")
        val KEY_RETURN_DAY_30 = longPreferencesKey("return_day_30")

        // ── In-App Review Prompt Throttle ──

        val KEY_LAST_REVIEW_PROMPT_TIMESTAMP = longPreferencesKey("last_review_prompt_timestamp")
        val KEY_REVIEW_PROMPT_COUNT = intPreferencesKey("review_prompt_count")

        // ── Stage 1D: Structured Feedback ──

        val KEY_FEEDBACK_PROMPT_SHOWN = booleanPreferencesKey("feedback_prompt_shown")
        val KEY_FEEDBACK_RESPONSE = stringPreferencesKey("feedback_response")

        // ── Stage 1E: Low-Engagement Feedback ──

        val KEY_LOW_ENGAGEMENT_PROMPT_SHOWN = booleanPreferencesKey("low_engagement_prompt_shown")
        val KEY_LOW_ENGAGEMENT_REASON = stringPreferencesKey("low_engagement_reason")
        val KEY_FIRST_VALUE_DEADLINE_CHECKED = booleanPreferencesKey("first_value_deadline_checked")

        // ── Feature Usage Counters ──

        val KEY_COUNT_SMS_PARSED = intPreferencesKey("count_sms_parsed")
        val KEY_COUNT_IMPORTS = intPreferencesKey("count_imports")
        val KEY_COUNT_MANUAL_ENTRIES = intPreferencesKey("count_manual_entries")
        val KEY_COUNT_CATEGORIZATIONS = intPreferencesKey("count_categorizations")
        val KEY_COUNT_BUDGETS_CREATED = intPreferencesKey("count_budgets_created")
        val KEY_COUNT_ANALYTICS_VIEWS = intPreferencesKey("count_analytics_views")
        val KEY_COUNT_FORECAST_VIEWS = intPreferencesKey("count_forecast_views")
        val KEY_COUNT_EXCEL_IMPORTS = intPreferencesKey("count_excel_imports")
        val KEY_COUNT_EXPORTS = intPreferencesKey("count_exports")
        val KEY_COUNT_BACKUPS = intPreferencesKey("count_backups")
    }

    // ==================== Bank SMS Tracking ====================

    /**
     * Whether bank SMS tracking is enabled (master toggle).
     * Default: true — all supported bank parsers are active out of the box.
     * Users can disable in Settings if they don't want bank SMS tracking.
     */
    val bankTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BANK_TRACKING_ENABLED] ?: true
    }

    /**
     * Set of enabled bank parser display names.
     * Default: all non-M-PESA parser names from the registry (all banks enabled).
     */
    val enabledBanks: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENABLED_BANKS] ?: defaultEnabledBanks()
    }

    /**
     * Toggle the master bank tracking switch.
     */
    suspend fun setBankTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BANK_TRACKING_ENABLED] = enabled
        }
    }

    /**
     * Enable or disable a specific bank parser.
     *
     * @param bankName Display name of the bank parser (e.g., "NCBA Bank")
     * @param enabled Whether to enable or disable
     */
    suspend fun setBankEnabled(bankName: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val currentBanks = preferences[KEY_ENABLED_BANKS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                currentBanks.add(bankName)
            } else {
                currentBanks.remove(bankName)
            }
            preferences[KEY_ENABLED_BANKS] = currentBanks
        }
    }

    /**
     * Check if a specific bank is enabled.
     * Default: true for all banks (bank tracking enabled by default).
     */
    suspend fun isBankEnabled(bankName: String): Boolean {
        val prefs = context.dataStore.data.first()
        val bankTrackingOn = prefs[KEY_BANK_TRACKING_ENABLED] ?: true
        if (!bankTrackingOn) return false
        val enabledSet = prefs[KEY_ENABLED_BANKS] ?: defaultEnabledBanks()
        return bankName in enabledSet
    }

    /**
     * Get the set of enabled bank names (snapshot, not Flow).
     * Returns empty set if bank tracking is disabled.
     * Default: all banks enabled.
     */
    suspend fun getEnabledBanksSnapshot(): Set<String> {
        val prefs = context.dataStore.data.first()
        val bankTrackingOn = prefs[KEY_BANK_TRACKING_ENABLED] ?: true
        if (!bankTrackingOn) return emptySet()
        return prefs[KEY_ENABLED_BANKS] ?: defaultEnabledBanks()
    }

    // ==================== Budget Prompt ====================

    /**
     * Whether the budget prompt has been dismissed by the user.
     */
    val budgetPromptDismissed: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BUDGET_PROMPT_DISMISSED] ?: false
    }

    /**
     * Check if budget prompt was dismissed (snapshot).
     */
    suspend fun isBudgetPromptDismissed(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_BUDGET_PROMPT_DISMISSED] ?: false
    }

    /**
     * Dismiss the budget prompt permanently.
     */
    suspend fun dismissBudgetPrompt() {
        context.dataStore.edit { preferences ->
            preferences[KEY_BUDGET_PROMPT_DISMISSED] = true
        }
    }

    // ==================== PIN Lock ====================

    /** Whether PIN lock is enabled. */
    val pinEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PIN_ENABLED] ?: false }

    /** The stored PIN hash ("salt:hash") or null. */
    val pinHash: Flow<String?> = context.dataStore.data.map { it[KEY_PIN_HASH] }

    /** Whether biometric unlock is enabled. */
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BIOMETRIC_ENABLED] ?: false }

    /** Lock timeout in seconds (0 = immediate). */
    val lockTimeoutSeconds: Flow<Int> = context.dataStore.data.map { it[KEY_LOCK_TIMEOUT_SECONDS] ?: 30 }

    /** Last background timestamp (epoch millis). */
    val lastBackgroundTimestamp: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_BACKGROUND_TIMESTAMP] ?: 0L }

    /** Snapshot: is PIN enabled? */
    suspend fun isPinEnabled(): Boolean {
        return context.dataStore.data.first()[KEY_PIN_ENABLED] ?: false
    }

    /** Snapshot: get PIN hash. */
    suspend fun getPinHash(): String? {
        return context.dataStore.data.first()[KEY_PIN_HASH]
    }

    /** Snapshot: is biometric enabled? */
    suspend fun isBiometricEnabled(): Boolean {
        return context.dataStore.data.first()[KEY_BIOMETRIC_ENABLED] ?: false
    }

    /** Snapshot: lock timeout in seconds. */
    suspend fun getLockTimeoutSeconds(): Int {
        return context.dataStore.data.first()[KEY_LOCK_TIMEOUT_SECONDS] ?: 30
    }

    /** Snapshot: last background timestamp. */
    suspend fun getLastBackgroundTimestamp(): Long {
        return context.dataStore.data.first()[KEY_LAST_BACKGROUND_TIMESTAMP] ?: 0L
    }

    /** Save PIN hash and enable PIN lock. */
    suspend fun setPinHash(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hash
            prefs[KEY_PIN_ENABLED] = true
        }
    }

    /** Clear PIN hash and disable PIN lock + biometric. */
    suspend fun clearPin() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PIN_HASH)
            prefs[KEY_PIN_ENABLED] = false
            prefs[KEY_BIOMETRIC_ENABLED] = false
        }
    }

    /** Toggle biometric unlock. */
    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = enabled
        }
    }

    /** Set lock timeout in seconds. */
    suspend fun setLockTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_TIMEOUT_SECONDS] = seconds
        }
    }

    /** Record when the app went to background. */
    suspend fun setLastBackgroundTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKGROUND_TIMESTAMP] = timestamp
        }
    }

    // ==================== Budget ====================

    /**
     * Day of the month when budget periods start (1–28, default 1).
     * Setting to 25 means a "monthly" budget runs from the 25th to the 24th of the next month.
     */
    val monthStartDay: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MONTH_START_DAY] ?: 1
    }

    /** Snapshot: get month start day. */
    suspend fun getMonthStartDay(): Int {
        return context.dataStore.data.first()[KEY_MONTH_START_DAY] ?: 1
    }

    /** Set month start day (1–28). */
    suspend fun setMonthStartDay(day: Int) {
        val clamped = day.coerceIn(1, 28)
        context.dataStore.edit { prefs ->
            prefs[KEY_MONTH_START_DAY] = clamped
        }
    }

    // ==================== Onboarding ====================

    /**
     * Whether the onboarding flow has been completed.
     * Default: false — onboarding shows on first launch.
     */
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    /**
     * Mark onboarding as completed (called when user finishes or skips onboarding).
     */
    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    // ==================== SMS Permission Banner ====================

    /**
     * Whether the SMS permission banner has been permanently dismissed.
     * Respects manual-only users who don't need SMS tracking.
     */
    val smsBannerDismissed: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SMS_BANNER_DISMISSED] ?: false
    }

    /** Snapshot: is SMS banner dismissed? */
    suspend fun isSmsBannerDismissed(): Boolean {
        return context.dataStore.data.first()[KEY_SMS_BANNER_DISMISSED] ?: false
    }

    /** Permanently dismiss the SMS permission banner ("Don't ask again"). */
    suspend fun dismissSmsBanner() {
        context.dataStore.edit { preferences ->
            preferences[KEY_SMS_BANNER_DISMISSED] = true
        }
    }

    // ==================== Forecast Notification Throttle ====================

    /**
     * Get the period key for which a forecast notification was already sent for a budget.
     * Returns empty string if never sent.
     *
     * One-shot per period: a forecast notification fires once when a budget's projection
     * first crosses 75%. The period key ensures it doesn't fire again in the same period.
     * A new period (e.g. next month) automatically resets the trigger.
     */
    suspend fun getForecastNotifPeriodKey(budgetId: Long): String {
        val key = stringPreferencesKey("${FORECAST_NOTIF_PREFIX}${budgetId}_period")
        return context.dataStore.data.first()[key] ?: ""
    }

    /**
     * Record that a forecast notification was sent for a budget in the given period.
     * Prevents re-firing for the same budget in the same period.
     */
    suspend fun setForecastNotifPeriodKey(budgetId: Long, periodKey: String) {
        val key = stringPreferencesKey("${FORECAST_NOTIF_PREFIX}${budgetId}_period")
        context.dataStore.edit { prefs ->
            prefs[key] = periodKey
        }
    }

    /**
     * Check if a forecast notification can be sent for a budget in the given period.
     * Returns true only if no notification has been sent for this budget in this period.
     */
    suspend fun canSendForecastNotification(budgetId: Long, currentPeriodKey: String): Boolean {
        val lastPeriodKey = getForecastNotifPeriodKey(budgetId)
        return lastPeriodKey != currentPeriodKey
    }

    // ==================== Recurring Expense Reminders ====================

    /**
     * Whether recurring expense reminder notifications are enabled.
     * Default: true — users can disable via Settings toggle.
     */
    val recurringRemindersEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_RECURRING_REMINDERS_ENABLED] ?: true
    }

    /** Snapshot: are recurring reminders enabled? */
    suspend fun getRecurringRemindersEnabled(): Boolean {
        return context.dataStore.data.first()[KEY_RECURRING_REMINDERS_ENABLED] ?: true
    }

    /** Toggle recurring reminders on/off. */
    suspend fun setRecurringRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECURRING_REMINDERS_ENABLED] = enabled
        }
    }

    // ==================== Weekly Review (Insights & Reports v1.0) ====================

    /**
     * Whether Weekly Review notifications are enabled.
     * Default: true.
     */
    val weeklyReviewEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WEEKLY_REVIEW_ENABLED] ?: true
    }

    /** Snapshot: are Weekly Review notifications enabled? */
    suspend fun getWeeklyReviewEnabled(): Boolean {
        return context.dataStore.data.first()[KEY_WEEKLY_REVIEW_ENABLED] ?: true
    }

    /** Toggle Weekly Review notifications. */
    suspend fun setWeeklyReviewEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WEEKLY_REVIEW_ENABLED] = enabled
        }
    }

    /**
     * Check if a recurring notification can be sent for a specific expense (throttle).
     * Max 1 notification per recurring expense per cycle (approximated as cycleDays minus 2 days).
     *
     * @param throttleKey Unique key for this notification type + recipient
     * @param cycleDays Expected cycle length in days (7 for weekly, 30 for monthly, etc.)
     */
    suspend fun canSendRecurringNotification(throttleKey: String, cycleDays: Int): Boolean {
        val key = longPreferencesKey("${RECURRING_NOTIF_PREFIX}$throttleKey")
        val lastSent = context.dataStore.data.first()[key] ?: 0L
        val elapsed = System.currentTimeMillis() - lastSent
        // Throttle: at least (cycleDays - 2) days between notifications for the same expense
        val cooldownMs = (cycleDays - 2).coerceAtLeast(1) * 24 * 60 * 60 * 1000L
        return elapsed >= cooldownMs
    }

    /**
     * Record the timestamp when a recurring notification was sent.
     */
    suspend fun setLastRecurringNotifTime(throttleKey: String, timestamp: Long = System.currentTimeMillis()) {
        val key = longPreferencesKey("${RECURRING_NOTIF_PREFIX}$throttleKey")
        context.dataStore.edit { prefs ->
            prefs[key] = timestamp
        }
    }

    // ==================== Usage Milestones ====================

    /**
     * Record the install timestamp. Only sets the value once (if currently 0L).
     * Fire-and-forget — callers just call this and move on.
     */
    suspend fun recordInstallTimestamp() {
        context.dataStore.edit { prefs ->
            if ((prefs[KEY_INSTALL_TIMESTAMP] ?: 0L) == 0L) {
                prefs[KEY_INSTALL_TIMESTAMP] = System.currentTimeMillis()
            }
        }
    }

    /** Snapshot: get install timestamp (0L if not set). */
    suspend fun getInstallTimestamp(): Long {
        return context.dataStore.data.first()[KEY_INSTALL_TIMESTAMP] ?: 0L
    }

    /**
     * Generic helper: record a first-time milestone timestamp.
     * Only sets the value if currently 0L (unset). Fire-and-forget.
     */
    suspend fun recordMilestone(key: Preferences.Key<Long>) {
        context.dataStore.edit { prefs ->
            if ((prefs[key] ?: 0L) == 0L) {
                prefs[key] = System.currentTimeMillis()
            }
        }
    }

    /**
     * Atomically increment an integer counter key.
     * Fire-and-forget — callers just call this and move on.
     */
    suspend fun incrementCounter(key: Preferences.Key<Int>) {
        context.dataStore.edit { prefs ->
            prefs[key] = (prefs[key] ?: 0) + 1
        }
    }

    /**
     * Record an app open event:
     * - Increments raw_launch_count on every call
     * - Increments qualified_session_count only if the previous open is >= 5 minutes ago
     * - Sets last_app_open timestamp
     * - Checks and sets return day markers (D1, D7, D30) based on install_timestamp
     */
    suspend fun recordAppOpen() {
        context.dataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val lastOpen = prefs[KEY_LAST_APP_OPEN] ?: 0L

            // Debug/diagnostic metric: every Activity creation.
            prefs[KEY_RAW_LAUNCH_COUNT] = (prefs[KEY_RAW_LAUNCH_COUNT] ?: 0) + 1

            // Product metric: only count launches separated by a meaningful gap.
            if (lastOpen == 0L || (now - lastOpen) >= QUALIFIED_SESSION_GAP_MS) {
                prefs[KEY_QUALIFIED_SESSION_COUNT] =
                    (prefs[KEY_QUALIFIED_SESSION_COUNT] ?: 0) + 1
            }

            prefs[KEY_LAST_APP_OPEN] = now

            val installTs = prefs[KEY_INSTALL_TIMESTAMP] ?: 0L
            if (installTs > 0L) {
                val elapsed = now - installTs
                val dayMs = 24 * 60 * 60 * 1000L
                if (elapsed >= 1 * dayMs && (prefs[KEY_RETURN_DAY_1] ?: 0L) == 0L) {
                    prefs[KEY_RETURN_DAY_1] = now
                }
                if (elapsed >= 7 * dayMs && (prefs[KEY_RETURN_DAY_7] ?: 0L) == 0L) {
                    prefs[KEY_RETURN_DAY_7] = now
                }
                if (elapsed >= 30 * dayMs && (prefs[KEY_RETURN_DAY_30] ?: 0L) == 0L) {
                    prefs[KEY_RETURN_DAY_30] = now
                }
            }
        }
    }

    /** Snapshot: last review prompt timestamp (0L if never prompted). */
    suspend fun getLastReviewPromptTimestamp(): Long {
        return context.dataStore.data.first()[KEY_LAST_REVIEW_PROMPT_TIMESTAMP] ?: 0L
    }

    /** Snapshot: total number of review prompt attempts. */
    suspend fun getReviewPromptCount(): Int {
        return context.dataStore.data.first()[KEY_REVIEW_PROMPT_COUNT] ?: 0
    }

    /** Snapshot: qualified session count. */
    suspend fun getQualifiedSessionCount(): Int {
        return context.dataStore.data.first()[KEY_QUALIFIED_SESSION_COUNT] ?: 0
    }

    /**
     * Mark that a review prompt attempt occurred.
     * This is used for coarse throttling because Google controls whether UI is actually shown.
     */
    suspend fun markReviewPromptShown() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_REVIEW_PROMPT_TIMESTAMP] = System.currentTimeMillis()
            prefs[KEY_REVIEW_PROMPT_COUNT] = (prefs[KEY_REVIEW_PROMPT_COUNT] ?: 0) + 1
        }
    }

    // ==================== Stage 1D / 1E Feedback Flags ====================

    suspend fun isFeedbackPromptShown(): Boolean {
        return context.dataStore.data.first()[KEY_FEEDBACK_PROMPT_SHOWN] ?: false
    }

    suspend fun markFeedbackPromptShown() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FEEDBACK_PROMPT_SHOWN] = true
        }
    }

    suspend fun saveFeedbackResponse(response: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FEEDBACK_RESPONSE] = response
        }
    }

    suspend fun isLowEngagementPromptShown(): Boolean {
        return context.dataStore.data.first()[KEY_LOW_ENGAGEMENT_PROMPT_SHOWN] ?: false
    }

    suspend fun markLowEngagementPromptShown() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOW_ENGAGEMENT_PROMPT_SHOWN] = true
        }
    }

    suspend fun saveLowEngagementReason(reason: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOW_ENGAGEMENT_REASON] = reason
        }
    }

    suspend fun isFirstValueDeadlineChecked(): Boolean {
        return context.dataStore.data.first()[KEY_FIRST_VALUE_DEADLINE_CHECKED] ?: false
    }

    suspend fun markFirstValueDeadlineChecked() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_VALUE_DEADLINE_CHECKED] = true
        }
    }

    // ==================== Usage Snapshot (Stage 1C) ====================

    data class UsageMetricsSnapshot(
        val installTimestamp: Long,
        val qualifiedSessions: Int,
        val onboardingSmsGranted: Boolean,
        val onboardingSmsSkipped: Boolean,
        val onboardingImportChosen: Boolean,
        val onboardingImportSkipped: Boolean,
        val firstSmsParsed: Boolean,
        val firstImportCompleted: Boolean,
        val firstManualEntry: Boolean,
        val firstCategorization: Boolean,
        val firstBudgetCreated: Boolean,
        val firstAnalyticsViewed: Boolean,
        val returnDay1: Boolean,
        val returnDay7: Boolean,
        val returnDay30: Boolean,
        val countSmsParsed: Int,
        val countImports: Int,
        val countManualEntries: Int,
        val countCategorizations: Int,
        val countBudgetsCreated: Int,
        val countAnalyticsViews: Int,
        val countForecastViews: Int,
        val countExcelImports: Int,
        val countExports: Int,
        val countBackups: Int
    )

    suspend fun getUsageMetricsSnapshot(): UsageMetricsSnapshot {
        val prefs = context.dataStore.data.first()
        return UsageMetricsSnapshot(
            installTimestamp = prefs[KEY_INSTALL_TIMESTAMP] ?: 0L,
            qualifiedSessions = prefs[KEY_QUALIFIED_SESSION_COUNT] ?: 0,
            onboardingSmsGranted = (prefs[KEY_ONBOARDING_SMS_GRANTED] ?: 0L) > 0L,
            onboardingSmsSkipped = (prefs[KEY_ONBOARDING_SMS_SKIPPED] ?: 0L) > 0L,
            onboardingImportChosen = (prefs[KEY_ONBOARDING_IMPORT_CHOSEN] ?: 0L) > 0L,
            onboardingImportSkipped = (prefs[KEY_ONBOARDING_IMPORT_SKIPPED] ?: 0L) > 0L,
            firstSmsParsed = (prefs[KEY_FIRST_SMS_PARSED] ?: 0L) > 0L,
            firstImportCompleted = (prefs[KEY_FIRST_IMPORT_COMPLETED] ?: 0L) > 0L,
            firstManualEntry = (prefs[KEY_FIRST_MANUAL_ENTRY] ?: 0L) > 0L,
            firstCategorization = (prefs[KEY_FIRST_CATEGORIZATION] ?: 0L) > 0L,
            firstBudgetCreated = (prefs[KEY_FIRST_BUDGET_CREATED] ?: 0L) > 0L,
            firstAnalyticsViewed = (prefs[KEY_FIRST_ANALYTICS_VIEWED] ?: 0L) > 0L,
            returnDay1 = (prefs[KEY_RETURN_DAY_1] ?: 0L) > 0L,
            returnDay7 = (prefs[KEY_RETURN_DAY_7] ?: 0L) > 0L,
            returnDay30 = (prefs[KEY_RETURN_DAY_30] ?: 0L) > 0L,
            countSmsParsed = prefs[KEY_COUNT_SMS_PARSED] ?: 0,
            countImports = prefs[KEY_COUNT_IMPORTS] ?: 0,
            countManualEntries = prefs[KEY_COUNT_MANUAL_ENTRIES] ?: 0,
            countCategorizations = prefs[KEY_COUNT_CATEGORIZATIONS] ?: 0,
            countBudgetsCreated = prefs[KEY_COUNT_BUDGETS_CREATED] ?: 0,
            countAnalyticsViews = prefs[KEY_COUNT_ANALYTICS_VIEWS] ?: 0,
            countForecastViews = prefs[KEY_COUNT_FORECAST_VIEWS] ?: 0,
            countExcelImports = prefs[KEY_COUNT_EXCEL_IMPORTS] ?: 0,
            countExports = prefs[KEY_COUNT_EXPORTS] ?: 0,
            countBackups = prefs[KEY_COUNT_BACKUPS] ?: 0
        )
    }

    // ── Milestone convenience methods (delegate to recordMilestone) ──

    suspend fun recordOnboardingStarted() = recordMilestone(KEY_ONBOARDING_STARTED)
    suspend fun recordOnboardingSmsGranted() = recordMilestone(KEY_ONBOARDING_SMS_GRANTED)
    suspend fun recordOnboardingSmsSkipped() = recordMilestone(KEY_ONBOARDING_SMS_SKIPPED)
    suspend fun recordOnboardingImportChosen() = recordMilestone(KEY_ONBOARDING_IMPORT_CHOSEN)
    suspend fun recordOnboardingImportSkipped() = recordMilestone(KEY_ONBOARDING_IMPORT_SKIPPED)
    suspend fun recordFirstSmsParsed() = recordMilestone(KEY_FIRST_SMS_PARSED)
    suspend fun recordFirstImportCompleted() = recordMilestone(KEY_FIRST_IMPORT_COMPLETED)
    suspend fun recordFirstManualEntry() = recordMilestone(KEY_FIRST_MANUAL_ENTRY)
    suspend fun recordFirstCategorization() = recordMilestone(KEY_FIRST_CATEGORIZATION)
    suspend fun recordFirstBudgetCreated() = recordMilestone(KEY_FIRST_BUDGET_CREATED)
    suspend fun recordFirstAnalyticsViewed() = recordMilestone(KEY_FIRST_ANALYTICS_VIEWED)

    // ── Counter convenience methods (delegate to incrementCounter) ──

    suspend fun incrementSmsParsedCount() = incrementCounter(KEY_COUNT_SMS_PARSED)
    suspend fun incrementImportsCount() = incrementCounter(KEY_COUNT_IMPORTS)
    suspend fun incrementManualEntriesCount() = incrementCounter(KEY_COUNT_MANUAL_ENTRIES)
    suspend fun incrementCategorizationsCount() = incrementCounter(KEY_COUNT_CATEGORIZATIONS)
    suspend fun incrementBudgetsCreatedCount() = incrementCounter(KEY_COUNT_BUDGETS_CREATED)
    suspend fun incrementAnalyticsViewsCount() = incrementCounter(KEY_COUNT_ANALYTICS_VIEWS)
    suspend fun incrementForecastViewsCount() = incrementCounter(KEY_COUNT_FORECAST_VIEWS)
    suspend fun incrementExcelImportsCount() = incrementCounter(KEY_COUNT_EXCEL_IMPORTS)
    suspend fun incrementExportsCount() = incrementCounter(KEY_COUNT_EXPORTS)
    suspend fun incrementBackupsCount() = incrementCounter(KEY_COUNT_BACKUPS)

    /**
     * Default set of enabled banks — all non-M-PESA parsers from the registry.
     * Used when the user hasn't explicitly configured bank preferences yet.
     */
    private fun defaultEnabledBanks(): Set<String> {
        return SmsParserRegistry.getAllParserNames()
            .filter { it != "M-PESA" }
            .toSet()
    }
}
