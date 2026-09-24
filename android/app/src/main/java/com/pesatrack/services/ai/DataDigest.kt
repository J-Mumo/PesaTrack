package com.pesatrack.services.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Privacy-safe monthly financial digest that the client sends to
 * `pesatrack-api.jmumo.com` when requesting AI features.
 *
 * This is the v1 schema defined in `plans/ai-pro-plan.md §7`. It is the
 * **only** payload the app sends to any AI provider — nothing else leaves
 * the device. The Phase-1 stub carried only [period]; slice B1 fleshes it
 * out to the full v1 shape used by Coach Insights.
 *
 * ---
 *
 * ## Privacy contract (enforced by convention + tests)
 *
 *  - **No raw recipient names, phone numbers, or account strings.** The
 *    [DigestRecipient.id] field is an ephemeral, request-scoped opaque id
 *    (`r1`, `r2`, ...) assigned at build time by [RecipientAnonymizer].
 *    The device keeps the `id → real name` map in memory *only for the
 *    duration of the request*, uses it to rehydrate names in the LLM
 *    response before rendering, then drops it. **This map is never
 *    persisted, never sent.**
 *  - **No SMS bodies. No transaction descriptions. No merchant tokens.**
 *    Only aggregated integer KES totals + stable category ids.
 *  - **Amounts as whole KES.** Kenya doesn't use fractional shillings in
 *    practice; every KES field is [Int], not [Double].
 *  - **Category [DigestCategory.name] IS sent** — the plan (§7) treats
 *    display names of user-visible categories (e.g. "Food & Dining") as
 *    non-sensitive because they're the app's own taxonomy, not user PII.
 *  - **Recurring [DigestRecurring.label] IS sent** — user-set or inferred
 *    labels like "Rent" / "Netflix", per the plan. Do NOT populate this
 *    field from raw recipient strings; the anonymizer's r-ids are for
 *    the [topRecipientsThisPeriod] list only.
 *
 * ---
 *
 * ## JSON contract
 *
 *  - Field names are `snake_case` matching the backend Zod schema.
 *  - Nullable fields emit `null` rather than being omitted — schema
 *    evolution stays safer that way (Moshi handles this via nullable
 *    Kotlin types).
 *  - Amounts are integer KES.
 *
 * See [plans/ai-pro-plan.md](../../../../../../../../plans/ai-pro-plan.md)
 * §7 for the wire schema; [plans/ai-pro-phase2-spec.md](../../../../../../../../plans/ai-pro-phase2-spec.md)
 * §4 for how the Coach Insight response consumes it.
 */
@JsonClass(generateAdapter = true)
data class DataDigest(
    /**
     * Reporting period, formatted `YYYY-MM` (e.g. `"2026-09"`). Backend
     * validates `min(4).max(20)`.
     */
    @Json(name = "period")
    val period: String,

    /**
     * The day of the month the user's budgeting month begins (1..28). If
     * the user hasn't customised this, defaults to 1. Lets the LLM reason
     * about pace ("day 5 of 30" vs "day 5 of a 31-day period starting the
     * 25th") without needing to send actual dates.
     */
    @Json(name = "month_start_day")
    val monthStartDay: Int,

    /**
     * Number of days elapsed in the current period, inclusive of today.
     * Used for pace math client- and server-side.
     */
    @Json(name = "days_elapsed")
    val daysElapsed: Int,

    /**
     * Total days in the current period.
     */
    @Json(name = "days_total")
    val daysTotal: Int,

    /**
     * Whole-period totals for the current period, previous period, and
     * three-month rolling average.
     */
    val totals: DigestTotals,

    /**
     * Top 10 categories by absolute spend for the current period.
     * Excludes pass-through money (`isExcluded = 1` expenses).
     */
    val categories: List<DigestCategory>,

    /**
     * Top 5 detected recurring expenses by monthly-equivalent amount.
     * Labels are the user-visible display names of the recipient — see
     * class doc for why this is considered non-sensitive by the plan.
     */
    val recurring: List<DigestRecurring>,

    /**
     * Top 8 recipients by absolute spend for the current period, with
     * names anonymised to opaque `r1..rN` ids. The id-to-name mapping is
     * held on-device for the duration of the request only.
     */
    @Json(name = "top_recipients_this_period")
    val topRecipientsThisPeriod: List<DigestRecipient>,

    /**
     * Notable week-over-week anomalies (currently just category spikes).
     * May be empty. Included so the LLM can lead with a real event
     * rather than fabricating one from the totals.
     */
    @Json(name = "anomalies_this_week")
    val anomaliesThisWeek: List<DigestAnomaly>,

    /**
     * Optional multi-year historical aggregates for deep-analysis
     * questions (v1.8.1 Ask Your Money extension). Each entry covers
     * one calendar year; the newest entry is the current year (partial
     * if we're mid-year). Sorted year-descending.
     *
     * Included **only** for the Ask Your Money endpoint's digest —
     * Coach Insight doesn't need this depth, and adding it there would
     * bloat the request for a once-daily card. `null` on the wire when
     * the app has less than one full year of usable data (fresh install,
     * no complete year), which is also how legacy clients pre-1.8.1
     * signal that they don't have the field yet.
     *
     * The backend Zod schema is `.optional().nullable()` so both
     * behaviours coexist.
     */
    @Json(name = "yearly_totals")
    val yearlyTotals: List<DigestYearlyTotal>? = null,
)

/**
 * A single calendar year's roll-up. Amounts are whole KES.
 */
