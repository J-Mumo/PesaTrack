package com.pesatrack.di

import com.pesatrack.BuildConfig
import com.pesatrack.services.ai.PesaTrackAiClient
import com.pesatrack.services.ai.ProAuthInterceptor
import com.pesatrack.services.ai.PurchaseTokenProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for the AI Pro backend HTTP stack. Provides a singleton
 * [PesaTrackAiClient] pointed at `https://pesatrack-api.jmumo.com`,
 * wired through OkHttp with:
 *  - [ProAuthInterceptor] injecting the `Authorization: Bearer <token>`
 *    header when appropriate (Slice A3)
 *  - `HttpLoggingInterceptor` at `BODY` level in debug builds and `NONE`
 *    in release — request bodies must **never** land in production logs
 *    (see plans/ai-pro-phase1-spec.md privacy contract).
 *
 * Timeouts follow §3.4 of the plan: 30 s connect, 60 s read/write.
 *
 * The [PurchaseTokenProvider] binding is the [PurchaseTokenProvider.None]
 * default until Slice A4's `ProEntitlementRepository` replaces it with a
 * live cache. No changes to this module are required at that point —
 * Slice A4 will register its own `@Provides` in a repository-scoped
 * module and mark this one for override, or simply publish an
 * `@Inject`ed repository that satisfies the interface.
 *
 * See plans/ai-pro-phase1-spec.md §3.4.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiHttpModule {

    /** Public base URL of the AI Pro backend. Slice A1 whitelists this host
     *  in `res/xml/network_security_config.xml`. */
    const val BASE_URL: String = "https://pesatrack-api.jmumo.com/"

    private const val CONNECT_TIMEOUT_SECONDS: Long = 30
    private const val READ_TIMEOUT_SECONDS: Long = 60
    private const val WRITE_TIMEOUT_SECONDS: Long = 60

    /**
     * Named qualifier so consumers that need a shared Moshi instance can
     * reference this one instead of building their own. Codegen adapters
     * do not require any factory registration; [KotlinJsonAdapterFactory]
     * is added only to cover ad-hoc data classes without
     * `@JsonClass(generateAdapter = true)` (belt-and-braces — not
     * currently exercised by anything in this module).
     */
    @Provides
    @Singleton
    @Named("aiPro")
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * Slice-A3 default token source. Slice A4's `ProEntitlementRepository`
     * will replace this binding with a `@Volatile`-backed live cache
     * mirroring the persisted `ProState`.
     */
    @Provides
    @Singleton
    fun providePurchaseTokenProvider(): PurchaseTokenProvider =
        PurchaseTokenProvider.None

    @Provides
    @Singleton
    fun provideProAuthInterceptor(
        tokenProvider: PurchaseTokenProvider,
    ): ProAuthInterceptor = ProAuthInterceptor(tokenProvider)

    @Provides
    @Singleton
    @Named("aiPro")
    fun provideOkHttpClient(
        proAuthInterceptor: ProAuthInterceptor,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Auth interceptor is an application-level interceptor: it runs
            // once per logical call, sees redirects transparently, and short-
            // circuits before touching the wire when it needs to.
            .addInterceptor(proAuthInterceptor)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            // Network-layer (not application-layer) so we log the actual
            // wire bytes AFTER our auth interceptor has run. That gives the
            // developer the authorized request as sent, and lets us verify
            // the interceptor's short-circuit branch by absence of a log
            // entry.
            builder.addNetworkInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("aiPro")
    fun provideRetrofit(
        @Named("aiPro") okHttpClient: OkHttpClient,
        @Named("aiPro") moshi: Moshi,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun providePesaTrackAiClient(
        @Named("aiPro") retrofit: Retrofit,
    ): PesaTrackAiClient = retrofit.create(PesaTrackAiClient::class.java)
}
