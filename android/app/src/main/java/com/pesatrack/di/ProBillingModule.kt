package com.pesatrack.di

import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.services.ai.CoachInsightCache
import com.pesatrack.services.ai.DataDigestBuilder
import com.pesatrack.services.ai.DigestBuilder
import com.pesatrack.services.ai.EntitlementSource
import com.pesatrack.services.ai.PurchaseTokenProvider
import com.pesatrack.services.pro.ProEntitlementRepository
import com.pesatrack.services.pro.ProStateStore
import com.pesatrack.services.pro.ProTokenCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the PesaTrack Pro subsystem.
 *
 * Five `@Binds`:
 *  - [PurchaseTokenProvider] → [ProTokenCache]. This is the mechanism
 *    that lets `ProAuthInterceptor` attach the Bearer header on live
 *    requests as soon as the user's entitlement changes.
 *    `ProEntitlementRepository` writes to the cache whenever the
 *    persisted `ProState` changes; the interceptor reads synchronously
 *    on every guarded request. See [ProTokenCache] for why a separate
 *    class exists — it breaks a Hilt dependency cycle between the auth
 *    interceptor and the repository.
 *  - [ProStateStore] → [AppPreferences]. Narrow persistence contract so
 *    `ProEntitlementRepository` can be unit-tested with a `FakeProStateStore`
 *    on the JVM without pulling in Robolectric.
 *  - [CoachInsightCache] → [AppPreferences]. Phase-2 daily-insight cache
 *    slot. Same interface-then-Preferences pattern so
 *    `CoachInsightRepository` can be exercised with a `FakeCoachInsightCache`
 *    on the JVM. See plans/ai-pro-phase2-spec.md §6.4.
 *  - [EntitlementSource] → [ProEntitlementRepository]. Narrow entitlement
 *    surface so `CoachInsightRepository` tests don't need to construct
 *    a whole `ProEntitlementRepository` (which requires a Retrofit
 *    client, token cache, telemetry client, and persistent state store).
 *  - [DigestBuilder] → [DataDigestBuilder]. Narrow digest-build surface
 *    so `CoachInsightRepository` tests don't need to construct a
 *    `DataDigestBuilder` with its six DAOs + preferences +
 *    RecurringExpenseService.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProBillingModule {

    @Binds
    @Singleton
    abstract fun bindPurchaseTokenProvider(
        cache: ProTokenCache,
    ): PurchaseTokenProvider

    @Binds
    @Singleton
    abstract fun bindProStateStore(
        appPreferences: AppPreferences,
    ): ProStateStore

    @Binds
    @Singleton
    abstract fun bindCoachInsightCache(
        appPreferences: AppPreferences,
    ): CoachInsightCache

    /**
     * Narrow entitlement-check surface for [com.pesatrack.services.ai.CoachInsightRepository].
     * See the interface KDoc for why we don't inject the concrete
     * [ProEntitlementRepository] directly (JVM-only testability).
     */
    @Binds
    @Singleton
    abstract fun bindEntitlementSource(
        repository: ProEntitlementRepository,
    ): EntitlementSource

    /**
     * Narrow digest-build surface for [com.pesatrack.services.ai.CoachInsightRepository].
     * The concrete builder needs six DAOs + `AppPreferences` +
     * `RecurringExpenseService`; the interface lets tests supply a
     * one-line fake.
     */
    @Binds
    @Singleton
    abstract fun bindDigestBuilder(
        builder: DataDigestBuilder,
    ): DigestBuilder
}
