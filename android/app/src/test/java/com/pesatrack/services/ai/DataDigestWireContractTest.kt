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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Regression coverage for the code-22 outage: Moshi drops null-valued
 * keys on serialization by default, but the backend Zod schema uses
 * `.nullable()` which requires the key to be **present with value
 * `null`**. Any user with (a) a category that has no budget set, or
 * (b) a top recipient with no primary category mapping, was silently
 * 400'd forever until we found this the hard way.
 *
 * These tests pin the wire contract for every nullable field the
 * digest DTOs currently expose. If any future refactor drops
 * `withNullSerialization()` from [com.pesatrack.di.AiHttpModule], or a
 * new nullable field is added without the same wiring, the assertions
 * below will fail on the JVM before any AAB gets uploaded.
 *
 * We deliberately build the Retrofit stack **exactly** the way
 * production wires it (same converter factory, same
 * `.withNullSerialization()` toggle) and use MockWebServer to capture
 * the actual bytes on the wire — no mocking of Moshi itself, no
 * inspection of Kotlin objects. What the backend sees is what we
 * assert on.
 */
class DataDigestWireContractTest {

    private lateinit var server: MockWebServer
    private lateinit var client: PesaTrackAiClient
    private lateinit var mapAdapter: JsonAdapter<Map<String, Any?>>

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            // MUST mirror com.pesatrack.di.AiHttpModule.provideRetrofit —
            // this is the whole point of these tests.
            .addConverterFactory(MoshiConverterFactory.create(moshi).withNullSerialization())
            .build()
        client = retrofit.create(PesaTrackAiClient::class.java)

        // We deliberately re-parse the raw wire bytes back into a Map<String, Any?>
        // rather than into a DataDigest instance — the whole point is to
        // observe what's ACTUALLY on the wire, not to round-trip through
        // Moshi's own read side (which would apply defaults etc. and hide
        // exactly the class of bug we're checking for). Map serialisation
        // preserves JSON null vs absent-key distinction: absent means the
        // key is missing from the Map; JSON null means the key is present
        // with value `null` (== Java null).
        //
        // org.json.JSONObject is on the mockable android.jar in unit tests
        // and returns stub null for everything, so we don't use it here.
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

