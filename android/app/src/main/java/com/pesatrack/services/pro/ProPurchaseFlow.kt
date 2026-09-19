package com.pesatrack.services.pro

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a single PesaTrack Pro subscription purchase from tap to
 * verified entitlement, as sketched in plans/ai-pro-phase1-spec.md §6.1:
 *
 * ```
 * tap Subscribe
 *   → queryProductDetails         (Play)
 *   → launchBillingFlow           (Play sheet)
 *   → wait on purchaseUpdates     (Play callback)
 *   → acknowledgeIfNeeded         (Play)
 *   → verifyPurchase              (our backend)
 *   → ProState persisted          (DataStore, via ProEntitlementRepository)
 * ```
 *
 * Every user-visible failure maps to a [PurchaseOutcome] variant so the
 * ViewModel in Slice A5b can render a single `when(outcome)` block. The
 * flow never throws — cancellation is a first-class outcome, not an
 * exception.
 *
 * Runs entirely in the caller's coroutine — no internal scope is
 * launched, so back-pressed cancellation from the ViewModel propagates
 * naturally.
 */
@Singleton
class ProPurchaseFlow @Inject constructor(
    private val playBilling: PlayBillingClient,
    private val entitlements: ProEntitlementRepository,
) {

    /**
     * Launch the purchase sheet for [product] and drive the flow through
     * to a persisted [ProState] (on success) or a diagnosable
     * [PurchaseOutcome] variant (on any failure).
     *
     * Must be called from a coroutine tied to the Activity lifecycle —
     * cancellation propagates cleanly.
     */
    suspend fun launchPurchase(
        activity: Activity,
        product: ProProduct,
    ): PurchaseOutcome = coroutineScope {
        // 1. Ensure Play is connected before we do anything else. Non-OK
        //    here is almost always a network / play-services situation.
        val connectResult = playBilling.ensureConnected()
        if (connectResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return@coroutineScope PurchaseOutcome.fromPlayResponseCode(
                connectResult.responseCode, phase = "connect"
            )
        }

        // 2. Fetch product details for the requested SKU. Missing = not
        //    published in Play Console (developer error, not user's fault).
        val details = playBilling.queryProductDetails(listOf(product))
            .firstOrNull { it.productId == product.productId }
            ?: return@coroutineScope PurchaseOutcome.BillingFailed(
                playResponseCode = PlayBillingResponseCodes.ITEM_UNAVAILABLE,
                phase = "product_details",
            )

        val offerToken = pickFirstOfferToken(details)
            ?: return@coroutineScope PurchaseOutcome.BillingFailed(
                playResponseCode = PlayBillingResponseCodes.DEVELOPER_ERROR,
                phase = "no_offer_token",
            )

        // 3. Subscribe to the purchase-update flow BEFORE calling
        //    launchBillingFlow — the callback can fire arbitrarily fast on
        //    fast networks + already-owned SKUs. The extraBufferCapacity=1
        //    on the SharedFlow is a belt-and-braces backstop; this
        //    async {} pattern is the actual race guard.
        val updateAwait = async { playBilling.purchaseUpdates.first() }

        val launch = playBilling.launchBillingFlow(activity, details, offerToken)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            updateAwait.cancel()
            return@coroutineScope PurchaseOutcome.fromPlayResponseCode(
                launch.responseCode, phase = "launch",
            )
        }

        // 4. Await the PurchasesUpdatedListener callback.
        val update = updateAwait.await()
        if (update.responseCode != BillingClient.BillingResponseCode.OK) {
            return@coroutineScope PurchaseOutcome.fromPlayResponseCode(
                update.responseCode, phase = "update",
            )
        }

        // 5. Locate the purchase for the SKU we just launched. In practice
        //    Play returns exactly one, but multi-item baskets are theoretically
        //    possible so we filter defensively.
        val purchase = update.purchases.firstOrNull {
            it.products.contains(product.productId)
        } ?: return@coroutineScope PurchaseOutcome.BillingFailed(
            playResponseCode = PlayBillingResponseCodes.ERROR,
            phase = "no_matching_purchase",
        )

        // 6. Verify with our backend BEFORE acknowledging. If the backend
        //    rejects (packaging mismatch, revoked, transient 500) we don't
        //    want to ack — Google will refund the pending purchase after
        //    ~3 days if we skip ack.
        val verifyResult = entitlements.verifyPurchase(
            purchaseToken = purchase.purchaseToken,
            product = product,
        )
        val newState = verifyResult.getOrElse { throwable ->
            return@coroutineScope PurchaseOutcome.VerifyFailed(throwable)
        }

        // 7. Best-effort acknowledge. Failure here isn't fatal to the
        //    entitlement (backend already knows), but we log so Slice A6
        //    can bucket the reason.
        val ackResult = playBilling.acknowledgeIfNeeded(purchase)
        if (ackResult != null && ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
            // Continue with success: the entitlement is verified server-side
            // and Play's post-purchase state will retry acknowledgement on
            // its own schedule.
        }

        PurchaseOutcome.Ok(newState)
    }

    /**
     * Query already-owned subscriptions and re-verify each against our
     * backend. Returns the number of purchases that were successfully
     * (re)verified — useful for a "N subscriptions restored" toast.
     *
     * See plans/ai-pro-phase1-spec.md §6.4.
     */
    suspend fun restorePurchases(): RestoreOutcome {
        val connect = playBilling.ensureConnected()
        if (connect.responseCode != BillingClient.BillingResponseCode.OK) {
            return RestoreOutcome.PlayUnavailable(connect.responseCode)
        }
        val owned = playBilling.queryOwnedSubscriptions()
        if (owned.isEmpty()) return RestoreOutcome.None

        var verified = 0
        var failed = 0
        for (purchase in owned) {
            val productId = purchase.products.firstOrNull() ?: continue
            val product = ProProduct.fromProductId(productId) ?: continue
            val result = entitlements.verifyPurchase(purchase.purchaseToken, product)
            if (result.isSuccess) {
                verified += 1
                // Acknowledge if we hadn't already (e.g. cross-device restore).
                playBilling.acknowledgeIfNeeded(purchase)
            } else {
                failed += 1
            }
        }
        return RestoreOutcome.Restored(verifiedCount = verified, failedCount = failed)
    }

    /**
     * Pick a subscription offer token to launch with. Phase 1 supports a
     * single non-tiered offer per product — we pick the first offer in
     * the first pricing phase. Slice A5b's UI shows only one price per
     * tier, matching this decision.
     */
    private fun pickFirstOfferToken(details: ProductDetails): String? =
        details.subscriptionOfferDetails?.firstOrNull()?.offerToken
}

/**
 * Result of `ProPurchaseFlow.restorePurchases()`.
 */
sealed class RestoreOutcome {
    /** Play was unavailable — connection failed. */
    data class PlayUnavailable(val playResponseCode: Int) : RestoreOutcome()

    /** Signed-in Play account owns no PesaTrack Pro subscriptions. */
    object None : RestoreOutcome()

    /**
     * Purchases were found. [verifiedCount] were re-verified successfully
     * (and their `ProState` persisted); [failedCount] failed backend
     * verification and should be retried later.
     */
    data class Restored(
        val verifiedCount: Int,
        val failedCount: Int,
    ) : RestoreOutcome()
}
