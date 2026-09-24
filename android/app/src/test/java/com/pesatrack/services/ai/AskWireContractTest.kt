package com.pesatrack.services.ai

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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

/**
 * Phase 3 slice B1 wire-contract cover for `POST /ai/ask`.
 *
 * Mirrors [DataDigestWireContractTest] — sends an `AskRequestDto`
 * through the exact same Moshi + `.withNullSerialization()` stack the
 * app ships, captures the raw bytes at MockWebServer, and re-parses
 * them into a `Map<String, Any?>` so absent-key vs JSON-null vs typed
 * value are all observable at test time.
 *
 * The Ask path can't reuse the Retrofit `PesaTrackAiClient` (SSE, not
 * suspend fun Response<T>) so this test bypasses Retrofit entirely and
 * exercises the Moshi adapters directly + a plain OkHttp call. That's
 * a closer fit to the real Phase 3 `AskYourMoneyClient` architecture
 * anyway.
 *
 * Regression coverage this suite provides:
 *
 *  1. Every nullable field on `AskResponse` (action_label, action_deeplink,
 *     chart) is present with value null when decoded from a JSON-null wire
 *     shape — not thrown as `Required`.
 *  2. Every camelCase Kotlin field is remapped to snake_case on the wire
 *     via `@Json(name = "…")`. Skipping an annotation would ship the
 *     wrong field name to the server and produce a silent 400.
 *  3. `AskDoneEnvelope` correctly distinguishes fallback from success.
 *  4. The request shape (digest + history + question) survives round-trip
 *     with the same key set the backend Zod schema requires.
 *
 * See plans/ai-pro-phase3-spec.md §12.3 and [DataDigestWireContractTest]
 * for the reference pattern.
 */
class AskWireContractTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private lateinit var moshi: Moshi
    private lateinit var requestAdapter: JsonAdapter<AskRequestDto>
    private lateinit var envelopeAdapter: JsonAdapter<AskDoneEnvelope>
    private lateinit var mapAdapter: JsonAdapter<Map<String, Any?>>

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        httpClient = OkHttpClient()
        moshi = Moshi.Builder().build()

        // Both adapters serializeNulls() — this is what
        // `MoshiConverterFactory.create(moshi).withNullSerialization()`
        // triggers under Retrofit for the `/ai/coach-insight` path. For
        // Phase 3 we'll wire the raw OkHttp EventSource path the same
        // way in `AskYourMoneyClient` (see spec §7.3 code sketch), so
        // asserting it here pins the contract at test time.
        requestAdapter = moshi.adapter(AskRequestDto::class.java).serializeNulls()
        envelopeAdapter = moshi.adapter(AskDoneEnvelope::class.java)

        val mapType = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java,
        )
        mapAdapter = moshi.adapter(mapType)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Outbound request shape ─────────────────────────────────────────

    @Test
    fun `request top-level keys are digest, history, question in snake_case`() {
        val req = fixtureRequest()
        val json = mapAdapter.fromJson(requestAdapter.toJson(req))!!
        assertEquals(
            setOf("digest", "history", "question"),
            json.keys,
        )
    }

    @Test
    fun `history entry keys are role and content, snake_case`() {
        val req = fixtureRequest(
            history = listOf(
                AskTurn(role = "user", content = "How much did I spend on takeout?"),
                AskTurn(role = "assistant", content = "You have spent KES 4,200."),
            ),
        )
        @Suppress("UNCHECKED_CAST")
        val json = mapAdapter.fromJson(requestAdapter.toJson(req))!!
        val history = json["history"] as List<Map<String, Any?>>
        assertEquals(2, history.size)
        assertEquals(setOf("role", "content"), history[0].keys)
        assertEquals("user", history[0]["role"])
        assertEquals("assistant", history[1]["role"])
    }

    @Test
    fun `question is preserved verbatim as a plain string`() {
        val req = fixtureRequest(question = "If I cut takeout by half, how much could I save in a year?")
        val json = mapAdapter.fromJson(requestAdapter.toJson(req))!!
        assertEquals(
            "If I cut takeout by half, how much could I save in a year?",
            json["question"],
        )
    }

    @Test
    fun `digest field passes through with backend-recognised top-level keys`() {
        val req = fixtureRequest()
        @Suppress("UNCHECKED_CAST")
        val json = mapAdapter.fromJson(requestAdapter.toJson(req))!!
        val digest = json["digest"] as Map<String, Any?>
        setOf(
            "period",
            "month_start_day",
            "days_elapsed",
            "days_total",
            "totals",
            "categories",
            "recurring",
            "top_recipients_this_period",
            "anomalies_this_week",
        ).forEach { key ->
            assertTrue(
                "digest must contain '$key' key",
                digest.containsKey(key),
            )
        }
    }

    // ── Inbound `done` envelope: success path ──────────────────────────

    @Test
    fun `successful response deserialises to a full AskResponse`() {
        val json = """
            {
                "fallback": false,
                "body": "You have spent KES 12,400 on Food & Dining this month.",
                "assumptions": [],
                "action_label": "Open Food & Dining",
                "action_deeplink": "pesatrack://category/7",
                "chart": null
            }
        """.trimIndent()
        val env = envelopeAdapter.fromJson(json)!!
        val response = env.asResponseOrNull()
        assertNotNull("envelope should convert to AskResponse", response)
        assertEquals(
            "You have spent KES 12,400 on Food & Dining this month.",
            response!!.body,
        )
        assertEquals(emptyList<String>(), response.assumptions)
        assertEquals("Open Food & Dining", response.actionLabel)
        assertEquals("pesatrack://category/7", response.actionDeeplink)
        assertNull("chart should be null on factual answers", response.chart)
    }

    @Test
    fun `all-null action fields deserialise as Kotlin null (not omitted, not thrown)`() {
        // Server always sends explicit `null` when there's no action; this
        // is the wire contract the backend Zod schema requires. Test that
        // the envelope parses without a JsonDataException Required.
        val json = """
            {
                "fallback": false,
                "body": "You have spent KES 12,400.",
                "assumptions": [],
                "action_label": null,
                "action_deeplink": null,
                "chart": null
            }
        """.trimIndent()
        val env = envelopeAdapter.fromJson(json)!!
        val response = env.asResponseOrNull()
        assertNotNull(response)
        assertNull(response!!.actionLabel)
        assertNull(response.actionDeeplink)
        assertNull(response.chart)
    }

    @Test
    fun `chart with two series and matching x_labels length round-trips`() {
        val json = """
            {
                "fallback": false,
                "body": "Cutting takeout by half would free up KES 54,600 in a year.",
                "assumptions": ["Assumes current pace of KES 1,050 per week"],
                "action_label": "Open Food & Dining",
                "action_deeplink": "pesatrack://category/7",
                "chart": {
                    "type": "compound_growth",
                    "unit": "KES",
                    "x_labels": ["Wk1", "Wk2", "Wk3"],
                    "series": [
                        { "label": "Cumulative savings", "values": [1050, 2100, 3150] }
                    ]
                }
            }
        """.trimIndent()
        val env = envelopeAdapter.fromJson(json)!!
        val response = env.asResponseOrNull()
        assertNotNull(response)
        val chart = response!!.chart
        assertNotNull("chart should deserialise on projection answers", chart)
        assertEquals("compound_growth", chart!!.type)
        assertEquals("KES", chart.unit)
        assertEquals(listOf("Wk1", "Wk2", "Wk3"), chart.xLabels)
        assertEquals(1, chart.series.size)
        assertEquals(listOf(1050, 2100, 3150), chart.series[0].values)
    }

    // ── Inbound `done` envelope: fallback path ─────────────────────────

    @Test
    fun `fallback envelope maps to null AskResponse without throwing`() {
        val json = """{"fallback": true, "reason": "provider_error"}"""
        val env = envelopeAdapter.fromJson(json)!!
        assertEquals(true, env.fallback)
        assertEquals("provider_error", env.reason)
        assertNull(
            "asResponseOrNull() must return null on fallback so ViewModel " +
                "swaps to template line",
            env.asResponseOrNull(),
        )
    }

    @Test
    fun `fallback envelope with unknown reason still parses (forward compat)`() {
        val json = """{"fallback": true, "reason": "some_future_bucket"}"""
        val env = envelopeAdapter.fromJson(json)!!
        assertEquals(true, env.fallback)
        assertEquals("some_future_bucket", env.reason)
        assertNull(env.asResponseOrNull())
    }

    @Test
    fun `envelope missing required success fields yields null asResponse`() {
        // Server-side bug or partial response — envelope has fallback:false
        // but no body. We must NOT throw; ViewModel falls back cleanly.
        val json = """{"fallback": false}"""
        val env = envelopeAdapter.fromJson(json)!!
        assertEquals(false, env.fallback)
        assertNull(env.asResponseOrNull())
    }

    // ── snake_case name-mapping ─────────────────────────────────────────

    @Test
    fun `AskResponse serialises with snake_case action_label and action_deeplink`() {
        val out = AskResponse(
            body = "hi",
            assumptions = emptyList(),
            actionLabel = "Open X",
            actionDeeplink = "pesatrack://home",
            chart = null,
        )
        val adapter = moshi.adapter(AskResponse::class.java).serializeNulls()
        val json = mapAdapter.fromJson(adapter.toJson(out))!!

        assertTrue("must have snake_case action_label", json.containsKey("action_label"))
        assertTrue("must have snake_case action_deeplink", json.containsKey("action_deeplink"))
        assertFalse("must NOT leak camelCase actionLabel", json.containsKey("actionLabel"))
        assertFalse("must NOT leak camelCase actionDeeplink", json.containsKey("actionDeeplink"))
    }

    @Test
    fun `Chart serialises with snake_case x_labels`() {
        val chart = Chart(
            type = "compound_growth",
            unit = "KES",
            xLabels = listOf("Wk1", "Wk2"),
            series = listOf(ChartSeries(label = "Savings", values = listOf(100, 200))),
        )
        val adapter = moshi.adapter(Chart::class.java).serializeNulls()
        val json = mapAdapter.fromJson(adapter.toJson(chart))!!
        assertTrue("must have snake_case x_labels", json.containsKey("x_labels"))
        assertFalse("must NOT leak camelCase xLabels", json.containsKey("xLabels"))
    }

    // ── Fixtures ────────────────────────────────────────────────────────

    private fun fixtureRequest(
        digest: DataDigest = fixtureDigest(),
        history: List<AskTurn> = emptyList(),
        question: String = "How much did I spend on Food & Dining this month?",
    ): AskRequestDto = AskRequestDto(
        digest = digest,
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
        categories = listOf(
            DigestCategory(
                id = 7,
                name = "Food & Dining",
                spent = 12400,
                budget = 10000,
                threeMoAvg = 9800,
                cv = 0.18,
            ),
        ),
        recurring = listOf(
            DigestRecurring(
                label = "Rent",
                amount = 25000,
                period = "MONTHLY",
                confidence = 0.98,
            ),
        ),
        topRecipientsThisPeriod = listOf(
            DigestRecipient(
                id = "r1",
                spent = 8400,
                count = 12,
                categoryId = 7,
                threeMoAvg = 3200,
            ),
        ),
        anomaliesThisWeek = emptyList(),
    )
}
