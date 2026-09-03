package com.pesatrack.services.telemetry

/**
 * Allow-listed telemetry event names + parameter keys + parameter values.
 *
 * **Only** the names declared here may be sent. Any new event must be added
 * to this file, reviewed against the "no PII, no financial data" rule from
 * AGENTS.md, and documented in _docs/implementation-status.md.
 *
 * Parameter values must never include:
 *  - Transaction amounts, counterparties, SMS bodies, transaction IDs
 *  - User-entered category names, budget names, or note text
 *  - Anything that could identify the user (phone number, email, install ID
 *    beyond Firebase's rotating App Instance ID)
 *
 * For raw counts (imports, batch operations), use [countBucket] instead of
 * the exact number so a rare bucket size can't be traced back to one user.
 *
 * See plans/product-principles.md and AGENTS.md ("Privacy is non-negotiable").
 */
object TelemetryEvents {
    // ==================== Phase 1: consent lifecycle ====================
    /** Fired once per cold start after consent state has been resolved. */
    const val APP_OPENED = "app_opened"

    /** Fired when the user opts in to telemetry (consent sheet or Settings). */
    const val TELEMETRY_ENABLED = "telemetry_enabled"

    /** Fired when the user opts out. Sent right before collection is turned off. */
    const val TELEMETRY_DISABLED = "telemetry_disabled"

    /** Fired when the consent sheet is shown to the user (one-time). */
    const val TELEMETRY_PROMPT_SHOWN = "telemetry_prompt_shown"

    /** Fired when the user dismisses the consent sheet without opting in. */
    const val TELEMETRY_PROMPT_DISMISSED = "telemetry_prompt_dismissed"

    // ==================== Phase 2: engagement ====================

    /**
     * Fired when the user navigates to a new destination.
     * Params: [PARAM_SCREEN] — route base (no arguments), e.g. `"home"`,
     * `"analytics"`, `"weekly_review"`.
     */
    const val SCREEN_VIEWED = "screen_viewed"

    /**
     * Fired after an SMS has been successfully parsed and persisted
     * (either as an expense or as an income). Fires from live receive and
     * from historical import.
     * Params: [PARAM_SOURCE] — parser display name (`"M-PESA"`, `"NCBA"`),
     *         [PARAM_KIND]   — `KIND_EXPENSE` or `KIND_INCOME`.
     */
    const val SMS_PARSED = "sms_parsed"

    /** User categorized a single expense from the Categorize screen. */
    const val EXPENSE_CATEGORIZED_MANUAL = "expense_categorized_manual"

    /**
     * User categorized a group / bulk selection in Batch Categorize.
     * Params: [PARAM_COUNT_BUCKET].
     */
    const val EXPENSE_CATEGORIZED_BATCH = "expense_categorized_batch"

    /** User saved a manual (non-SMS) expense from Manual Entry. */
    const val EXPENSE_MANUAL_ADDED = "expense_manual_added"

    /**
     * User started an import.
     * Params: [PARAM_SOURCE] — `SOURCE_SMS`, `SOURCE_EXCEL`, `SOURCE_STATEMENT`.
     */
    const val IMPORT_STARTED = "import_started"

    /**
     * Import finished successfully.
     * Params: [PARAM_SOURCE], [PARAM_COUNT_BUCKET] (new expenses imported).
     */
    const val IMPORT_COMPLETED = "import_completed"

    /**
     * Import failed with a caught exception.
     * Params: [PARAM_SOURCE].
     */
    const val IMPORT_FAILED = "import_failed"

    /**
     * User saved a budget (overall spending cap, category budget, or income
     * target).
     * Params: [PARAM_SCOPE] — `SCOPE_OVERALL`, `SCOPE_CATEGORY`, `SCOPE_INCOME`.
     */
    const val BUDGET_SAVED = "budget_saved"

    /**
     * User created a custom category or category group.
     * Params: [PARAM_KIND] — `KIND_GROUP` or `KIND_SUB`.
     */
    const val CATEGORY_CREATED = "category_created"

    /** User deleted a custom category. */
    const val CATEGORY_DELETED = "category_deleted"

    /** User created a custom auto-categorization rule. */
    const val CATEGORY_RULE_CREATED = "category_rule_created"

    /** User deleted a custom auto-categorization rule. */
    const val CATEGORY_RULE_DELETED = "category_rule_deleted"

    /**
     * User tapped a notification and it deep-linked into the app.
     * Params: [PARAM_KIND] — `NOTIFICATION_INCOME`, `NOTIFICATION_CATEGORIZE`,
     * `NOTIFICATION_REVIEW`, `NOTIFICATION_BUDGET`.
     */
    const val NOTIFICATION_OPENED = "notification_opened"

