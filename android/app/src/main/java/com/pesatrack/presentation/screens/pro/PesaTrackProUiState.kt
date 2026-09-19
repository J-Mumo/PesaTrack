package com.pesatrack.presentation.screens.pro

import com.pesatrack.services.pro.ProProduct
import com.pesatrack.services.pro.ProState

/**
 * UI-facing state for the PesaTrack Pro subscription screen. Kept as a
 * dumb value class per the codebase MVVM convention (see `AGENTS.md`).
 *
 * The screen self-adapts through three main variants:
 *  - **Loading** — initial product-details fetch in flight.
 *  - **Coming soon** — Play returned zero product details (SKUs not yet
 *    published in Play Console). Renders a placeholder; no subscribe
 *    buttons; no purchase attempt is possible.
 *  - **Available** — [tiers] is non-empty; render tier cards.
 * And an orthogonal variant:
 *  - **Entitled** — [currentState.isEntitled] is true; the top of the
 *    screen shows the current tier + expiry instead of an upsell.
 *
 * See plans/ai-pro-phase1-spec.md §3.1.
 */
data class PesaTrackProUiState(
    val loading: Boolean = true,
    val currentState: ProState = ProState.DEFAULT,
    val tiers: List<TierViewData> = emptyList(),
    val purchaseInFlight: Boolean = false,
    val restoreInFlight: Boolean = false,
    val outcomeMessage: OutcomeMessage? = null,
) {
    /**
     * True when Play returned zero product details. The screen renders a
     * "coming soon" placeholder in this case rather than blank tier
     * cards. This is the intended state when v1.6.0 ships before the
     * Play Console SKUs are published — users simply see a placeholder
     * and no purchase attempt is possible.
     */
    val showComingSoonPlaceholder: Boolean
        get() = !loading && tiers.isEmpty() && !currentState.isEntitled

    /** True when the current user is actively entitled (any tier, not-yet expired). */
    val isCurrentlyEntitled: Boolean
        get() = currentState.isEntitled
            && (currentState.expiresAtEpochMs ?: 0L) > System.currentTimeMillis()
}

/**
 * Per-tier data ready for rendering. Populated from Play's
 * `ProductDetails` — we deliberately keep only what the UI displays,
 * not the whole SDK type, so previews/tests don't need Play on the
 * classpath.
 */
data class TierViewData(
    val product: ProProduct,
    /** e.g. `"KES 500.00"` — from `ProductDetails.SubscriptionOfferDetails.PricingPhase.formattedPrice`. */
    val formattedPrice: String,
    /** ISO 8601 period from Play, e.g. `"P1M"` or `"P1Y"`. Human-mapping happens at render time. */
    val billingPeriod: String,
    /**
     * Optional secondary caption — e.g. `"KES 41.67 per month, billed
     * annually"` — computed by the ViewModel where a per-month figure
     * makes sense to surface (annual tier only, if pricing is uniform).
     */
    val savingsCaption: String? = null,
)

/**
 * Small snackbar-ready message envelope. Sealed so `when(outcome)` in
 * the composable stays exhaustive.
 */
sealed class OutcomeMessage {
    abstract val text: String

    data class Success(override val text: String) : OutcomeMessage()
    data class Error(override val text: String) : OutcomeMessage()
    data class Info(override val text: String) : OutcomeMessage()
}
