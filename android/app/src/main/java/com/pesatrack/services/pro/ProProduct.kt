package com.pesatrack.services.pro

import com.squareup.moshi.Json

/**
 * PesaTrack Pro subscription tiers offered on Google Play.
 *
 * The [productId] strings MUST match the subscription SKU IDs configured in
 * the Play Console. Both the Android client and the backend
 * (`pesatrack-api.jmumo.com` — see `backend/src/services/playBilling.js`
 * `KNOWN_PRODUCT_IDS`) use these exact strings as the durable identity for
 * the tier — do not change without a coordinated migration.
 *
 * The `@Json(name = ...)` short labels ("monthly" / "annual") are the wire
 * format used inside the persisted `ProState` JSON blob (see
 * [ProStateJson]). Persisting the short name rather than the Kotlin
 * `Enum.name` decouples the on-disk schema from Kotlin identifiers.
 *
 * See plans/ai-pro-phase1-spec.md §3.1.
 */
enum class ProProduct(val productId: String) {
    @Json(name = "monthly")
    MONTHLY("pesatrack_pro_monthly"),

    @Json(name = "annual")
    ANNUAL("pesatrack_pro_annual");

    companion object {
        private val byProductId: Map<String, ProProduct> =
            entries.associateBy(ProProduct::productId)

        /**
         * Reverse lookup from the Play Billing `productId` string to the
         * enum. Returns `null` for unknown ids so callers can decide whether
         * to log-and-drop (backend receives an unexpected purchase) or fail
         * loudly (client tried to launch an unsupported flow).
         */
        fun fromProductId(productId: String?): ProProduct? =
            productId?.let { byProductId[it] }
    }
}
