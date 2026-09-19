package com.pesatrack.services.pro

import com.pesatrack.services.ai.PesaTrackAiClient
import com.pesatrack.services.ai.VerifyRequestDto
import com.pesatrack.services.telemetry.TelemetryClient
import com.pesatrack.services.telemetry.TelemetryEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of PesaTrack Pro entitlement state on the Android side.
 *
 * Responsibilities:
 *  - Observes the persisted [ProState] via [ProStateStore] (backed by
 *    `AppPreferences` in production) and mirrors the current raw purchase
 *    token into [ProTokenCache] so `ProAuthInterceptor` can attach
 *    `Authorization: Bearer <token>` on every guarded request.
 *  - Talks to the backend for the two auth-critical HTTP calls:
 *    [verifyPurchase] (one-time on a fresh Play Billing purchase, POST
 *    `/billing/verify`) and [refreshEntitlement] (cold-start / on-demand
 *    re-check, GET `/billing/entitlement`).
 *  - Owns the honest-numbers checks on liveness — a persisted
 *    `isEntitled = true` with an expired `expiresAtEpochMs` is treated as
 *    expired at read time here, not at every call site.
 *  - Owns the state-transition telemetry ([TelemetryEvents.PRO_PURCHASE_COMPLETED],
 *    [TelemetryEvents.PRO_ENTITLEMENT_GAINED], [TelemetryEvents.PRO_ENTITLEMENT_LOST])
 *    — user-action events (screen view, subscribe tap, failure buckets)
 *    stay in the ViewModel because they need the outcome/reason mapping.
 *
 * See plans/ai-pro-phase1-spec.md §6.
 *
 * @see ProTokenCache for the reason this class is not itself the
 *   `PurchaseTokenProvider` — Hilt cycle avoidance.
 */
