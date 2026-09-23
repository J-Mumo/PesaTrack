package com.pesatrack.services.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface for the PesaTrack AI Pro backend at
 * `https://pesatrack-api.jmumo.com`. Every method returns `Response<T>` so
 * the caller can distinguish 2xx / 4xx / 5xx explicitly — critical for the
 * "silent revert to free UX" branch on `/billing/verify` failures (see
 * plans/ai-pro-phase1-spec.md §6.2–6.3).
 *
 * Auth model (enforced by [ProAuthInterceptor], not this interface):
 *  - `/health` — no auth.
 *  - `/billing/verify` — the purchase token to be verified travels in the
 *    request **body**; no `Authorization` header is added (the caller
 *    hasn't been authenticated yet).
 *  - `/billing/entitlement` and every `/ai/…` path — `Authorization: Bearer
 *    <purchaseToken>` is required. If the [PurchaseTokenProvider] returns
 *    null, the interceptor short-circuits with a synthetic 401 rather than
 *    dispatching an anonymous request.
 *
 * All wire-format field names are snake_case matching the backend Zod / raw
 * JSON contracts in `backend/src/routes/`.
 */
interface PesaTrackAiClient {

    /** Public health probe. Returns `{status, service, version, uptime_ms, env}`. */
    @GET("health")
    suspend fun health(): Response<HealthResponseDto>

    /**
     * Verify a Google Play purchase token against the backend. This is the
     * one-time call that establishes an [EntitlementResponseDto] server-side;
     * subsequent auth-guarded calls carry the same token as a Bearer header.
     */
    @POST("billing/verify")
    suspend fun verifyBilling(@Body request: VerifyRequestDto): Response<EntitlementResponseDto>

    /**
     * Read the current server-recorded entitlement for the caller's Bearer
     * token. Requires a valid entitlement to be already established via
     * [verifyBilling] — otherwise 401.
     */
    @GET("billing/entitlement")
    suspend fun getEntitlement(): Response<EntitlementResponseDto>

    /**
     * Phase-1 pipeline-proof endpoint. Backend receives a [DataDigest],
     * counts the field names, and returns a small confirmation — the LLM
     * is never invoked in Phase 1. Wire test only.
     */
    @POST("ai/echo")
    suspend fun aiEcho(@Body request: AiEchoRequestDto): Response<AiEchoResponseDto>

    /**
     * Phase-2 Coach Insight endpoint. Sends the anonymised [DataDigest]
     * to the backend, which grounds an OpenAI Structured-Outputs call
     * against it and returns either a [CoachInsight] or a
     * `fallback: true` envelope with a bucketed reason. Server-side
     * guardrails: deny-list scrub, `postValidate` cross-checks, and
     * strict-mode JSON schema enforcement — see
     * `backend/src/services/ai/coachInsight.js` and
     * plans/ai-pro-phase2-spec.md §7.
     *
     * The response envelope is HTTP 200 even on server-side fallback so
     * the client renders the template card silently instead of showing
     * an error state. Only genuine client bugs (invalid digest, missing
     * bearer) surface as 4xx; the caller ([CoachInsightRepository])
     * treats every non-2xx as "fall back to yesterday's cache".
     */
    @POST("ai/coach-insight")
    suspend fun coachInsight(
        @Body request: CoachInsightRequestDto,
    ): Response<CoachInsightResponseDto>
}

// ─────────────────────────────────────────────────────────────
// DTOs — kept in this file for compactness (all are wire-only,
// tightly coupled to the interface). Domain types live elsewhere.
// ─────────────────────────────────────────────────────────────

/** `GET /health` response. */
@JsonClass(generateAdapter = true)
data class HealthResponseDto(
    @Json(name = "status") val status: String,
    @Json(name = "service") val service: String,
    @Json(name = "version") val version: String,
    @Json(name = "uptime_ms") val uptimeMs: Long,
    @Json(name = "env") val env: String,
)

/** `POST /billing/verify` request body. */
@JsonClass(generateAdapter = true)
data class VerifyRequestDto(
    @Json(name = "purchaseToken") val purchaseToken: String,
    @Json(name = "productId") val productId: String,
)

/**
 * Shared response shape for `POST /billing/verify` and
 * `GET /billing/entitlement`. Matches `toClientEntitlement()` in
 * `backend/src/routes/billing.js`.
 */
@JsonClass(generateAdapter = true)
data class EntitlementResponseDto(
    @Json(name = "entitled") val entitled: Boolean,
    @Json(name = "productId") val productId: String,
    @Json(name = "expiresAtEpochMs") val expiresAtEpochMs: Long,
    @Json(name = "autoRenewing") val autoRenewing: Boolean,
    @Json(name = "isTrialPeriod") val isTrialPeriod: Boolean,
)

/** `POST /ai/echo` request body — wraps a [DataDigest]. */
@JsonClass(generateAdapter = true)
data class AiEchoRequestDto(
    @Json(name = "digest") val digest: DataDigest,
)

/** `POST /ai/echo` response. */
@JsonClass(generateAdapter = true)
data class AiEchoResponseDto(
    @Json(name = "received_at") val receivedAt: String,
    @Json(name = "digest_field_count") val digestFieldCount: Int,
    @Json(name = "period") val period: String,
    @Json(name = "provider") val provider: String,
    @Json(name = "request_id") val requestId: String,
)

/** `POST /ai/coach-insight` request body — wraps a [DataDigest]. */
@JsonClass(generateAdapter = true)
data class CoachInsightRequestDto(
    @Json(name = "digest") val digest: DataDigest,
)

/**
 * `POST /ai/coach-insight` response envelope.
 *
 * The backend deliberately returns HTTP 200 for every guardrail
 * rejection or provider failure — the [fallback] flag distinguishes
 * "usable insight" from "silent template fallback" so the client never
 * has to render an error state. See plans/ai-pro-phase2-spec.md §8
 * ("Fallback & Failure Modes").
 *
 *  - `fallback == false && insight != null` — happy path, use [insight].
 *  - `fallback == true` — [insight] is null; render the existing
 *    template card. [reason] is one of `provider_error`,
 *    `saveable_no_assumptions`, `unknown_recipient_id`,
 *    `foreign_currency`, `denylist`, etc. — never surfaced to the
 *    user, only to telemetry.
 *  - `cached` — true when the backend served this insight from its own
 *    24h digest-hash cache. Purely informational (client caches
 *    independently); nice for debugging.
 */
@JsonClass(generateAdapter = true)
data class CoachInsightResponseDto(
    @Json(name = "fallback") val fallback: Boolean,
    @Json(name = "insight") val insight: CoachInsight?,
    @Json(name = "reason") val reason: String?,
    @Json(name = "cached") val cached: Boolean?,
    @Json(name = "request_id") val requestId: String?,
)