    /**
     * Sends a request through the real Retrofit + Moshi stack, returns
     * the deserialised `digest` object as a Map<String, Any?>. Absent
     * keys are missing from the Map; JSON nulls are present with value
     * `null`. That's exactly what Zod checks against.
     */
    @Suppress("UNCHECKED_CAST")
    private fun captureSerializedDigest(digest: DataDigest): Map<String, Any?> {
        // Enqueue a fully-populated response so Moshi's response
        // deserialisation doesn't throw on the way back.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "fallback": false,
                        "insight": {
                            "title": "t",
                            "body": "b",
                            "action_label": null,
                            "action_deeplink": null,
                            "saveable_amount_kes": null,
                            "assumptions": [],
                            "referenced_recipient_ids": []
                        },
                        "reason": null,
                        "cached": false,
                        "request_id": "req-1"
                    }
                    """.trimIndent(),
                ),
        )
        kotlinx.coroutines.runBlocking {
            client.coachInsight(CoachInsightRequestDto(digest = digest))
        }
        val recorded = server.takeRequest()
        val bodyStr = recorded.body.readUtf8()
        val root = mapAdapter.fromJson(bodyStr)
            ?: throw AssertionError("Empty request body: $bodyStr")
        val d = root["digest"]
            ?: throw AssertionError("Request body did not contain 'digest': $bodyStr")
        return d as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun firstOfArray(map: Map<String, Any?>, key: String): Map<String, Any?> {
        val list = map[key] as? List<Map<String, Any?>>
            ?: throw AssertionError("Missing or non-list key '$key' in $map")
        assertTrue("Array '$key' must not be empty for this test", list.isNotEmpty())
        return list[0]
    }

    // ── Nullable fields must be PRESENT with value null ───────────────
    //
    // For each nullable field on the wire schema we assert:
    //   containsKey(field) == true     ← key present
    //   map[field] == null             ← value is JSON null
    // Together these prove `"field": null` on the wire, not the
    // Moshi-default of omitting the key.

    @Test
    fun `category budget is present as null when unset`() {
        val d = captureSerializedDigest(
            fixtureDigest(categories = listOf(digestCategory(id = 7, budget = null))),
        )
        val cat = firstOfArray(d, "categories")
        assertTrue(
            "categories[0] must contain 'budget' key (Zod .nullable() requires the key to exist)",
            cat.containsKey("budget"),
        )
        assertNull("categories[0].budget must serialize as JSON null, not omitted", cat["budget"])
    }

    @Test
    fun `category id is present as null when uncategorised`() {
        val d = captureSerializedDigest(
            fixtureDigest(categories = listOf(digestCategory(id = null))),
        )
        val cat = firstOfArray(d, "categories")
        assertTrue("categories[0] must contain 'id' key", cat.containsKey("id"))
        assertNull("categories[0].id must be JSON null when uncategorised", cat["id"])
    }

    @Test
    fun `category cv is present as null when history is thin`() {
        val d = captureSerializedDigest(
            fixtureDigest(categories = listOf(digestCategory(cv = null))),
        )
        val cat = firstOfArray(d, "categories")
        assertTrue("categories[0] must contain 'cv' key", cat.containsKey("cv"))
        assertNull("categories[0].cv must be JSON null when history <3mo", cat["cv"])
    }

    @Test
    fun `recipient category_id is present as null when unmapped`() {
        val d = captureSerializedDigest(
            fixtureDigest(topRecipientsThisPeriod = listOf(digestRecipient(categoryId = null))),
        )
        val recipient = firstOfArray(d, "top_recipients_this_period")
        assertTrue(
            "top_recipients_this_period[0] must contain 'category_id' key",
            recipient.containsKey("category_id"),
        )
        assertNull(
            "top_recipients_this_period[0].category_id must be JSON null when no primary mapping exists",
            recipient["category_id"],
        )
    }

    @Test
    fun `anomaly category_id is present as null for non-category-scoped anomalies`() {
        val d = captureSerializedDigest(
            fixtureDigest(
                anomaliesThisWeek = listOf(
                    DigestAnomaly(type = "recurring_missed", categoryId = null, deltaPct = 0),
                ),
            ),
        )
        val anomaly = firstOfArray(d, "anomalies_this_week")
        assertTrue("anomalies_this_week[0] must contain 'category_id' key", anomaly.containsKey("category_id"))
        assertNull("anomalies_this_week[0].category_id must be JSON null", anomaly["category_id"])
    }

    // ── Snake_case wire names are still applied under R8-safe wiring ──
    //
    // These aren't null-drop tests but they belong in the same suite:
    // they lock in the @Json(name = "…") renames the backend depends on,
    // so a future refactor that removes an annotation gets caught here.

    @Test
    fun `digest top-level keys use snake_case as backend expects`() {
        val d = captureSerializedDigest(fixtureDigest())
        val expected = setOf(
            "period",
            "month_start_day",
            "days_elapsed",
            "days_total",
            "totals",
            "categories",
            "recurring",
            "top_recipients_this_period",
            "anomalies_this_week",
        )
        assertEquals(
            "top-level digest keys must match the backend Zod schema exactly",
            expected,
            d.keys,
        )
    }

    @Test
    fun `totals uses snake_case for spent_last_period, spent_3mo_avg, income_est, invested_this_period`() {
        val d = captureSerializedDigest(fixtureDigest())
        @Suppress("UNCHECKED_CAST")
        val totals = d["totals"] as Map<String, Any?>

        listOf(
            "spent",
            "spent_last_period",
            "spent_3mo_avg",
            "income_est",
            "invested_this_period",
        ).forEach { key ->
            assertTrue("totals must contain '$key' key on the wire", totals.containsKey(key))
        }
        // And no camelCase leakage.
        listOf("spentLastPeriod", "spent3moAvg", "incomeEst", "investedThisPeriod").forEach { key ->
            assertFalse("totals must NOT contain camelCase '$key'", totals.containsKey(key))
        }
    }

    @Test
    fun `recipient uses snake_case for category_id and 3mo_avg`() {
        val d = captureSerializedDigest(
            fixtureDigest(topRecipientsThisPeriod = listOf(digestRecipient(categoryId = 7L, threeMoAvg = 1234))),
        )
        val r = firstOfArray(d, "top_recipients_this_period")
        assertTrue(r.containsKey("category_id"))
        assertTrue(r.containsKey("3mo_avg"))
        // Moshi decodes JSON numbers into Double when the target type is
        // Any — compare via toDouble() so we're value-not-type sensitive.
        assertEquals(7.0, (r["category_id"] as Number).toDouble(), 0.0)
        assertEquals(1234.0, (r["3mo_avg"] as Number).toDouble(), 0.0)
    }

    // ── Fixtures ──────────────────────────────────────────────────────

    private fun fixtureDigest(
        categories: List<DigestCategory> = listOf(digestCategory()),
        topRecipientsThisPeriod: List<DigestRecipient> = listOf(digestRecipient()),
        anomaliesThisWeek: List<DigestAnomaly> = emptyList(),
    ): DataDigest = DataDigest(
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
        categories = categories,
        recurring = listOf(
            DigestRecurring(label = "Rent", amount = 25000, period = "MONTHLY", confidence = 0.98),
        ),
        topRecipientsThisPeriod = topRecipientsThisPeriod,
        anomaliesThisWeek = anomaliesThisWeek,
    )

    private fun digestCategory(
        id: Long? = 7L,
        name: String = "Food & Dining",
        spent: Int = 12400,
        budget: Int? = 10000,
        threeMoAvg: Int = 9800,
        cv: Double? = 0.18,
    ): DigestCategory = DigestCategory(
        id = id,
        name = name,
        spent = spent,
        budget = budget,
        threeMoAvg = threeMoAvg,
        cv = cv,
    )

    private fun digestRecipient(
        id: String = "r1",
        spent: Int = 8400,
        count: Int = 12,
        categoryId: Long? = 7L,
        threeMoAvg: Int = 3200,
    ): DigestRecipient = DigestRecipient(
        id = id,
        spent = spent,
        count = count,
        categoryId = categoryId,
        threeMoAvg = threeMoAvg,
    )
}
