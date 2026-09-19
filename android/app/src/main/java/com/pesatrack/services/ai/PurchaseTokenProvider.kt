package com.pesatrack.services.ai

/**
 * One-method interface that returns the current raw Google Play purchase
 * token for the entitled user, or `null` if no active entitlement is on
 * file.
 *
 * Reads on this interface happen from an OkHttp interceptor thread on
 * every guarded network call, so implementations MUST return a cached
 * value synchronously — no I/O, no coroutines, no blocking DataStore
 * reads. `ProEntitlementRepository` (Slice A4) will maintain a
 * `@Volatile` copy that mirrors the persisted [com.pesatrack.services.pro.ProState].
 *
 * Slice A3 wires the default binding to `PurchaseTokenProvider.None`
 * (always null) so the interceptor code path is exercisable end-to-end
 * without the repository. Slice A4 replaces the Hilt binding with the
 * live repository — no interceptor changes required.
 *
 * See plans/ai-pro-phase1-spec.md §3.4.
 */
fun interface PurchaseTokenProvider {

    /**
     * Current raw purchase token, or `null` if the user has no active
     * entitlement.
     */
    fun currentToken(): String?

    companion object {
        /**
         * Default token provider used by the Hilt module until Slice A4's
         * `ProEntitlementRepository` binds a real one. Always returns
         * `null` — every auth-guarded request will short-circuit with the
         * synthetic 401 from [ProAuthInterceptor].
         */
        val None: PurchaseTokenProvider = PurchaseTokenProvider { null }
    }
}
