package com.pesatrack.services.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A single AI-generated Coach Insight — the payload the backend
 * `POST /ai/coach-insight` endpoint returns inside its `insight` field.
 *
 * The shape mirrors the `coach_insight_v1` JSON schema enforced by
 * OpenAI Structured Outputs strict mode server-side (see
 * `backend/src/services/ai/coachInsight.js`), and by the same file's
 * `postValidate` guardrails. The client re-validates via Moshi codegen +
 * `withRehydratedRecipients` before render.
 *
 * ---
 *
 * ## Recipient rehydration
 *
 * The model writes recipient references as opaque ids (`r1`, `r2`, …) —
 * the same ids [DataDigestBuilder] assigns before the request goes out.
 * The client keeps the `id → real name` map in memory for the duration
 * of that single request, then rehydrates the ids into real names
 * *on device* just before rendering. **The map never crosses the wire.**
 *
 * Rehydration uses [RecipientAnonymizer.rehydrate] under the hood, so
 * `r1` and `r10` never collide, unknown ids are left in place, and no
 * regex has to be duplicated across files.
 *
 * See `plans/ai-pro-phase2-spec.md §4` for the schema and §6.2 for the
 * rehydration contract.
 */
@JsonClass(generateAdapter = true)
data class CoachInsight(
    /** 1-line headline, 8..60 chars. Neutral tone (enforced server-side). */
    val title: String,

    /**
     * 2–3 sentence body, 40..400 chars. Second person, present tense,
     * with at least one KES figure. Foreign currencies (USD/EUR/GBP)
     * rejected server-side.
     */
    val body: String,

    /**
     * Optional CTA text, ≤40 chars. `null` when the model has no useful
     * next step. Server may null this out during soft-fix if
     * [actionDeeplink] references a category/recipient no longer in the
     * digest.
     */
    @Json(name = "action_label")
    val actionLabel: String?,

    /**
     * Optional deep link. Constrained to
     * `pesatrack://(home|budgets|analytics|expenses|category/N|recipient/rN)`.
     * `null` when the model omits the action. Same soft-fix as above.
     */
    @Json(name = "action_deeplink")
    val actionDeeplink: String?,

    /**
     * Optional saveable amount in whole KES (0..1_000_000). `null` when
     * the insight is not framed as a savings suggestion. If non-null,
     * [assumptions] MUST be non-empty (enforced server-side).
     */
    @Json(name = "saveable_amount_kes")
    val saveableAmountKes: Int?,

    /**
     * ≤5 short assumptions the model made, each ≤120 chars. Mandatory
     * when [saveableAmountKes] is non-null — surfaces the "honest
     * numbers" principle: no projection without visible assumptions.
     */
    val assumptions: List<String>,

    /**
     * ≤3 `rN` ids the model referenced in [title]/[body]. The client
     * uses these to invalidate a stale action-deeplink or to prepare
     * the rehydration path — server-side [postValidate] rejects any id
     * not present in the digest.
     */
    @Json(name = "referenced_recipient_ids")
    val referencedRecipientIds: List<String>,
) {
    /**
     * Return a copy with [title] and [body] rewritten so `r1`/`r2`/…
     * are replaced by the merchant names from [rehydrationMap]. Ids not
     * present in the map are left in place — the caller can decide
     * whether to log or fall back.
     *
     * Uses [RecipientAnonymizer.rehydrate] so the word-boundary
     * behaviour matches the anonymiser at digest-build time (no `r1`
     * vs `r10` collisions).
     */
    fun withRehydratedRecipients(rehydrationMap: Map<String, String>): CoachInsight {
        if (rehydrationMap.isEmpty()) return this
        return copy(
            title = RecipientAnonymizer.rehydrate(title, rehydrationMap),
            body = RecipientAnonymizer.rehydrate(body, rehydrationMap),
        )
    }
}
