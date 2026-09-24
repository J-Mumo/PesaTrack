package com.pesatrack.services.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Wire-format models for the Phase 3 `/ai/ask` chat endpoint.
 *
 * See [plans/ai-pro-phase3-spec.md](../../../../../../../../plans/ai-pro-phase3-spec.md)
 * §5 for the response schema (`ask_response_v1`), §8.2 for the request
 * contract. Every field's JSON name is fixed by the backend Zod schema
 * and asserted by [DataDigestWireContractTest] plus (from B1) the
 * `AskWireContractTest` in the same test source-set.
 *
 * ---
 *
 * ## Contract rules (do not break)
 *
 *  - **snake_case** on the wire — every camelCase Kotlin field name is
 *    remapped via `@Json(name = "...")`.
 *  - Nullable Kotlin fields serialise as `"field": null` — never absent.
 *    This works because the production Retrofit converter is wired with
 *    `.withNullSerialization()` in
 *    [com.pesatrack.di.AiHttpModule.provideRetrofit]. Do not remove that
 *    call; do not add a `@JsonQualifier(ignoreNulls = true)` here.
 *  - Amounts are whole KES ([Int]), matching the digest contract from
 *    Phase 2.
 *  - Every DTO carries `@JsonClass(generateAdapter = true)` so a Moshi
 *    KSP-generated adapter exists — the reflective fallback is not
 *    installed on `@Named("aiPro") Moshi` and would fail at runtime.
 *  - Every class in this file is covered by the blanket ProGuard rule
 *    `-keep class com.pesatrack.services.ai.** { *; }` in
 *    `android/app/proguard-rules.pro`. Moving these into another package
 *    without adding a matching keep rule will silently strip the
 *    generated adapter in the release AAB — the exact bug that took
 *    codes 20→21→22→23 to shake out in Phase 2.
 *
 * ---
 *
 * ## Not persisted
 *
 * Nothing in this file is stored in Room or DataStore. Chat history is
 * in-memory only per the plan (`ai-pro-plan.md` §15 row C — 10-turn
 * memory, cleared on process death). This means:
 *
 *  - We do not need Room converters for these types.
 *  - We do not need a schema-version field — the JSON schema is
 *    versioned server-side as `ask_response_v1`; any breaking client
 *    change flips the model version and the server refuses old shapes.
 */

/**
 * A single turn in the local 10-turn conversation buffer. The client
 * keeps a bounded ring of these in a Compose `SnapshotStateList`; on
 * every call to `/ai/ask` the server-visible slice is
 * `history.takeLast(10)`.
 *
 * Only [role] and [content] cross the wire — chart, assumptions, and
 * action metadata from prior assistant turns are **not** included in
 * history (see [plans/ai-pro-phase3-spec.md](../../../../../../../../plans/ai-pro-phase3-spec.md)
 * §6 rationale — keeps context compact and stops the model repeating a
 * chart it already showed the user).
 */
@JsonClass(generateAdapter = true)
data class AskTurn(
    /**
     * One of `"user"` or `"assistant"`. Wire enum enforced server-side
     * via Zod. We don't use a Kotlin enum here because Moshi enum
     * mapping needs an extra adapter and the two-value string is
     * simpler to test against `Map<String,Any?>`.
     */
    val role: String,

    /**
     * The turn's plain text. For a user turn this is the question the
     * user typed; for an assistant turn this is the `body` field from
     * a prior `AskResponse` (not the streamed draft — the finalised
     * value from the `done` frame).
     */
    val content: String,
)

/**
 * Outbound request body for `POST /ai/ask`.
 *
 * The [digest] is rebuilt fresh on every turn via
 * [DataDigestBuilder.buildForCurrentPeriod] so the model sees any new
 * SMS transactions that arrived since the last turn. Do not cache the
 * digest between turns — that would strand answers on stale data (see
 * §8.3 step 3 "skip cache for /ai/ask").
 */
@JsonClass(generateAdapter = true)
data class AskRequestDto(
    /** Same `DataDigest` v1 shape used by `/ai/coach-insight`. */
    val digest: DataDigest,

    /**
     * Prior turns in chronological order (oldest first). Server-side
     * we `slice(-10)` defensively, but the client SHOULD trim to 10
     * before sending so it never wastes upload bandwidth.
     */
    val history: List<AskTurn>,

    /**
     * The user's current question. `minLength: 1, maxLength: 500` on
     * the backend Zod schema — the client enforces the same limit on
     * the composer's send button so we never see a 400 for an empty
     * or oversized question.
     */
    val question: String,
)

/**
 * A single series (line or bar group) inside a [Chart].
 *
 * `series[]` is capped at 2 entries by the schema — one for "current
 * pace" and (optionally) one for "if you change". More series would
 * make the mini-chart illegible at 140dp height.
 */
@JsonClass(generateAdapter = true)
data class ChartSeries(
    /** Short human-readable label (`maxLength: 40`). */
    val label: String,

    /**
     * Whole KES integer values, same length as [Chart.xLabels]. Length
     * mismatch is caught server-side in `postValidate` and the whole
     * response falls back — but if a stray null ever leaks through, the
     * client's `InlineChart` composable also guards against it before
     * handing values to Vico.
     */
    val values: List<Int>,
)

