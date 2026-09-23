package com.pesatrack.services.pro

/**
 * Result of an end-to-end PesaTrack Pro purchase attempt, from tapping
 * "Subscribe" through backend verification.
 *
 * The purchase flow has three distinct failure surfaces:
 *  1. **BillingFailed** — Google Play refused (network to Play, developer
 *     error, item unavailable, etc.). We can't recover — surface the
 *     bucket to the user and let them retry.
 *  2. **VerifyFailed** — Google Play confirmed the purchase but our
 *     backend rejected the token (packaging mismatch, revoked, or
 *     transient 500). The user has been charged; we must retry `/billing/verify`
 *     later (Slice A6 telemetry captures the reason).
 *  3. **NetworkError** — I/O failure reaching Play or the backend. Same
 *     "you might be charged; we'll retry" story.
 *
 * `UserCancelled` is a first-class outcome, not an error — the user
 * dismissed the Play sheet. Zero recovery required.
 *
 * See plans/ai-pro-phase1-spec.md §6.1.
 */
sealed class PurchaseOutcome {

    /** Play confirmed + backend verified + [ProState] persisted. */
    data class Ok(val state: ProState) : PurchaseOutcome()

    /** User dismissed the Play sheet. No side effects. */
    object UserCancelled : PurchaseOutcome()

    /**
     * Google Play refused the purchase because the current Play account
     * already owns this SKU (`BillingResponseCode.ITEM_ALREADY_OWNED`
     * = 7). Callers should treat this as an **implicit Restore** signal
     * — the entitlement already exists server-side, the client just
     * needs to re-verify against the owned subscription. See
     * `PesaTrackProViewModel.subscribe` for the auto-Restore handling.
     *
     * This case exists because ITEM_ALREADY_OWNED is the natural
     * failure mode after a fresh install with an existing subscription,
     * or when the client's local `ProState` fell behind Google's
     * server-side subscription state (test-track 5-min expiry, cleared
     * app data, etc.). Bucketing it as generic [BillingFailed] would
     * surface a scary error to a paying user and mask the trivial fix
     * (call `Restore` on the caller's behalf).
     */
    object AlreadyOwned : PurchaseOutcome()

    /**
     * Google Play refused the purchase before it could reach our backend.
     * [phase] locates the failure: `launch` (the `launchBillingFlow` call
     * itself), `update` (the `PurchasesUpdatedListener` callback), or
     * `acknowledge` (the post-purchase acknowledgement).
     *
     * Does **not** cover [AlreadyOwned] — code 7 is broken out as its
     * own outcome so the ViewModel can auto-restore instead of showing
     * a failure snackbar.
     */
    data class BillingFailed(
        val playResponseCode: Int,
        val phase: String,
    ) : PurchaseOutcome()

    /** Backend `/billing/verify` non-2xx or malformed response. User was charged. */
    data class VerifyFailed(val cause: Throwable) : PurchaseOutcome()

    /** I/O failure reaching Play or the backend. */
    data class NetworkError(val cause: Throwable) : PurchaseOutcome()

    companion object {
        /**
         * Pure mapping from a Play `BillingResponseCode` to the
         * appropriate outcome bucket. Public so tests can lock the
         * policy without spinning up any BillingClient scaffolding.
         *
         * See [PlayBillingResponseCodes] for the numeric constants —
         * duplicated locally to avoid forcing the `billing-ktx` classpath
         * onto pure JVM unit tests.
         *
         * @param code the Play `BillingResult.responseCode`
         * @param phase which pipeline stage produced the code (for
         *  telemetry bucketing later)
         */
        fun fromPlayResponseCode(code: Int, phase: String): PurchaseOutcome = when (code) {
            PlayBillingResponseCodes.OK ->
                // OK should never reach here — it means "purchase pending or
                // succeeded" and the caller handles it separately. Treat as
                // developer error if it does slip through.
                BillingFailed(code, "$phase:unexpected_ok")

            PlayBillingResponseCodes.USER_CANCELED -> UserCancelled

            // "You already own this SKU" — not a real failure. Break out
            // as its own outcome so the ViewModel can auto-restore
            // instead of surfacing a scary "purchase failed" snackbar
            // to a user who is, in fact, subscribed. Common after fresh
            // installs, cleared app data, or the test-track 5-min
            // expiry clock resetting local ProState.
            PlayBillingResponseCodes.ITEM_ALREADY_OWNED -> AlreadyOwned

            // Anything network-flavoured maps to NetworkError so the UI can
            // pick a "check connection and retry" surface. The exception
            // wrapper is synthetic — there's no throwable at this layer.
            PlayBillingResponseCodes.SERVICE_UNAVAILABLE,
            PlayBillingResponseCodes.SERVICE_DISCONNECTED,
            PlayBillingResponseCodes.SERVICE_TIMEOUT,
            PlayBillingResponseCodes.NETWORK_ERROR ->
                NetworkError(BillingNetworkException(code, phase))

            else -> BillingFailed(code, phase)
        }
    }
}

/**
 * Numeric copy of the Play `BillingClient.BillingResponseCode` constants
 * we care about. Duplicated on purpose: keeps [PurchaseOutcome.fromPlayResponseCode]
 * runnable on the pure JVM (Robolectric-free unit tests) and shields
 * calling code from a future rename in the Play SDK.
 *
 * If the Play SDK ever changes these numbers (they've been stable since
 * v2), a Slice-A6 telemetry drift will surface it immediately.
 */
internal object PlayBillingResponseCodes {
    const val OK: Int = 0
    const val USER_CANCELED: Int = 1
    const val SERVICE_UNAVAILABLE: Int = 2
    const val BILLING_UNAVAILABLE: Int = 3
    const val ITEM_UNAVAILABLE: Int = 4
    const val DEVELOPER_ERROR: Int = 5
    const val ERROR: Int = 6
    const val ITEM_ALREADY_OWNED: Int = 7
    const val ITEM_NOT_OWNED: Int = 8
    const val NETWORK_ERROR: Int = 12
    const val FEATURE_NOT_SUPPORTED: Int = -2
    const val SERVICE_DISCONNECTED: Int = -1
    const val SERVICE_TIMEOUT: Int = -3
}

/** Synthetic exception attached to [PurchaseOutcome.NetworkError] when the
 *  cause is a Play billing response code rather than a real throwable. */
class BillingNetworkException(
    val playResponseCode: Int,
    val phase: String,
) : RuntimeException("Play network failure (code=$playResponseCode, phase=$phase)")
