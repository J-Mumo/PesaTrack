package com.pesatrack.services.ai

import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for [AskYourMoneyClient] — the raw SSE plumbing that translates
 * `text` / `done` / `error` frames into [AskStreamEvent]s and never
 * throws.
 *
 * Uses a real [MockWebServer] and a real OkHttp client so the framing
 * is genuinely exercised. We rewrite `BASE_URL` for the test by putting
 * a wrapper subclass in the same package — actually no, we can just
 * point the client's baseUrl by injecting our own subclass that
 * overrides the target. Cleaner: since [AskYourMoneyClient] hard-codes
 * BASE_URL, we make the URL host-portable by swapping the URL construction
 * for the test via reflection on `BASE_URL`. That's ugly. Simpler: expose
 * the URL as an injectable value via a testing-only secondary
 * constructor, OR just verify the target-URL portion of the request in
 * a MockWebServer.enqueue(...) call and rely on OkHttp reaching it.
 *
 * We take the cleanest route: build a dedicated test client that
 * inherits from AskYourMoneyClient but overrides the URL by wrapping.
 * Since Kotlin `const val` in a companion isn't overridable, we create
 * a wrapping test factory that constructs the request against
 * MockWebServer's base URL and delegates to the same private helpers.
 * That's too much testing-only surface. **Chosen approach:** promote
 * `BASE_URL` to an internal `open` companion property replaceable in
 * tests via a package-visible setter. Done via `internal var
 * testBaseUrlOverride`.
 *
 * See implementation for the hook.
 */
class AskYourMoneyClientTest {

    private lateinit var server: MockWebServer
    private lateinit var okHttp: OkHttpClient
    private lateinit var moshi: Moshi
    private lateinit var client: AskYourMoneyClient

    /**
     * Wrap each test in a hard timeout. Any regression in the SSE
     * plumbing that fails to close the flow (e.g. `onClosed` returning
     * without calling `close()`) hangs `.toList()` forever — this
     * turns that class of bug into a clean 5-second failure instead of
     * a stuck Gradle daemon.
     */
    private fun runBlockingTest(body: suspend () -> Unit): Unit = runBlocking {
        withTimeout(5_000) { body() }
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        okHttp = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        moshi = Moshi.Builder().build()
        // Point the client at MockWebServer via the testing override.
        AskYourMoneyClient.testBaseUrlOverride = server.url("/").toString()
        client = AskYourMoneyClient(okHttp, moshi)
    }

    @After
    fun tearDown() {
        AskYourMoneyClient.testBaseUrlOverride = null
        server.shutdown()
    }

    // ── Happy path ─────────────────────────────────────────────────────

    @Test
    fun `streams text chunks then emits Done on a successful done frame`() = runBlockingTest {
        server.enqueue(sseResponse(
            """
            event: text
            data: {"delta":"You have "}

            event: text
            data: {"delta":"spent KES 12,400."}

            event: done
            data: {"fallback":false,"body":"You have spent KES 12,400.","assumptions":[],"action_label":null,"action_deeplink":null,"chart":null}

            """.trimIndent(),
        ))

        val events = client.stream(fixtureRequest()).toList()

        // Should be exactly two TextChunks + one Done.
        val texts = events.filterIsInstance<AskStreamEvent.TextChunk>()
        val dones = events.filterIsInstance<AskStreamEvent.Done>()
        val fallbacks = events.filterIsInstance<AskStreamEvent.Fallback>()

        assertEquals(2, texts.size)
        assertEquals("You have ", texts[0].delta)
        assertEquals("spent KES 12,400.", texts[1].delta)

        assertEquals(1, dones.size)
        assertEquals("You have spent KES 12,400.", dones[0].response.body)

        assertEquals("must not emit fallback on happy path", 0, fallbacks.size)
    }

    @Test
    fun `Done envelope with fallback true maps to Fallback event with reason`() = runBlockingTest {
        server.enqueue(sseResponse(
            """
            event: text
            data: {"delta":"streamed but eventually rejected"}

            event: done
            data: {"fallback":true,"reason":"projection_no_assumptions"}

            """.trimIndent(),
        ))

        val events = client.stream(fixtureRequest()).toList()
        val fallbacks = events.filterIsInstance<AskStreamEvent.Fallback>()

        assertEquals(1, fallbacks.size)
        assertEquals("projection_no_assumptions", fallbacks[0].reason)
    }

    @Test
    fun `Done envelope missing required success fields maps to schema fallback`() = runBlockingTest {
        // fallback is false but the required 'body' field isn't present.
        server.enqueue(sseResponse(
            """
            event: done
            data: {"fallback":false}

            """.trimIndent(),
        ))

        val events = client.stream(fixtureRequest()).toList()
        val fallbacks = events.filterIsInstance<AskStreamEvent.Fallback>()

        assertEquals(1, fallbacks.size)
        assertEquals("schema", fallbacks[0].reason)
    }

