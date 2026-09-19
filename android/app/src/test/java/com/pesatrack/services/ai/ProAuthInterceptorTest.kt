package com.pesatrack.services.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

/**
 * Tests the auth-gating policy in [ProAuthInterceptor] end-to-end through
 * a real [OkHttpClient] pointed at a [MockWebServer]. This is the source
 * of truth for the "which paths need Bearer" contract — the wire-level
 * behaviour matters more than any abstract policy definition.
 */
class ProAuthInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private var stubbedToken: String? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        stubbedToken = null
        val tokenProvider = PurchaseTokenProvider { stubbedToken }
        client = OkHttpClient.Builder()
            .addInterceptor(ProAuthInterceptor(tokenProvider))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ─── needsAuth policy (pure decision) ────────────────────────────────

    @Test
    fun `needsAuth is false for public paths`() {
        assertFalse(ProAuthInterceptor.needsAuth("/health"))
        assertFalse(ProAuthInterceptor.needsAuth("/billing/verify"))
        assertFalse(ProAuthInterceptor.needsAuth("/"))
    }

    @Test
    fun `needsAuth is true for guarded paths`() {
        assertTrue(ProAuthInterceptor.needsAuth("/billing/entitlement"))
        assertTrue(ProAuthInterceptor.needsAuth("/ai/echo"))
        assertTrue(ProAuthInterceptor.needsAuth("/ai/coach"))
        assertTrue(ProAuthInterceptor.needsAuth("/ai/chat/stream"))
    }

    @Test
    fun `needsAuth handles missing leading slash`() {
        // OkHttp always gives an encoded path with a leading '/', but the
        // helper should behave sensibly if a caller strips it.
        assertTrue(ProAuthInterceptor.needsAuth("ai/echo"))
        assertFalse(ProAuthInterceptor.needsAuth("health"))
    }

    // ─── /health: no auth, dispatched ────────────────────────────────────

    @Test
    fun `health request dispatched without Authorization header even when token exists`() {
        stubbedToken = "GPA.token.1234"
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

        val response = client.newCall(Request.Builder().url(server.url("/health")).build()).execute()
        response.close()

        val recorded = server.takeRequest()
        assertEquals(1, server.requestCount)
        assertNull(
            "Authorization header must NEVER be sent to /health",
            recorded.getHeader("Authorization"),
        )
    }

    // ─── /billing/verify: no auth, dispatched even without token ────────

    @Test
    fun `billing verify dispatched without Authorization header even when token exists`() {
        stubbedToken = "GPA.token.1234"
        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/billing/verify"))
            .post(ByteArray(0).toRequestBody())
            .build()
        client.newCall(request).execute().close()

        val recorded = server.takeRequest()
        assertNull(
            "Authorization header must not be added on /billing/verify — token is in body",
            recorded.getHeader("Authorization"),
        )
    }

    // ─── Missing-token short-circuit ─────────────────────────────────────

    @Test
    fun `guarded request without token returns synthetic 401 and never hits the wire`() {
        stubbedToken = null
        // Deliberately do NOT enqueue a response — if the interceptor
        // dispatches, the test hangs/fails.

        val request = Request.Builder().url(server.url("/ai/echo")).build()
        val response = client.newCall(request).execute()

        assertEquals(401, response.code)
        assertEquals(0, server.requestCount)
        val body = response.body?.string().orEmpty()
        assertTrue(
            "Body should identify the short-circuit source, got: $body",
            body.contains("client_interceptor"),
        )
        response.close()
    }

    @Test
    fun `guarded request with blank token also short-circuits (defensive)`() {
        stubbedToken = "" // shouldn't happen in practice but must not send `Bearer ` alone
        val response = client.newCall(
            Request.Builder().url(server.url("/billing/entitlement")).build()
        ).execute()

        assertEquals(401, response.code)
        assertEquals(0, server.requestCount)
        response.close()
    }

    // ─── Happy path: Bearer added, request dispatched ────────────────────

    @Test
    fun `guarded request with token attaches Bearer and forwards request`() {
        stubbedToken = "GPA.subs.happy-path-token"
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"entitled":true}"""))

        val response = client.newCall(
            Request.Builder().url(server.url("/billing/entitlement")).build()
        ).execute()

        assertEquals(200, response.code)
        val recorded = server.takeRequest()
        assertNotNull(recorded)
        assertEquals(
            "Bearer GPA.subs.happy-path-token",
            recorded.getHeader("Authorization"),
        )
        response.close()
    }

    @Test
    fun `ai path with token attaches Bearer`() {
        stubbedToken = "GPA.ai.token"
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(
            Request.Builder().url(server.url("/ai/echo"))
                .post(ByteArray(0).toRequestBody())
                .build()
        ).execute().close()

        val recorded = server.takeRequest()
        assertEquals("Bearer GPA.ai.token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `token changes between requests are picked up (no caching by interceptor)`() {
        // Regression guard: the interceptor must call currentToken() on
        // every request, not cache the first value. This is what lets
        // Slice A4's ProEntitlementRepository invalidate a revoked entitlement
        // by simply nulling out its @Volatile field.
        stubbedToken = "first"
        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(Request.Builder().url(server.url("/ai/echo")).build()).execute().close()
        assertEquals("Bearer first", server.takeRequest().getHeader("Authorization"))

        stubbedToken = "second"
        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(Request.Builder().url(server.url("/ai/echo")).build()).execute().close()
        assertEquals("Bearer second", server.takeRequest().getHeader("Authorization"))

        stubbedToken = null
        val response = client.newCall(
            Request.Builder().url(server.url("/ai/echo")).build()
        ).execute()
        assertEquals(401, response.code)
        // Only two of the three requests should have reached the server.
        assertEquals(2, server.requestCount)
        response.close()
    }
}
