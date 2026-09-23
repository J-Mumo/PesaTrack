package com.pesatrack.services.ai

import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.services.ai.DataDigestBuilder.Build
import com.pesatrack.services.ai.DataDigestBuilder.DigestInputs
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Tests for [CoachInsightRepository] — the orchestration layer that
 * decides whether the Home screen sees a fresh insight, yesterday's
 * cached fallback, or `null` (⇒ render the template card).
 *
 * The whole point of the interfaces we introduced in B3 —
 * [EntitlementSource], [DigestBuilder], [CoachInsightCache] — is that
 * this suite runs on the JVM with hand-built fakes rather than
 * spinning up Room, DataStore, and the full `ProEntitlementRepository`.
 * The HTTP layer is exercised through a real [OkHttpClient] pointed at
 * a [MockWebServer] so the Moshi wire-format round-trip is genuinely
 * covered — no HTTP mocks.
 */
class CoachInsightRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var client: PesaTrackAiClient

    private val fixedZone: ZoneId = ZoneId.of("Africa/Nairobi")
    // 2026-09-23 12:00:00 in Africa/Nairobi (+03:00).
    private val fixedNowMs = LocalDate.of(2026, 9, 23)
        .atStartOfDay(fixedZone)
        .plusHours(12)
        .toInstant()
        .toEpochMilli()
    private val today: LocalDate = LocalDate.of(2026, 9, 23)
    private val yesterday: LocalDate = today.minusDays(1)

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
        client = retrofit.create(PesaTrackAiClient::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Entitlement gate ────────────────────────────────────────────────

    @Test
    fun `not entitled returns null without touching cache or network`() = runBlocking {
        val cache = InMemoryCoachInsightCache()
        val repo = repository(entitled = false, cache = cache)
        val out = repo.getForToday(fixedNowMs, fixedZone)
        assertNull(out)
        // Server not touched:
        assertEquals(0, server.requestCount)
        // Cache not read (nothing was in it anyway, but no writes either):
        assertNull(cache.snapshot())
    }

    // ── Cache-hit path ─────────────────────────────────────────────────

    @Test
    fun `fresh cache hit for today short-circuits network`() = runBlocking {
        val today_insight = sampleInsight("hydrated title")
        val cache = InMemoryCoachInsightCache().apply { putForToday(today, today_insight) }
        val repo = repository(entitled = true, cache = cache)

        val out = repo.getForToday(fixedNowMs, fixedZone)

        assertEquals(today_insight, out)
        assertEquals(0, server.requestCount)
    }

    // ── Happy path ─────────────────────────────────────────────────────

    @Test
    fun `happy path calls backend, rehydrates, caches, returns hydrated`() = runBlocking {
        val backendInsight = sampleInsight(
            title = "You spent KES 8,400 at r1 this week",
            body = "That is up KES 2,600 at r1. Reducing to KES 500 per visit would free ~KES 2,000.",
            referencedRecipientIds = listOf("r1"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(coachInsightResponseJson(fallback = false, insight = backendInsight))
        )

        val cache = InMemoryCoachInsightCache()
        val repo = repository(
            entitled = true,
            cache = cache,
            digestBuilder = FixedDigestBuilder(
                build = sampleBuild(rehydrationMap = mapOf("r1" to "Java House")),
            ),
        )

        val out = repo.getForToday(fixedNowMs, fixedZone)

        assertNotNull(out)
        assertEquals("You spent KES 8,400 at Java House this week", out!!.title)
        assertTrue(out.body.contains("Java House"))
        // Cache slot updated with the rehydrated insight:
        val stored = cache.snapshot()
        assertNotNull(stored)
        assertEquals(today.toString(), stored!!.date)
        assertEquals(out, stored.insight)
    }

    // ── Server-side fallback envelope ─────────────────────────────────

    @Test
    fun `server-side fallback envelope with yesterday cached returns yesterday`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"fallback":true,"reason":"denylist"}""")
        )
        val yesterday_insight = sampleInsight("yesterday's insight")
        val cache = InMemoryCoachInsightCache().apply { putForToday(yesterday, yesterday_insight) }
        val repo = repository(entitled = true, cache = cache)

        val out = repo.getForToday(fixedNowMs, fixedZone)

        assertEquals(yesterday_insight, out)
        // Yesterday's cache entry is NOT overwritten by a today-put:
        assertEquals(yesterday.toString(), cache.snapshot()!!.date)
    }

    @Test
    fun `server-side fallback with empty cache returns null`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"fallback":true,"reason":"provider_error"}""")
        )
        val cache = InMemoryCoachInsightCache()
        val repo = repository(entitled = true, cache = cache)
        assertNull(repo.getForToday(fixedNowMs, fixedZone))
    }

    // ── Network / HTTP error paths ────────────────────────────────────

    @Test
    fun `HTTP 500 falls back to yesterday`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val yesterday_insight = sampleInsight("yesterday's insight")
        val cache = InMemoryCoachInsightCache().apply { putForToday(yesterday, yesterday_insight) }
        val repo = repository(entitled = true, cache = cache)
        assertEquals(yesterday_insight, repo.getForToday(fixedNowMs, fixedZone))
    }

    @Test
    fun `HTTP 429 rate-limited falls back to yesterday`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate_limited"))
        val yesterday_insight = sampleInsight("yesterday's insight")
        val cache = InMemoryCoachInsightCache().apply { putForToday(yesterday, yesterday_insight) }
        val repo = repository(entitled = true, cache = cache)
        assertEquals(yesterday_insight, repo.getForToday(fixedNowMs, fixedZone))
    }

    @Test
    fun `socket disconnect during body falls back to yesterday`() = runBlocking {
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        val yesterday_insight = sampleInsight("yesterday's insight")
        val cache = InMemoryCoachInsightCache().apply { putForToday(yesterday, yesterday_insight) }
        val repo = repository(entitled = true, cache = cache)
        assertEquals(yesterday_insight, repo.getForToday(fixedNowMs, fixedZone))
    }

    // ── DigestBuilder failure ─────────────────────────────────────────

    @Test
    fun `digest build failure falls back to yesterday without hitting network`() = runBlocking {
        val yesterday_insight = sampleInsight("yesterday's insight")
        val cache = InMemoryCoachInsightCache().apply { putForToday(yesterday, yesterday_insight) }
        val repo = repository(
            entitled = true,
            cache = cache,
            digestBuilder = ThrowingDigestBuilder(),
        )
        val out = repo.getForToday(fixedNowMs, fixedZone)
        assertEquals(yesterday_insight, out)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `two-day-old cache is NOT treated as yesterday fallback`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val twoDaysAgo = today.minusDays(2)
        val cache = InMemoryCoachInsightCache().apply { putForToday(twoDaysAgo, sampleInsight("stale")) }
        val repo = repository(entitled = true, cache = cache)
        assertNull(repo.getForToday(fixedNowMs, fixedZone))
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun repository(
        entitled: Boolean,
        cache: CoachInsightCache,
        digestBuilder: DigestBuilder = FixedDigestBuilder(sampleBuild(emptyMap())),
    ) = CoachInsightRepository(
        entitlement = object : EntitlementSource {
            override suspend fun isCurrentlyEntitled(nowMs: Long): Boolean = entitled
        },
        digestBuilder = digestBuilder,
        client = client,
        cache = cache,
    )

    private fun sampleInsight(
        title: String = "sample title",
        body: String = "This is a sample body that satisfies the 40-char minimum.",
        actionLabel: String? = null,
        actionDeeplink: String? = null,
        saveableAmountKes: Int? = null,
        assumptions: List<String> = emptyList(),
        referencedRecipientIds: List<String> = emptyList(),
    ) = CoachInsight(
        title = title,
        body = body,
        actionLabel = actionLabel,
        actionDeeplink = actionDeeplink,
        saveableAmountKes = saveableAmountKes,
        assumptions = assumptions,
        referencedRecipientIds = referencedRecipientIds,
    )

    private fun sampleBuild(rehydrationMap: Map<String, String>): Build =
        DataDigestBuilder.computeDigest(
            DigestInputs(
                periodLabel = "2026-09",
                monthStartDay = 1,
                daysElapsed = 23,
                daysTotal = 30,
                currentPeriodCategoryTotals = emptyList<CategoryTotal>(),
                previousPeriodCategoryTotals = emptyList(),
                historicalPeriodCategoryTotals = emptyList(),
                recipientAggregates = rehydrationMap.entries.mapIndexed { index, (key, name) ->
                    RecipientAnonymizer.RecipientAggregate(
                        key = key,
                        displayName = name,
                        spent = 1_000 + index * 100,
                        count = 1,
                        categoryId = null,
                        threeMoAvg = 0,
                    )
                },
                budgetsByCategoryId = emptyMap(),
                incomeThisPeriod = 0,
                investedThisPeriod = 0,
                recurring = emptyList(),
                anomalies = emptyList(),
            )
        )

    /**
     * Hand-serialise the coach-insight response envelope so the tests
     * don't depend on the CoachInsightResponseDto adapter for output
     * formatting — a rename regression on any @Json name would still
     * pass tests here if we serialised through the same DTO.
     */
    private fun coachInsightResponseJson(fallback: Boolean, insight: CoachInsight?): String {
        val insightJson = insight?.let {
            """{
                "title":"${it.title.escape()}",
                "body":"${it.body.escape()}",
                "action_label":${it.actionLabel.jsonStr()},
                "action_deeplink":${it.actionDeeplink.jsonStr()},
                "saveable_amount_kes":${it.saveableAmountKes ?: "null"},
                "assumptions":${it.assumptions.joinToString(prefix = "[", postfix = "]") { s -> "\"${s.escape()}\"" }},
                "referenced_recipient_ids":${it.referencedRecipientIds.joinToString(prefix = "[", postfix = "]") { s -> "\"${s.escape()}\"" }}
            }""".trimIndent()
        } ?: "null"
        return """{"fallback":$fallback,"insight":$insightJson,"reason":null,"cached":false,"request_id":"test-req"}"""
    }

    private fun String.escape(): String = replace("\\", "\\\\").replace("\"", "\\\"")
    private fun String?.jsonStr(): String = if (this == null) "null" else "\"${escape()}\""
}

// ── Fakes ──────────────────────────────────────────────────────────────

private class InMemoryCoachInsightCache : CoachInsightCache {
    private var cached: CachedCoachInsight? = null

    fun snapshot(): CachedCoachInsight? = cached

    override suspend fun getIfFreshForToday(today: LocalDate): CoachInsight? =
        cached?.takeIf { it.date == today.toString() }?.insight

    override suspend fun getYesterday(today: LocalDate): CoachInsight? =
        cached?.takeIf { it.date == today.minusDays(1).toString() }?.insight

    override suspend fun putForToday(today: LocalDate, insight: CoachInsight) {
        cached = CachedCoachInsight(date = today.toString(), insight = insight)
    }

    override suspend fun clear() {
        cached = null
    }
}

private class FixedDigestBuilder(private val build: Build) : DigestBuilder {
    override suspend fun buildForCurrentPeriod(nowMs: Long): Build = build
}

private class ThrowingDigestBuilder : DigestBuilder {
    override suspend fun buildForCurrentPeriod(nowMs: Long): Build =
        throw IllegalStateException("simulated digest build failure")
}
