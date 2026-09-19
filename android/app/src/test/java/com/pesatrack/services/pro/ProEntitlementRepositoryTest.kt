package com.pesatrack.services.pro

import com.pesatrack.services.ai.PesaTrackAiClient
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Unit test for [ProEntitlementRepository] and its collaborators.
 *
 * Runs against a real [OkHttpClient] pointed at a [MockWebServer] so the
 * Retrofit + Moshi round-trip is genuinely exercised — no HTTP-layer
 * mocks, no reflection tricks. Persistence is stubbed via
 * [FakeProStateStore] so DataStore doesn't need a Context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProEntitlementRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var aiClient: PesaTrackAiClient
    private lateinit var store: FakeProStateStore
    private lateinit var tokenCache: ProTokenCache
    private lateinit var repo: ProEntitlementRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }

        val moshi = Moshi.Builder().build()
        val http = OkHttpClient.Builder()
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(http)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        aiClient = retrofit.create(PesaTrackAiClient::class.java)

        store = FakeProStateStore()
        tokenCache = ProTokenCache()
        repo = ProEntitlementRepository(store, aiClient, tokenCache)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ─── EntitlementLostReason wire-format lock ─────────────────────────

    @Test
    fun `EntitlementLostReason telemetry values match plans allow-list`() {
        // The three values are locked here because Slice A6 will emit them
        // as-is into the pro_entitlement_lost.reason param. If these
        // strings drift, telemetry dashboards silently break.
        assertEquals("expired", EntitlementLostReason.EXPIRED.telemetryValue)
        assertEquals("refunded", EntitlementLostReason.REFUNDED.telemetryValue)
        assertEquals("revoked", EntitlementLostReason.REVOKED.telemetryValue)
    }

    // ─── ProTokenCache basics ───────────────────────────────────────────

    @Test
    fun `ProTokenCache defaults to null and can be updated and cleared`() {
        val cache = ProTokenCache()
        assertNull(cache.currentToken())

        cache.update("GPA.token")
        assertEquals("GPA.token", cache.currentToken())

        cache.update(null)
        assertNull(cache.currentToken())

        // Blank strings are dropped defensively — the interceptor must
        // never send "Authorization: Bearer " alone.
        cache.update("")
        assertNull(cache.currentToken())
        cache.update("   ")
        assertNull(cache.currentToken())
    }

    // ─── State mirror → token cache ─────────────────────────────────────

    @Test
    fun `entitled ProState populates the token cache`() = runBlocking {
        store.emit(
            ProState(
                isEntitled = true,
                tier = ProProduct.MONTHLY,
                purchaseToken = "GPA.mirror.token",
                expiresAtEpochMs = FAR_FUTURE_MS,
            )
        )
        awaitCache { it == "GPA.mirror.token" }
        assertEquals("GPA.mirror.token", tokenCache.currentToken())
    }

    @Test
    fun `unentitled ProState clears the token cache even if a token is persisted`() = runBlocking {
        // A stored token with isEntitled=false is a corrupt or transitional
        // state; the cache must NOT hand it to the interceptor.
        store.emit(
            ProState(
                isEntitled = false,
                purchaseToken = "GPA.stale.token",
                expiresAtEpochMs = FAR_FUTURE_MS,
            )
        )
        awaitCache { it == null }
        assertNull(tokenCache.currentToken())
    }

    // ─── verifyPurchase happy path ──────────────────────────────────────

    @Test
    fun `verifyPurchase 200 OK persists new state and populates cache`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"entitled":true,"productId":"pesatrack_pro_monthly",
                 "expiresAtEpochMs":${FAR_FUTURE_MS},
                 "autoRenewing":true,"isTrialPeriod":false}
                """.trimIndent()
            )
        )

        val result = repo.verifyPurchase("GPA.fresh.token", ProProduct.MONTHLY)

        assertTrue("expected success, got $result", result.isSuccess)
        val state = result.getOrThrow()
        assertTrue(state.isEntitled)
        assertEquals(ProProduct.MONTHLY, state.tier)
        assertEquals("GPA.fresh.token", state.purchaseToken)
        assertEquals(FAR_FUTURE_MS, state.expiresAtEpochMs)
        assertTrue(state.autoRenewing)
        assertNotNull(state.lastVerifiedAtEpochMs)

        // Persistence + cache both updated
        assertEquals(state, store.getProState())
        awaitCache { it == "GPA.fresh.token" }
        assertEquals("GPA.fresh.token", tokenCache.currentToken())
    }

    @Test
    fun `verifyPurchase non-2xx returns failure and does not mutate persisted state`() = runBlocking {
        val before = ProState.DEFAULT
        store.emit(before)

        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = repo.verifyPurchase("GPA.any", ProProduct.ANNUAL)

        assertTrue("expected failure, got $result", result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is EntitlementVerifyException)
        assertEquals(500, (err as EntitlementVerifyException).httpCode)

        // State untouched — no torn write
        assertEquals(before, store.getProState())
    }

    // ─── refreshEntitlement branches ────────────────────────────────────

    @Test
    fun `refreshEntitlement without a persisted token returns NoToken`() = runBlocking {
        store.emit(ProState.DEFAULT)
        val result = repo.refreshEntitlement()
        assertEquals(RefreshResult.NoToken, result)
        // Server should not have been touched
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refreshEntitlement 200 entitled updates persisted state`() = runBlocking {
        store.emit(
            ProState(
                isEntitled = true,
                tier = ProProduct.MONTHLY,
                purchaseToken = "GPA.refresh.token",
                expiresAtEpochMs = 1_000L, // stale-looking; server returns fresh
                autoRenewing = false,
            )
        )
        val newExpiry = FAR_FUTURE_MS
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"entitled":true,"productId":"pesatrack_pro_monthly",
                 "expiresAtEpochMs":$newExpiry,
                 "autoRenewing":true,"isTrialPeriod":false}
                """.trimIndent()
            )
        )

        val result = repo.refreshEntitlement()

        assertTrue("expected Ok, got $result", result is RefreshResult.Ok)
        val updated = (result as RefreshResult.Ok).state
        assertEquals(newExpiry, updated.expiresAtEpochMs)
        assertTrue(updated.autoRenewing)
        assertEquals("GPA.refresh.token", updated.purchaseToken) // token unchanged
        assertEquals(updated, store.getProState())
    }

    @Test
    fun `refreshEntitlement 200 not entitled clears state and returns Revoked`() = runBlocking {
        store.emit(entitledState("GPA.about.to.expire"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"entitled":false,"productId":"pesatrack_pro_monthly",
                 "expiresAtEpochMs":1,"autoRenewing":false,"isTrialPeriod":false}
                """.trimIndent()
            )
        )

        val result = repo.refreshEntitlement()

        assertEquals(RefreshResult.Revoked, result)
        assertEquals(ProState.DEFAULT, store.getProState())
        awaitCache { it == null }
        assertNull(tokenCache.currentToken())
    }

    @Test
    fun `refreshEntitlement 401 treats entitlement as revoked`() = runBlocking {
        store.emit(entitledState("GPA.unknown.to.server"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"missing_bearer_token"}"""))

        val result = repo.refreshEntitlement()

        assertEquals(RefreshResult.Revoked, result)
        assertEquals(ProState.DEFAULT, store.getProState())
    }

    @Test
    fun `refreshEntitlement 500 preserves cached state`() = runBlocking {
        val original = entitledState("GPA.server.hiccup")
        store.emit(original)
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repo.refreshEntitlement()

        assertTrue("expected ServerError, got $result", result is RefreshResult.ServerError)
        assertEquals(500, (result as RefreshResult.ServerError).httpCode)
        // State preserved — do NOT lose the user's entitlement because our server had a bad minute
        assertEquals(original, store.getProState())
    }

    @Test
    fun `refreshEntitlement network I O failure preserves cached state`() = runBlocking {
        val original = entitledState("GPA.offline")
        store.emit(original)
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
        )

        val result = repo.refreshEntitlement()

        assertTrue("expected NetworkError, got $result", result is RefreshResult.NetworkError)
        assertTrue((result as RefreshResult.NetworkError).cause is IOException)
        // State preserved — offline user is still entitled from the cache
        assertEquals(original, store.getProState())
    }

    // ─── isCurrentlyEntitled honest-numbers check ───────────────────────

    @Test
    fun `isCurrentlyEntitled treats past expiry as not entitled`() = runBlocking {
        val past = System.currentTimeMillis() - 1_000L
        store.emit(
            ProState(
                isEntitled = true, // deliberately stale flag
                tier = ProProduct.MONTHLY,
                purchaseToken = "GPA.expired.but.flag.stale",
                expiresAtEpochMs = past,
            )
        )
        assertFalse(repo.isCurrentlyEntitled())
    }

    @Test
    fun `isCurrentlyEntitled honors future expiry`() = runBlocking {
        store.emit(entitledState("GPA.live"))
        assertTrue(repo.isCurrentlyEntitled())
    }

    @Test
    fun `isCurrentlyEntitled false when isEntitled false regardless of expiry`() = runBlocking {
        store.emit(
            ProState(
                isEntitled = false,
                purchaseToken = "GPA.token",
                expiresAtEpochMs = FAR_FUTURE_MS,
            )
        )
        assertFalse(repo.isCurrentlyEntitled())
    }

    // ─── clearEntitlement ───────────────────────────────────────────────

    @Test
    fun `clearEntitlement resets persisted state to DEFAULT`() = runBlocking {
        store.emit(entitledState("GPA.about.to.be.cleared"))
        awaitCache { it == "GPA.about.to.be.cleared" }

        repo.clearEntitlement(EntitlementLostReason.REFUNDED)

        assertEquals(ProState.DEFAULT, store.getProState())
        awaitCache { it == null }
        assertNull(tokenCache.currentToken())
    }

    // ─── Test helpers ───────────────────────────────────────────────────

    private fun entitledState(token: String) = ProState(
        isEntitled = true,
        tier = ProProduct.MONTHLY,
        purchaseToken = token,
        expiresAtEpochMs = FAR_FUTURE_MS,
        autoRenewing = true,
    )

    /**
     * The state-mirror job is asynchronous — it runs on `Dispatchers.Default`
     * and reacts to `store.proState` emissions. Test assertions after a
     * `store.emit()` must wait for the mirror to catch up before reading
     * the token cache. Small poll to avoid races without pulling in the
     * Turbine dep.
     */
    private suspend fun awaitCache(pred: (String?) -> Boolean) {
        withTimeout(1_000) {
            while (!pred(tokenCache.currentToken())) {
                kotlinx.coroutines.yield()
            }
        }
    }

    companion object {
        // ~year 2100 in ms — comfortably in the future for any test
        private const val FAR_FUTURE_MS: Long = 4_102_444_800_000L
    }
}

/**
 * JVM-friendly fake of [ProStateStore] backed by an in-memory
 * [MutableStateFlow]. Every [setProState] emit is observable through
 * [proState] just like the DataStore-backed production implementation.
 */
private class FakeProStateStore(initial: ProState = ProState.DEFAULT) : ProStateStore {
    private val flow: MutableStateFlow<ProState> = MutableStateFlow(initial)

    override val proState: Flow<ProState> = flow.asStateFlow()

    override suspend fun getProState(): ProState = flow.value

    override suspend fun setProState(state: ProState) {
        flow.value = state
    }

    /** Direct emit for arranging state before the repository is exercised. */
    suspend fun emit(state: ProState) {
        flow.value = state
        // give any downstream collector a chance to observe
        flow.first { it == state }
    }
}