    // ==================== Phase 3: income tracking ====================

    /**
     * User set / changed the source of an income transaction from the
     * Categorize Income screen.
     * Params: none — the source enum ordinal is not sent because the label
     *         list is user-visible product copy and can change.
     */
    const val INCOME_CATEGORIZED_MANUAL = "income_categorized_manual"

    /**
     * User toggled the "exclude from analytics" flag on an income row
     * (typically pass-through / transfer between own accounts).
     * Params: [PARAM_ENABLED] — new state (`true` = now excluded).
     */
    const val INCOME_EXCLUDED_TOGGLED = "income_excluded_toggled"

    /** User deleted an income row from Categorize Income. */
    const val INCOME_DELETED = "income_deleted"

    // ==================== Phase 3: settings ====================

    /** User toggled the master bank-SMS tracking switch. Params: [PARAM_ENABLED]. */
    const val SETTINGS_BANK_TRACKING_TOGGLED = "settings_bank_tracking_toggled"

    /**
     * User toggled a specific bank's SMS tracking.
     * Params: [PARAM_BANK] (hardcoded parser display name — not user input),
     *         [PARAM_ENABLED].
     */
    const val SETTINGS_INDIVIDUAL_BANK_TOGGLED = "settings_individual_bank_toggled"

    /** User toggled biometric unlock. Params: [PARAM_ENABLED]. */
    const val SETTINGS_BIOMETRIC_TOGGLED = "settings_biometric_toggled"

    /**
     * User changed the auto-lock timeout.
     * Params: [PARAM_COUNT_BUCKET] — bucketed seconds via [timeoutBucket].
     */
    const val SETTINGS_LOCK_TIMEOUT_CHANGED = "settings_lock_timeout_changed"

    /**
     * User changed which day of the month budget periods start on.
     * Params: [PARAM_COUNT_BUCKET] — bucketed day via [monthStartDayBucket]
     *         (start / mid / late / other). We do not send the raw day
     *         because in a small population the exact payday could be
     *         semi-identifying.
     */
    const val SETTINGS_MONTH_START_DAY_CHANGED = "settings_month_start_day_changed"

    /** User tapped "Reset categories to defaults". No params. */
    const val SETTINGS_CATEGORIES_RESET = "settings_categories_reset"

    // ==================== Phase 3: data management ====================

    /**
     * User exported all transactions to CSV.
     * Params: [PARAM_SUCCESS] — whether the file was written.
     */
    const val DATA_EXPORTED = "data_exported"

    /**
     * User created a database backup.
     * Params: [PARAM_SUCCESS].
     */
    const val DATABASE_BACKED_UP = "database_backed_up"

    /**
     * User restored a database backup.
     * Params: [PARAM_SUCCESS]. Fires *before* the app process is killed on
     * successful restore, so success events do reach the wire.
     */
    const val DATABASE_RESTORED = "database_restored"

    /** User cleared all expense / income / budget data. No params. */
    const val ALL_DATA_CLEARED = "all_data_cleared"

    // ==================== Phase 3: category edits ====================

    /**
     * User edited an existing category or group (name / icon / color).
     * Params: [PARAM_KIND] — `KIND_GROUP` or `KIND_SUB`.
     */
    const val CATEGORY_EDITED = "category_edited"

    /** User edited an existing auto-categorization rule. No params. */
    const val CATEGORY_RULE_EDITED = "category_rule_edited"

    /**
     * User reassigned every transaction from a merchant to a new category.
     * Params: [PARAM_COUNT_BUCKET] — affected transaction count.
     */
    const val MERCHANT_RECATEGORIZED = "merchant_recategorized"

    // ==================== Phase 3: permissions ====================

    /**
     * User was shown a runtime permission dialog.
     * Params: [PARAM_KIND] — `PERMISSION_SMS` or `PERMISSION_NOTIFICATION`.
     */
    const val PERMISSION_REQUESTED = "permission_requested"

    /**
     * A runtime permission dialog resolved to "granted".
     * Params: [PARAM_KIND], [PARAM_SOURCE] — `SOURCE_ONBOARDING` or `SOURCE_APP`.
     */
    const val PERMISSION_GRANTED = "permission_granted"

    /**
     * A runtime permission dialog resolved to "denied".
     * Params: [PARAM_KIND], [PARAM_SOURCE].
     */
    const val PERMISSION_DENIED = "permission_denied"

    // ==================== Phase 3: onboarding funnel ====================

    /** Onboarding flow started (first launch). */
    const val ONBOARDING_STARTED = "onboarding_started"

