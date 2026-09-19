package com.pesatrack.services.pro

import com.squareup.moshi.JsonClass

/**
 * Snapshot of the user's PesaTrack Pro entitlement, persisted as a single
 * JSON blob in DataStore under `KEY_PRO_STATE` (see
 * `AppPreferences.proState`). Persisted as **one atomic write** so we never
 * observe a torn state (e.g. `purchaseToken` present but
 * `expiresAtEpochMs` missing).
 *
 * Contract:
 *   - The default (unpurchased) state is [DEFAULT]: `isEntitled = false` and
 *     every optional field `null`.
 *   - [purchaseToken] is the **raw** Google Play Billing purchase token; it
 *     stays only on-device. The backend stores `SHA-256(token)` — never the
 *     raw value. See `backend/src/services/playBilling.js`.
 *   - [expiresAtEpochMs] uses UTC milliseconds since epoch. `isEntitled`
 *     must remain honest against this: if the persisted expiry is in the
 *     past, the effective entitlement is expired even if `isEntitled` is
 *     still `true` from the last verify. Callers must apply that check at
 *     read time; `ProEntitlementRepository` (Slice A4) is the single owner
 *     of that logic — this data class deliberately stays a dumb value.
 *   - [autoRenewing] mirrors the field returned by
 *     `/billing/verify` on the backend. `false` means the user cancelled
 *     but the entitlement is valid until [expiresAtEpochMs].
 *
 * The JSON wire format is versioned via the DataStore key name
 * (`pro_state_json_v1`), not via a field inside the payload. When we need
 * `_v2`, migrate by reading the old key, transforming, writing the new
 * key, and only then deleting the old.
 *
 * See plans/ai-pro-phase1-spec.md §3.2.
 */
@JsonClass(generateAdapter = true)
data class ProState(
    val isEntitled: Boolean = false,
    val tier: ProProduct? = null,
    val purchaseToken: String? = null,
    val expiresAtEpochMs: Long? = null,
    val lastVerifiedAtEpochMs: Long? = null,
    val autoRenewing: Boolean = false,
) {
    companion object {
        /** Persisted default for a fresh install — everyone starts here. */
        val DEFAULT: ProState = ProState()
    }
}