    @Test
    fun `unparseable done frame maps to schema fallback`() = runBlockingTest {
        server.enqueue(sseResponse(
            """
            event: done
            data: not-json

            """.trimIndent(),
        ))

        val events = client.stream(fixtureRequest()).toList()
        val fallbacks = events.filterIsInstance<AskStreamEvent.Fallback>()

        assertEquals(1, fallbacks.size)
        assertEquals("schema", fallbacks[0].reason)
    }

    @Test
    fun `text frame with missing delta is ignored (not a fatal fallback)`() = runBlockingTest {
        server.enqueue(sseResponse(
            """
            event: text
            data: {}

            event: text
            data: {"delta":"real content"}

            event: done
            data: {"fallback":false,"body":"real content","assumptions":[],"action_label":null,"action_deeplink":null,"chart":null}

            """.trimIndent(),
        ))

        val events = client.stream(fixtureRequest()).toList()
        val texts = events.filterIsInstance<AskStreamEvent.TextChunk>()

        // Empty-delta frame silently ignored; the real one still shows up.
        assertEquals(1, texts.size)
        assertEquals("real content", texts[0].delta)
    }

    // ── HTTP failure → Fallback ────────────────────────────────────────

    @Test
    fun `HTTP 429 maps to rate_limit fallback`() = runBlockingTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":\"rate_limited\"}"))

        val events = client.stream(fixtureRequest()).toList()
        val fallbacks = events.filterIsInstance<AskStreamEvent.Fallback>()

        assertEquals(1, fallbacks.size)
        assertEquals("rate_limit", fallbacks[0].reason)
    }

    @Test
    fun `HTTP 400 maps to server_error fallback (client bug shape)`() = runBlockingTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_request\"}"))

        val events = client.stream(fixtureRequest()).toList()
        val fallbacks = events.filterIsInstance<AskStreamEvent.Fallback>()

        assertEquals(1, fallbacks.size)
        assertEquals("server_error", fallbacks[0].reason)
    }

    @Test
    fun `HTTP 502 maps to network fallback`() = runBlockingTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))

        val events = client.stream(fixtureRequest()).toList()
        val fallbacks = events.filterIsInstance<AskStreamEvent.Fallback>()

        assertEquals(1, fallbacks.size)
        assertEquals("network", fallbacks[0].reason)
    }

    @Test
    fun `POSTed body contains the request digest question and history keys`() = runBlockingTest {
        server.enqueue(sseResponse(
            """
            event: done
            data: {"fallback":false,"body":"ok","assumptions":[],"action_label":null,"action_deeplink":null,"chart":null}

            """.trimIndent(),
        ))
        val request = fixtureRequest(
            question = "How much have I spent on Food & Dining this month?",
            history = listOf(
                AskTurn(role = "user", content = "earlier question"),
                AskTurn(role = "assistant", content = "earlier answer"),
            ),
        )
        client.stream(request).first { it is AskStreamEvent.Done }

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("body must contain 'digest' key", body.contains("\"digest\""))
        assertTrue("body must contain 'history' key", body.contains("\"history\""))
        assertTrue("body must contain 'question' key", body.contains("\"question\""))
        assertTrue(
            "body must contain the verbatim question",
            body.contains("How much have I spent on Food & Dining this month?"),
        )
        assertTrue(
            "body must contain the earlier history turn labels",
            body.contains("earlier question") && body.contains("earlier answer"),
        )
    }

    // ── Fixtures ────────────────────────────────────────────────────────

    private fun sseResponse(body: String): MockResponse {
        // OkHttp's SSE parser only dispatches an event once it sees a
        // blank line terminator (`\n\n`). trimIndent() strips the
        // trailing blank line from the raw string, so the last event's
        // data line never gets its own dispatch. Always append the
        // terminator here so callers can write natural fixtures.
        val terminated = if (body.endsWith("\n\n")) body else body.trimEnd('\n') + "\n\n"
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream; charset=utf-8")
            .setBody(terminated)
    }

    private fun fixtureRequest(
        question: String = "How much did I spend on Food & Dining this month?",
        history: List<AskTurn> = emptyList(),
    ): AskRequestDto = AskRequestDto(
        digest = fixtureDigest(),
        history = history,
        question = question,
    )

    private fun fixtureDigest(): DataDigest = DataDigest(
        period = "2026-09",
        monthStartDay = 1,
        daysElapsed = 15,
        daysTotal = 30,
        totals = DigestTotals(
            spent = 42800,
            spentLastPeriod = 38200,
            spent3moAvg = 39500,
            incomeEst = 85000,
            investedThisPeriod = 6000,
        ),
        categories = emptyList(),
        recurring = emptyList(),
        topRecipientsThisPeriod = emptyList(),
        anomaliesThisWeek = emptyList(),
    )
}