    /**
     * Onboarding flow finished.
     * Params: [PARAM_KIND] — `ONBOARDING_COMPLETION_IMPORT` (user tapped
     * "Import Now") or `ONBOARDING_COMPLETION_SKIPPED` (finished via
     * Skip / Get Started without importing). This is enough to see the
     * import-conversion rate without a second event.
     */
    const val ONBOARDING_COMPLETED = "onboarding_completed"

    // ==================== Phase 3: PIN lock ====================

    /** User successfully set up or changed their PIN. */
    const val PIN_SETUP_COMPLETED = "pin_setup_completed"

    /** User disabled the PIN lock (after verifying current PIN). */
    const val PIN_DISABLED = "pin_disabled"

    /**
     * A PIN verification attempt failed. Fires on wrong PIN for any flow
     * (unlock, change, disable). Success is not emitted separately —
     * it can be derived from the ratio of failures to `app_opened`.
     */
    const val PIN_UNLOCK_FAILED = "pin_unlock_failed"

    // ==================== Phase 3: analytics interactions ====================

    /**
     * User switched a tab inside the Analytics screen.
     * Params: [PARAM_TAB] — `TAB_INSIGHTS`, `TAB_CHARTS`, `TAB_MONTHLY`,
     * `TAB_YEARLY`.
     */
    const val ANALYTICS_TAB_SWITCHED = "analytics_tab_switched"

    // ==================== Phase 3: deletion ====================

    /** User deleted an expense row. No params. */
    const val EXPENSE_DELETED = "expense_deleted"

    // ==================== Parameter keys ====================
    const val PARAM_SCREEN = "screen"
    const val PARAM_SOURCE = "source"
    const val PARAM_KIND = "kind"
    const val PARAM_COUNT_BUCKET = "count_bucket"
    const val PARAM_SCOPE = "scope"
    const val PARAM_ENABLED = "enabled"
    const val PARAM_BANK = "bank"
    const val PARAM_SUCCESS = "success"
    const val PARAM_TAB = "tab"

    // ==================== Parameter value enums ====================
    const val KIND_EXPENSE = "expense"
    const val KIND_INCOME = "income"
    const val KIND_GROUP = "group"
    const val KIND_SUB = "sub"

    const val SOURCE_SMS = "sms"
    const val SOURCE_EXCEL = "excel"
    const val SOURCE_STATEMENT = "statement_pdf"
    const val SOURCE_ONBOARDING = "onboarding"
    const val SOURCE_APP = "app"

    const val SCOPE_OVERALL = "overall"
    const val SCOPE_CATEGORY = "category"
    const val SCOPE_INCOME = "income"

    const val NOTIFICATION_INCOME = "income"
    const val NOTIFICATION_CATEGORIZE = "categorize"
    const val NOTIFICATION_REVIEW = "review"
    const val NOTIFICATION_BUDGET = "budget"

    const val PERMISSION_SMS = "sms"
    const val PERMISSION_NOTIFICATION = "notification"

    const val ONBOARDING_COMPLETION_IMPORT = "import_chosen"
    const val ONBOARDING_COMPLETION_SKIPPED = "skipped"

    const val TAB_INSIGHTS = "insights"
    const val TAB_CHARTS = "charts"
    const val TAB_MONTHLY = "monthly"
    const val TAB_YEARLY = "yearly"

    /**
     * Bucketize a raw count to avoid leaking exact volumes.
     * Aligns with Firebase's recommendation to prefer coarse dimensions.
     */
    fun countBucket(n: Int): String = when {
        n <= 0 -> "0"
        n <= 10 -> "1-10"
        n <= 100 -> "11-100"
        n <= 1_000 -> "101-1k"
        else -> "1k+"
    }

    /**
     * Bucketize an auto-lock timeout in seconds. Keeps the dimension coarse
     * so exact custom values don't uniquely identify a user.
     * Buckets: `immediate` (0), `<=1m`, `<=5m`, `<=15m`, `>15m`.
     */
    fun timeoutBucket(seconds: Int): String = when {
        seconds <= 0 -> "immediate"
        seconds <= 60 -> "<=1m"
        seconds <= 300 -> "<=5m"
        seconds <= 900 -> "<=15m"
        else -> ">15m"
    }

    /**
     * Bucketize month-start day. Payday distribution in Kenya clusters
     * around three points; sending the raw day for an outlier could be
     * semi-identifying.
     * Buckets: `start` (1–5), `mid` (6–20), `late` (21–28), `other`.
     */
    fun monthStartDayBucket(day: Int): String = when (day) {
        in 1..5 -> "start"
        in 6..20 -> "mid"
        in 21..28 -> "late"
        else -> "other"
    }
}