@Singleton
class ProEntitlementRepository @Inject constructor(
    private val store: ProStateStore,
    private val aiClient: PesaTrackAiClient,
    private val tokenCache: ProTokenCache,
    private val telemetryClient: TelemetryClient,
) {

    /**
     * Long-lived scope owned by the singleton. Cancelled implicitly at
     * process death — [SupervisorJob] means a failure in the state-mirror
     * collector cannot poison the whole singleton.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val stateMirrorJob: Job

    init {
        // Keep the token cache in sync with the persisted state. Runs
        // forever; every state change updates the cache, revocation nulls
        // it out. The first emission also warms the cache on cold start.
        stateMirrorJob = store.proState
            .onEach { state -> mirrorToCache(state) }
            .launchIn(scope + Dispatchers.Default)
    }

    /** Hot flow of the persisted [ProState]. Delegates to [ProStateStore]. */
    val proState: Flow<ProState> = store.proState

    /**
     * Snapshot: is the user *effectively* entitled *right now*?
     *
     * Honest-numbers check: even if the stored `isEntitled` is `true`,
     * this returns `false` when `expiresAtEpochMs` is in the past. Callers
     * that only care about "is the Pro UI unlocked" should call this
     * rather than reading `state.isEntitled` directly.
     */
    suspend fun isCurrentlyEntitled(now: Long = System.currentTimeMillis()): Boolean {
        val state = store.getProState()
        if (!state.isEntitled) return false
        val expiry = state.expiresAtEpochMs ?: return false
        return expiry > now
    }

    /**
     * Called by Slice A5's `ProPurchaseFlow` immediately after Google Play
     * returns a fresh `Purchase`. POSTs `/billing/verify`, and on 200 OK
     * persists the returned entitlement as the new [ProState].
     *
     * Contract:
     *  - Returns [Result.success] with the new [ProState] on 200 OK.
     *  - Returns [Result.failure] on any non-2xx or network error. The
     *    caller is responsible for user-facing recovery (retry, restore,
     *    or surface a toast). The persisted [ProState] is not mutated on
     *    failure so a partial write can't strand the user in an
     *    inconsistent state.
     */
    suspend fun verifyPurchase(
        purchaseToken: String,
        product: ProProduct,
    ): Result<ProState> = runCatching {
        val response = aiClient.verifyBilling(
            VerifyRequestDto(purchaseToken = purchaseToken, productId = product.productId)
        )
        if (!response.isSuccessful) {
            throw EntitlementVerifyException(response.code(), "verify_failed")
        }
        val body = response.body()
            ?: throw EntitlementVerifyException(response.code(), "verify_empty_body")

        val newState = ProState(
            isEntitled = body.entitled,
            tier = ProProduct.fromProductId(body.productId) ?: product,
            purchaseToken = purchaseToken,
            expiresAtEpochMs = body.expiresAtEpochMs,
            lastVerifiedAtEpochMs = System.currentTimeMillis(),
            autoRenewing = body.autoRenewing,
        )
        store.setProState(newState)

        // State-transition telemetry. Fires from verifyPurchase() only —
        // the only code path that establishes a fresh entitlement — so
        // cross-device restore fires exactly once per restored subscription.
        // is_trial is emitted as the string "true" / "false" so Firebase's
        // dashboard bucketing stays consistent with other boolean-like params
        // in the allow-list.
        val emittedProduct = newState.tier ?: product
        telemetryClient.logEvent(
            TelemetryEvents.PRO_PURCHASE_COMPLETED,
            mapOf(
                TelemetryEvents.PARAM_PRODUCT_ID to emittedProduct.telemetryValue,
                TelemetryEvents.PARAM_IS_TRIAL to body.isTrialPeriod.toString(),
            ),
        )
        telemetryClient.logEvent(
            TelemetryEvents.PRO_ENTITLEMENT_GAINED,
            mapOf(TelemetryEvents.PARAM_PRODUCT_ID to emittedProduct.telemetryValue),
        )

        newState
    }

    /**
     * Cold-start / on-demand re-verify against the backend. Reads the
     * cached token, hits `/billing/entitlement`, updates the persisted
     * state on the outcome. Never throws — every failure is mapped to a
     * variant of [RefreshResult] so the caller can pick a policy without
     * a try/catch.
     *
     * Failure semantics (plans §6.2–6.3):
     *  - **Success + still entitled** → persist updated state, [RefreshResult.Ok].
     *  - **Success + not entitled**   → [clearEntitlement] then [RefreshResult.Revoked].
     *  - **401 / 404**                → server has no record of us; treat as revoked.
     *  - **Other non-2xx**            → [RefreshResult.ServerError]; cached state is preserved.
     *  - **Network / I/O failure**    → [RefreshResult.NetworkError]; cached state is preserved.
     *
     * TODO(Phase 1 polish): implement the "extend grace 48h once" branch
     * from plans §6.3 for users near expiry with intermittent network.
     * Skeleton behaviour today: cached [ProState] is honoured until the
     * server explicitly revokes.
     */
    suspend fun refreshEntitlement(): RefreshResult {
        val current = store.getProState()
        val token = current.purchaseToken?.takeIf { it.isNotBlank() }
            ?: return RefreshResult.NoToken

        val response = runCatching { aiClient.getEntitlement() }
            .getOrElse { return RefreshResult.NetworkError(it) }

        return when {
            response.code() == 401 || response.code() == 404 -> {
                clearEntitlement(EntitlementLostReason.REVOKED)
                RefreshResult.Revoked
            }
            !response.isSuccessful -> RefreshResult.ServerError(response.code())
            else -> {
                val body = response.body()
                    ?: return RefreshResult.ServerError(response.code())
                if (!body.entitled) {
                    clearEntitlement(EntitlementLostReason.EXPIRED)
                    RefreshResult.Revoked
                } else {
                    val updated = current.copy(
                        isEntitled = true,
                        tier = ProProduct.fromProductId(body.productId) ?: current.tier,
                        expiresAtEpochMs = body.expiresAtEpochMs,
                        autoRenewing = body.autoRenewing,
                        lastVerifiedAtEpochMs = System.currentTimeMillis(),
                        // token stays whatever it was — /billing/entitlement
                        // doesn't return the raw token.
                        purchaseToken = token,
                    )
                    store.setProState(updated)
                    RefreshResult.Ok(updated)
                }
            }
        }
    }

    /**
     * Reset persisted state to [ProState.DEFAULT] and drop the cached
     * token. The [reason] is emitted as the `reason` param of the
     * [TelemetryEvents.PRO_ENTITLEMENT_LOST] event; it is not persisted.
     */
    suspend fun clearEntitlement(reason: EntitlementLostReason) {
        store.setProState(ProState.DEFAULT)
        telemetryClient.logEvent(
            TelemetryEvents.PRO_ENTITLEMENT_LOST,
            mapOf(TelemetryEvents.PARAM_REASON to reason.telemetryValue),
        )
    }

    /**
     * Package-visible so unit tests can wait for the state-mirror
     * collector to catch up. Not part of the public API.
     */
    internal fun stateMirrorJobForTesting(): Job = stateMirrorJob

    private fun mirrorToCache(state: ProState) {
        val effectiveToken = state.purchaseToken?.takeIf { state.isEntitled }
        tokenCache.update(effectiveToken)
    }
}

/**
 * Outcome of [ProEntitlementRepository.refreshEntitlement]. Every branch
 * carries enough information for the caller (or Slice A6's telemetry) to
 * pick a policy without inspecting an exception.
 */
sealed class RefreshResult {
    /** Backend confirmed the entitlement is live; state updated. */
    data class Ok(val state: ProState) : RefreshResult()

    /** No cached purchase token — user has never subscribed or already lost. */
    object NoToken : RefreshResult()

    /** Backend says we're no longer entitled. Persisted state has been cleared. */
    object Revoked : RefreshResult()

    /** Backend returned a non-401/404 non-2xx. Cached state was preserved. */
    data class ServerError(val httpCode: Int) : RefreshResult()

    /** I/O failure reaching the backend. Cached state was preserved. */
    data class NetworkError(val cause: Throwable) : RefreshResult()
}

/**
 * Why the user's Pro entitlement was cleared. Attached to the Slice-A6
 * telemetry event `pro_entitlement_lost.reason`. Values are the
 * allow-listed bucket names from plans/ai-pro-phase1-spec.md §3.6.
 */
enum class EntitlementLostReason(val telemetryValue: String) {
    EXPIRED("expired"),
    REFUNDED("refunded"),
    REVOKED("revoked"),
}

/**
 * Internal wrapping error for non-2xx `/billing/verify` responses. Not
 * exposed to callers — `verifyPurchase` catches this and returns
 * [Result.failure]. Present as a distinct type so `runCatching`'s failure
 * carries a stable code the caller can bucket into telemetry.
 */
class EntitlementVerifyException(
    val httpCode: Int,
    val bucket: String,
) : RuntimeException("$bucket (HTTP $httpCode)")
