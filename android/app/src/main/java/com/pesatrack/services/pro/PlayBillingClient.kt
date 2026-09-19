package com.pesatrack.services.pro

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Coroutine-friendly wrapper around Google [BillingClient] v7 for the
 * PesaTrack Pro subscription. Owns the [BillingClient] instance,
 * serialises connect calls, and republishes Play's
 * `PurchasesUpdatedListener` as a hot [SharedFlow] so the callback →
 * suspend `first()` handoff in [ProPurchaseFlow] can wait deterministically.
 *
 * All connection / query / acknowledge methods are `suspend` — the
 * lower-level BillingClient callbacks are wrapped with either the
 * `billing-ktx` extensions (`queryProductDetails`, `queryPurchasesAsync`,
 * `acknowledgePurchase`) or a `suspendCancellableCoroutine` for
 * `startConnection`.
 *
 * The [purchaseUpdates] flow uses `extraBufferCapacity = 1` with
 * `DROP_OLDEST` so an update that arrives after `launchBillingFlow` but
 * before the caller manages to subscribe still lands in the buffer. The
 * [ProPurchaseFlow] pattern is nonetheless "subscribe with
 * `async { first() }` before `launchBillingFlow`" to close the race
 * window entirely.
 *
 * This class is deliberately not the caller of `/billing/verify` — that
 * responsibility belongs to `ProEntitlementRepository` from Slice A4.
 * PlayBillingClient just brokers with Google Play.
 *
 * See plans/ai-pro-phase1-spec.md §6.1.
 */
@Singleton
class PlayBillingClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : PurchasesUpdatedListener {

    private val _updates: MutableSharedFlow<PurchaseUpdate> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Hot flow of `PurchasesUpdatedListener` callbacks. */
    val purchaseUpdates: SharedFlow<PurchaseUpdate> = _updates.asSharedFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases(
            // Subscriptions aren't "pending" in the one-time-product sense,
            // but BillingClient v7 requires PendingPurchasesParams to be
            // configured before build(). Enabling one-time products here
            // is a no-op for our subs-only app; it's the minimum config
            // the builder demands.
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .setListener(this)
        .build()

    private val connectionMutex = Mutex()

    // ─── Play callback → SharedFlow bridge ───────────────────────────────

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        _updates.tryEmit(
            PurchaseUpdate(
                responseCode = billingResult.responseCode,
                debugMessage = billingResult.debugMessage.orEmpty(),
                purchases = purchases.orEmpty(),
            )
        )
    }

    // ─── Connection lifecycle ────────────────────────────────────────────

    /**
     * Ensure the underlying BillingClient is connected to Play Services.
     * Idempotent: a mutex serialises concurrent callers so
     * `startConnection` is never invoked twice.
     *
     * Returns the [BillingResult] from Play — non-OK codes are the
     * caller's problem to bucket via [PurchaseOutcome.fromPlayResponseCode].
     */
    suspend fun ensureConnected(): BillingResult = connectionMutex.withLock {
        if (billingClient.isReady) {
            return@withLock okResult()
        }
        suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (cont.isActive) cont.resume(billingResult)
                }
                override fun onBillingServiceDisconnected() {
                    // No-op. The next ensureConnected() call will detect
                    // `!isReady` and reconnect on demand.
                }
            })
        }
    }

    // ─── Queries ─────────────────────────────────────────────────────────

    /**
     * Query [ProductDetails] for the given [ProProduct]s from Google Play.
     * Returns an empty list if Play doesn't have the SKU on file — the
     * usual cause is that the product hasn't been published in the Play
     * Console yet.
     */
    suspend fun queryProductDetails(products: List<ProProduct>): List<ProductDetails> {
        require(products.isNotEmpty()) { "queryProductDetails needs at least one product" }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                products.map { product ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(product.productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()
        val result = billingClient.queryProductDetails(params)
        return result.productDetailsList.orEmpty()
    }

    /**
     * Query all subscription [Purchase]s currently owned by the signed-in
     * Play account. Used by the "Restore purchase" affordance (Slice A5b)
     * and by cold-start refresh (Slice A5b's ViewModel).
     */
    suspend fun queryOwnedSubscriptions(): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        return result.purchasesList
    }

    // ─── Purchase flow entry ─────────────────────────────────────────────

    /**
     * Launch the Play Billing purchase sheet for the given
     * [productDetails] + [offerToken]. Synchronous — returns a
     * [BillingResult] indicating whether Play accepted the launch request.
     * The actual purchase result (or user cancellation) arrives later on
     * [purchaseUpdates].
     */
    fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
    ): BillingResult {
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        return billingClient.launchBillingFlow(activity, flowParams)
    }

    // ─── Post-purchase acknowledgement ───────────────────────────────────

    /**
     * Acknowledge a [Purchase] with Google Play. Idempotent: already-
     * acknowledged purchases return `null` without touching the wire.
     * Google gives us three days from purchase confirmation to acknowledge
     * or Play automatically refunds — we always ack immediately after
     * `/billing/verify` succeeds.
     */
    suspend fun acknowledgeIfNeeded(purchase: Purchase): BillingResult? {
        if (purchase.isAcknowledged) return null
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        return billingClient.acknowledgePurchase(params)
    }

    private fun okResult(): BillingResult = BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.OK)
        .build()
}

/**
 * Snapshot of a [PurchasesUpdatedListener] callback. Republished on
 * [PlayBillingClient.purchaseUpdates] so consumers can `first()` off a
 * hot flow instead of installing another callback.
 */
data class PurchaseUpdate(
    val responseCode: Int,
    val debugMessage: String,
    val purchases: List<Purchase>,
)
