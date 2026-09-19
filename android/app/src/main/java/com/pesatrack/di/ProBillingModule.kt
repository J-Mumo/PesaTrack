package com.pesatrack.di

import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.services.ai.PurchaseTokenProvider
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
 * Two `@Binds`:
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
}