/**
 * Optional inline chart for projection / scenario answers. When
 * present, [ChartSeries.values] length == [xLabels] length is
 * guaranteed by server-side `postValidate` (see spec §5 rule 1).
 */
@JsonClass(generateAdapter = true)
data class Chart(
    /**
     * `"compound_growth"` → Vico `LineChart` (savings projection).
     * `"monthly_delta_bar"` → Vico `ColumnChart` (category deltas).
     * Any other value is rejected by the JSON schema before it reaches
     * the client, but the `InlineChart` composable's `when` block
     * still needs an `else -> return` branch for future-proofing.
     */
    val type: String,

    /** Only `"KES"` is currently accepted. Enum lives server-side. */
    val unit: String,

    /**
     * X-axis labels. `minItems: 2, maxItems: 60`. Each label
     * `maxLength: 8` chars — enough for `"Wk1"`, `"Sep"`, `"Mo1"`
     * without wrapping under the 140dp chart height.
     */
    @Json(name = "x_labels")
    val xLabels: List<String>,

    /** 1 or 2 series (see class doc). */
    val series: List<ChartSeries>,
)

/**
 * Inbound `AskResponse` — the `done` SSE frame's decoded body when the
 * turn succeeds. Fallbacks arrive as a separate `{ "fallback": true }`
 * shape parsed via [AskDoneEnvelope].
 *
 * The client only ever sees [AskResponse] after post-validation has
 * passed server-side, so the tight rules from §5 (deny-list scrubbed,
 * chart length matched, projections carry assumptions, no
 * imperative-past claims) are already enforced. The composable layer
 * therefore does **not** re-validate business rules — it only guards
 * against structural issues that would crash Vico (empty series, size
 * mismatch — belt-and-braces even though the server catches them).
 */
@JsonClass(generateAdapter = true)
data class AskResponse(
    /**
     * The finalised assistant body (what the streamed text tokens were
     * building up to). `minLength: 1, maxLength: 800`. Second person,
     * present tense, KES-only figures.
     */
    val body: String,

    /**
     * 0..5 assumption strings shown behind an expander. Non-empty when
     * [chart] is non-null or [body] carries projection markers
     * (enforced server-side).
     */
    val assumptions: List<String>,

    /**
     * Optional CTA text. Verb-first (`"Open Food & Dining"`).
     * `maxLength: 40`. Null if [actionDeeplink] is also null.
     */
    @Json(name = "action_label")
    val actionLabel: String?,

    /**
     * Optional pesatrack:// deep link. Server-side pattern:
     * `^pesatrack://(home|budgets|analytics|expenses|category/[0-9]+)$`.
     * Null when the assistant chose not to suggest an action.
     */
    @Json(name = "action_deeplink")
    val actionDeeplink: String?,

    /** Optional inline chart. Null for factual (non-projection) answers. */
    val chart: Chart?,
)

/**
 * Envelope for the SSE `done` frame. Encodes the union of the success
 * shape ([AskResponse]) and the fallback marker `{ "fallback": true }`.
 *
 * We parse into this loose envelope first so a malformed `AskResponse`
 * degrades to a client-side fallback without throwing — the same
 * "silent fallback" contract we settled on in Phase 2 for
 * `CoachInsightResponseDto`.
 */
@JsonClass(generateAdapter = true)
data class AskDoneEnvelope(
    /**
     * `true` when the server chose to serve the deterministic template
     * line (any of §9's failure modes fired). `false` or absent on a
     * happy-path response — in that case [response] carries the real
     * [AskResponse].
     */
    val fallback: Boolean? = null,

    /**
     * Bucketed reason string when [fallback] is true. Values match the
     * `PARAM_REASON` enum used by client telemetry (`network`,
     * `schema`, `deny`, `imperative`, `chart_mismatch`,
     * `circuit_breaker`, `rate_limit`, `provider_error`,
     * `mid_stream_abort`, `unknown`). Never surfaced to the user;
     * only logged.
     */
    val reason: String? = null,

    /**
     * The successful response fields, flattened. Moshi doesn't have a
     * built-in sealed-union decoder without a discriminator, so we
     * inline the fields of [AskResponse] here and let the ViewModel
     * pick one shape based on [fallback].
     */
    val body: String? = null,
    val assumptions: List<String>? = null,
    @Json(name = "action_label")
    val actionLabel: String? = null,
    @Json(name = "action_deeplink")
    val actionDeeplink: String? = null,
    val chart: Chart? = null,
)

/**
 * Convenience: view the envelope as a strict [AskResponse] when it
 * clearly is one. Returns null on any missing required field so the
 * ViewModel falls back cleanly.
 */
fun AskDoneEnvelope.asResponseOrNull(): AskResponse? {
    if (fallback == true) return null
    val b = body ?: return null
    val a = assumptions ?: return null
    return AskResponse(
        body = b,
        assumptions = a,
        actionLabel = actionLabel,
        actionDeeplink = actionDeeplink,
        chart = chart,
    )
}