@JsonClass(generateAdapter = true)
data class DigestYearlyTotal(
    /** Four-digit calendar year, e.g. `2026`. */
    val year: Int,
    /** Total spend for the year (whole KES). Excludes pass-through. */
    val spent: Int,
    /**
     * Estimated income for the year. Zero when the user has no income
     * tracking configured; the LLM is instructed to treat 0 as "unknown
     * income", not "zero earnings".
     */
    @Json(name = "income_est")
    val incomeEst: Int,
    /**
     * Amount routed to the Savings & Investments category group (18)
     * in this year. Whole KES.
     */
    val invested: Int,
    /**
     * Total transactions in the year — a coarse proxy for financial
     * activity that lets the LLM compare "engagement" across years.
     */
    @Json(name = "txn_count")
    val txnCount: Int,
)

/**
 * Whole-period spend & income aggregates. All amounts are integer KES
 * (rounded to nearest shilling).
 */
@JsonClass(generateAdapter = true)
data class DigestTotals(
    /** Total spend in the current period (excluding pass-through). */
    val spent: Int,
    /** Total spend in the immediately previous period of the same length. */
    @Json(name = "spent_last_period")
    val spentLastPeriod: Int,
    /** Three-month rolling average of spend (previous 3 complete periods). */
    @Json(name = "spent_3mo_avg")
    val spent3moAvg: Int,
    /**
     * Estimated income for the current period. Zero if unknown / not set.
     * The plan calls this `income_est` because it may be an estimate from
     * detected income transactions rather than a user-declared value.
     */
    @Json(name = "income_est")
    val incomeEst: Int,
    /**
     * Amount routed to the Savings & Investments category group (18) in
     * the current period. Zero if none.
     */
    @Json(name = "invested_this_period")
    val investedThisPeriod: Int,
)

/**
 * Per-category summary line. Represents either a group category (e.g.
 * "Food & Dining") or a sub-category, depending on the aggregation used
 * by the builder — the id is the stable category id from the app.
 */
@JsonClass(generateAdapter = true)
data class DigestCategory(
    /**
     * Stable category id from the app. `null` for the "Uncategorized"
     * bucket (rows where `expenses.categoryId IS NULL`).
     */
    val id: Long?,
    /** Display name, e.g. "Food & Dining". Non-sensitive (app taxonomy). */
    val name: String,
    /** Spend in the current period (KES, whole). */
    val spent: Int,
    /**
     * User-set budget for this category, in whole KES. `null` if no
     * budget has been configured.
     */
    val budget: Int?,
    /**
     * Three-month rolling average of monthly spend in this category.
     * Whole KES. Used by the LLM to distinguish "up unexpectedly" from
     * "normal".
     */
    @Json(name = "3mo_avg")
    val threeMoAvg: Int,
    /**
     * Coefficient of variation (std-dev / mean) across the last three
     * months. `null` if we have fewer than three data points — the LLM
     * should treat null as "unknown volatility", not zero.
     */
    val cv: Double?,
)

/**
 * A detected recurring expense.
 */
@JsonClass(generateAdapter = true)
data class DigestRecurring(
    /**
     * User-visible label. May be a user-set nickname (e.g. "Rent") or
     * the merchant display name (e.g. "Netflix"). Non-sensitive per the
     * plan — but populated from the recurring-detection service, never
     * from raw SMS text.
     */
    val label: String,
    /** Average amount per occurrence, whole KES. */
    val amount: Int,
    /**
     * Recurrence cycle as a canonical string. Matches [com.pesatrack.domain.models.RecurrenceCycle]
     * enum names: `"WEEKLY"`, `"BIWEEKLY"`, `"MONTHLY"`, `"YEARLY"`.
     */
    val period: String,
    /** Detection confidence in [0.0, 1.0]. */
    val confidence: Double,
)

/**
 * Anonymised recipient line. Only the opaque [id] is meaningful across
 * the wire — the LLM refers to recipients by these ids, and the client
 * rehydrates them to real names locally before rendering.
 */
@JsonClass(generateAdapter = true)
data class DigestRecipient(
    /**
     * Opaque request-scoped identifier of the form `r1`, `r2`, ...,
     * assigned by [RecipientAnonymizer] in descending-spend order. **Never
     * a hash of the original name** — a plain sequential id, so it
     * cannot fingerprint the merchant across requests.
     */
    val id: String,
    /** Spend attributed to this recipient in the current period. */
    val spent: Int,
    /** Transaction count in the current period. */
    val count: Int,
    /**
     * Best-guess category id for this recipient. `null` if the
     * recipient's expenses are uncategorised or ambiguous.
     */
    @Json(name = "category_id")
    val categoryId: Long?,
    /**
     * Three-month rolling monthly-average spend for this recipient.
     * Whole KES. Zero if the recipient has no prior history.
     */
    @Json(name = "3mo_avg")
    val threeMoAvg: Int,
)

/**
 * A notable week-over-week anomaly. The initial (v1) implementation only
 * emits [type] = `"category_spike"`, but the field is a string so the
 * schema can grow additively (e.g. `"recurring_missed"`,
 * `"new_recurring"`, `"large_one_off"`) without a new version.
 */
@JsonClass(generateAdapter = true)
data class DigestAnomaly(
    /** Anomaly type. Currently only `"category_spike"`. */
    val type: String,
    /**
     * Associated category id if [type] is category-scoped. `null`
     * otherwise.
     */
    @Json(name = "category_id")
    val categoryId: Long?,
    /**
     * Week-over-week delta as an integer percentage. Positive = spend
     * up. E.g. `68` for a category that grew 68% week-on-week.
     */
    @Json(name = "delta_pct")
    val deltaPct: Int,
)
