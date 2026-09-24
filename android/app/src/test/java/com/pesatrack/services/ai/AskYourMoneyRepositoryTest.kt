package com.pesatrack.services.ai

import com.pesatrack.services.ai.DataDigestBuilder.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import com.squareup.moshi.Moshi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for [AskYourMoneyRepository] — the orchestration layer that
 * gates on entitlement, rebuilds the digest per turn, trims history to
 * 10 turns, and delegates SSE plumbing to [AskYourMoneyClient].
 *
 * The client uses MockWebServer (same pattern as
 * [AskYourMoneyClientTest]) so we exercise the real Moshi
 * serialisation and OkHttp lifecycle. Entitlement + digest sources are
 * hand-built fakes.
 */
class AskYourMoneyRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AskYourMoneyClient
    private lateinit var okHttp: OkHttpClient
    private lateinit var moshi: Moshi

    private val entitledSource = FakeEntitlementSource(entitled = true)
    private val notEntitledSource = FakeEntitlementSource(entitled = false)

    /** See AskYourMoneyClientTest for rationale — hard-cap runaway hangs. */
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
        AskYourMoneyClient.testBaseUrlOverride = server.url("/").toString()
        client = AskYourMoneyClient(okHttp, moshi)
    }

    @After
    fun tearDown() {
        AskYourMoneyClient.testBaseUrlOverride = null
        server.shutdown()
    }

    // ── Short-circuits (no HTTP call) ──────────────────────────────────

    @Test
    fun `blank question short-circuits with empty_question fallback (no HTTP call)`() = runBlockingTest {
        val repo = AskYourMoneyRepository(
            entitlement = entitledSource,
            digestBuilder = FakeDigestBuilder(),
            client = client,
        )
        val events = repo.ask(question = "   ", history = emptyList()).toList()
        assertEquals(1, events.size)
        val f = events[0] as AskStreamEvent.Fallback
        assertEquals("empty_question", f.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `not entitled short-circuits with not_entitled fallback (no HTTP call)`() = runBlockingTest {
        val repo = AskYourMoneyRepository(
            entitlement = notEntitledSource,
            digestBuilder = FakeDigestBuilder(),
            client = client,
        )
        val events = repo.ask(question = "how much?", history = emptyList()).toList()
        assertEquals(1, events.size)
        val f = events[0] as AskStreamEvent.Fallback
        assertEquals("not_entitled", f.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `digest builder throw short-circuits with digest_error fallback (no HTTP call)`() = runBlockingTest {
        val repo = AskYourMoneyRepository(
            entitlement = entitledSource,
            digestBuilder = ThrowingDigestBuilder(),
            client = client,
        )
        val events = repo.ask(question = "how much?", history = emptyList()).toList()
        assertEquals(1, events.size)
        val f = events[0] as AskStreamEvent.Fallback
        assertEquals("digest_error", f.reason)
        assertEquals(0, server.requestCount)
    }

    // ── Happy path ─────────────────────────────────────────────────────

    @Test
    fun `happy path delegates to client and forwards streamed events`() = runBlockingTest {
        server.enqueue(sseResponse(
            """
            event: text
            data: {"delta":"You have spent "}

            event: text
            data: {"delta":"KES 12,400."}

            event: done
            data: {"fallback":false,"body":"You have spent KES 12,400.","assumptions":[],"action_label":null,"action_deeplink":null,"chart":null}

            """.trimIndent(),
        ))
        val repo = AskYourMoneyRepository(
            entitlement = entitledSource,
            digestBuilder = FakeDigestBuilder(),
            client = client,
        )

        val events = repo.ask(
            question = "How much did I spend?",
            history = emptyList(),
        ).toList()

        val texts = events.filterIsInstance<AskStreamEvent.TextChunk>()
        val dones = events.filterIsInstance<AskStreamEvent.Done>()
        assertEquals(2, texts.size)
        assertEquals(1, dones.size)
        assertEquals("You have spent KES 12,400.", dones[0].response.body)
        assertEquals(1, server.requestCount)
    }

    // ── History trim ───────────────────────────────────────────────────

    @Test
    fun `history longer than 10 turns is trimmed to last 10 on the wire`() = runBlockingTest {
        server.enqueue(sseResponse(
            """
            event: done
            data: {"fallback":false,"body":"ok","assumptions":[],"action_label":null,"action_deeplink":null,"chart":null}

            """.trimIndent(),
        ))
        val history = (1..15).map { i ->
            AskTurn(role = if (i % 2 == 0) "assistant" else "user", content = "turn_${String.format("%02d", i)}")
        }
        val repo = AskYourMoneyRepository(
            entitlement = entitledSource,
            digestBuilder = FakeDigestBuilder(),
            client = client,
        )
        repo.ask(question = "latest", history = history).first { it is AskStreamEvent.Done }

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        // Turns 1..5 (oldest five) must be trimmed; 6..15 kept.
        assertTrue("trimmed turn 1 should not appear", !body.contains("turn_01"))
        assertTrue("trimmed turn 5 should not appear", !body.contains("turn_05"))
        assertTrue("kept turn 6 should appear", body.contains("turn_06"))
        assertTrue("kept turn 15 should appear", body.contains("turn_15"))
    }

    @Test
    fun `history at exactly 10 turns is sent verbatim`() = runBlockingTest {
        server.enqueue(sseResponse(
            """
            event: done
            data: {"fallback":false,"body":"ok","assumptions":[],"action_label":null,"action_deeplink":null,"chart":null}

            """.trimIndent(),
        ))
        val history = (1..10).map { i ->
            AskTurn(role = "user", content = "turn_${String.format("%02d", i)}")
        }
        val repo = AskYourMoneyRepository(
            entitlement = entitledSource,
            digestBuilder = FakeDigestBuilder(),
            client = client,
        )
        repo.ask(question = "latest", history = history).first { it is AskStreamEvent.Done }

        val body = server.takeRequest().body.readUtf8()
        (1..10).forEach { i ->
            assertTrue(
                "turn_${String.format("%02d", i)} should appear in body",
                body.contains("turn_${String.format("%02d", i)}"),
            )
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────

    private fun sseResponse(body: String): MockResponse {
        // See AskYourMoneyClientTest.sseResponse for the terminator
        // rationale. Keeping the same convention here so fixtures
        // written in either file behave identically.
        val terminated = if (body.endsWith("\n\n")) body else body.trimEnd('\n') + "\n\n"
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream; charset=utf-8")
            .setBody(terminated)
    }

    /** Fake entitlement that returns a fixed value. */
    private class FakeEntitlementSource(private val entitled: Boolean) : EntitlementSource {
        override suspend fun isCurrentlyEntitled(nowMs: Long): Boolean = entitled
    }

    /** Fake digest builder that returns a deterministic minimal digest. */
    private class FakeDigestBuilder : DigestBuilder {
        override suspend fun buildForCurrentPeriod(nowMs: Long): Build = Build(
            digest = DataDigest(
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
            ),
            rehydrationMap = emptyMap(),
        )
    }

    /** Fake digest builder that throws — verifies the repository doesn't crash. */
    private class ThrowingDigestBuilder : DigestBuilder {
        override suspend fun buildForCurrentPeriod(nowMs: Long): Build =
            throw IllegalStateException("DAO offline")
    }
}
