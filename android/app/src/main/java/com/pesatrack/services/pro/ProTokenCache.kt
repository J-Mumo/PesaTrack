package com.pesatrack.services.pro

import com.pesatrack.services.ai.PurchaseTokenProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-dependency singleton holding the current raw Google Play purchase
 * token as a `@Volatile` field.
 *
 * Its whole purpose is to break a Hilt circular dependency:
 *
 * ```
 * ProAuthInterceptor  →  PurchaseTokenProvider
 *                              ↓
 *         (if the repository is the provider)
 *                              ↓
 *                      ProEntitlementRepository
 *                              ↓
 *                      PesaTrackAiClient  →  Retrofit  →  OkHttp
 *                                                            ↓
 *                                              ProAuthInterceptor   ← CYCLE
 * ```
 *
 * By making the cache a separate dep-free singleton, the interceptor's
 * `PurchaseTokenProvider` is satisfied by [ProTokenCache] with no
 * transitive references. `ProEntitlementRepository` writes to it whenever
 * the persisted `ProState` changes; the interceptor reads from it on
 * every guarded request.
 *
 * Reads happen from the OkHttp interceptor thread — [currentToken] is
 * synchronous by design (no coroutines, no I/O). `@Volatile` guarantees
 * the reader sees the last write without needing a lock.
 *
 * See plans/ai-pro-phase1-spec.md §3.4.
 */
@Singleton
class ProTokenCache @Inject constructor() : PurchaseTokenProvider {

    @Volatile
    private var cachedToken: String? = null

    override fun currentToken(): String? = cachedToken

    /**
     * Called by [ProEntitlementRepository] whenever the persisted
     * `ProState` changes. Set to `null` to revoke — the interceptor's
     * next guarded request will synthesise a 401.
     */
    fun update(token: String?) {
        cachedToken = token?.takeIf { it.isNotBlank() }
    }
}
