package com.pesatrack.presentation.screens.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.pesatrack.services.pro.PlayBillingClient
import com.pesatrack.services.pro.ProEntitlementRepository
import com.pesatrack.services.pro.ProProduct
import com.pesatrack.services.pro.ProPurchaseFlow
import com.pesatrack.services.pro.PurchaseOutcome
import com.pesatrack.services.pro.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [PesaTrackProScreen]. Owns the two long-lived state
 * subscriptions (Pro entitlement and outcome messages) and exposes three
 * user actions: [subscribe], [restore], [dismissMessage].
 *
 * Deliberately narrow — the actual purchase orchestration lives in
 * [ProPurchaseFlow] (Slice A5a); this class is a thin bridge that turns
 * screen intents into flow calls and outcomes back into UI state.
 *
 * See plans/ai-pro-phase1-spec.md §3.1.
 */
@HiltViewModel
class PesaTrackProViewModel @Inject constructor(
    private val playBilling: PlayBillingClient,
    private val purchaseFlow: ProPurchaseFlow,
    private val entitlements: ProEntitlementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PesaTrackProUiState())
    val uiState: StateFlow<PesaTrackProUiState> = _uiState.asStateFlow()

    init {
        // Mirror the persisted ProState into the UI so entitled users see
        // the "manage subscription" surface instead of the upsell.
        entitlements.proState
            .onEach { state -> _uiState.value = _uiState.value.copy(currentState = state) }
            .launchIn(viewModelScope)

        loadTiers()
    }

    /**
     * Load product details for both tiers from Google Play. Failures are
     * silent — a `Coming soon` placeholder is a valid, ship-worthy state
     * when the Play Console SKUs aren't published yet (see
     * `PesaTrackProUiState.showComingSoonPlaceholder`).
     */
    fun loadTiers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val connect = playBilling.ensureConnected()
            if (connect.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                // Silent — tiers stay empty, screen shows "Coming soon".
                _uiState.value = _uiState.value.copy(loading = false, tiers = emptyList())
                return@launch
            }
            val allProducts = ProProduct.entries
            val details = runCatching { playBilling.queryProductDetails(allProducts) }
                .getOrDefault(emptyList())

            val tiers = allProducts
                .mapNotNull { p ->
                    val d = details.firstOrNull { it.productId == p.productId } ?: return@mapNotNull null
                    val offer = d.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
                    val phase = offer.pricingPhases.pricingPhaseList.firstOrNull() ?: return@mapNotNull null
                    TierViewData(
                        product = p,
                        formattedPrice = phase.formattedPrice.orEmpty(),
                        billingPeriod = phase.billingPeriod.orEmpty(),
                        savingsCaption = computeSavingsCaption(p, details),
                    )
                }
            _uiState.value = _uiState.value.copy(loading = false, tiers = tiers)
        }
    }

    /**
     * Launch the Play Billing sheet for [product] and drive the whole
     * flow through to a persisted entitlement (or a diagnosable outcome).
     */
    fun subscribe(activity: Activity, product: ProProduct) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(purchaseInFlight = true)
            val outcome = purchaseFlow.launchPurchase(activity, product)
            _uiState.value = _uiState.value.copy(
                purchaseInFlight = false,
                outcomeMessage = outcome.toMessage(product),
            )
        }
    }

    /**
     * Re-verify every subscription already owned by the Play account
     * signed into this device. Idempotent — safe to spam.
     */
    fun restore() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(restoreInFlight = true)
            val outcome = purchaseFlow.restorePurchases()
            _uiState.value = _uiState.value.copy(
                restoreInFlight = false,
                outcomeMessage = outcome.toMessage(),
            )
        }
    }

    /** Called after the snackbar dismisses. */
    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(outcomeMessage = null)
    }

    // ─── Mapping helpers ────────────────────────────────────────────────

    private fun PurchaseOutcome.toMessage(product: ProProduct): OutcomeMessage = when (this) {
        is PurchaseOutcome.Ok -> OutcomeMessage.Success(
            "Welcome to PesaTrack Pro — ${product.displayLabel()}."
        )
        PurchaseOutcome.UserCancelled -> OutcomeMessage.Info("Purchase cancelled.")
        is PurchaseOutcome.NetworkError -> OutcomeMessage.Error(
            "Couldn't reach Google Play. Check your connection and try again."
        )
        is PurchaseOutcome.BillingFailed -> OutcomeMessage.Error(
            "Google Play couldn't complete the purchase (code $playResponseCode)."
        )
        is PurchaseOutcome.VerifyFailed -> OutcomeMessage.Error(
            "We couldn't verify the purchase yet. It'll retry automatically — no need to buy again."
        )
    }

    private fun RestoreOutcome.toMessage(): OutcomeMessage = when (this) {
        is RestoreOutcome.PlayUnavailable -> OutcomeMessage.Error(
            "Google Play is currently unavailable. Try again later."
        )
        RestoreOutcome.None -> OutcomeMessage.Info(
            "No previous PesaTrack Pro subscription found on this Google account."
        )
        is RestoreOutcome.Restored -> when {
            verifiedCount > 0 && failedCount == 0 ->
                OutcomeMessage.Success("Restored $verifiedCount subscription${plural(verifiedCount)}.")
            verifiedCount > 0 && failedCount > 0 ->
                OutcomeMessage.Success(
                    "Restored $verifiedCount subscription${plural(verifiedCount)}. " +
                        "$failedCount couldn't be verified yet — we'll retry."
                )
            else -> OutcomeMessage.Error(
                "Found $failedCount subscription${plural(failedCount)} but couldn't verify. Try again shortly."
            )
        }
    }

    private fun plural(n: Int): String = if (n == 1) "" else "s"

    /**
     * When both tiers are present and the annual is a whole-number
     * multiple of the monthly, surface a "KES X/mo, billed annually"
     * caption on the annual tier. Skips when either tier is missing or
     * pricing doesn't reduce cleanly.
     */
    private fun computeSavingsCaption(
        product: ProProduct,
        details: List<ProductDetails>,
    ): String? {
        if (product != ProProduct.ANNUAL) return null
        val annual = details.firstOrNull { it.productId == ProProduct.ANNUAL.productId } ?: return null
        val annualPhase = annual.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull() ?: return null
        val annualMicros = annualPhase.priceAmountMicros
        val currency = annualPhase.priceCurrencyCode.orEmpty()
        if (annualMicros <= 0 || currency.isBlank()) return null
        val monthlyMicros = annualMicros / 12L
        // Format major units with one decimal, no currency-locale voodoo.
        val majorUnits = monthlyMicros.toDouble() / 1_000_000.0
        val formatted = "%s %.2f".format(currency, majorUnits)
        return "$formatted per month, billed annually"
    }

    private fun ProProduct.displayLabel(): String = when (this) {
        ProProduct.MONTHLY -> "Monthly"
        ProProduct.ANNUAL -> "Annual"
    }
}
