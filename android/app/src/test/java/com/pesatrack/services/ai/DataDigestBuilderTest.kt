package com.pesatrack.services.ai

import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.domain.models.AmountPattern
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.domain.models.RecurrenceCycle
import com.pesatrack.domain.models.RecurringExpense
import com.pesatrack.services.ai.DataDigestBuilder.Companion.MIN_RECURRING_CONFIDENCE
import com.pesatrack.services.ai.DataDigestBuilder.Companion.TOP_CATEGORIES
import com.pesatrack.services.ai.DataDigestBuilder.Companion.TOP_RECURRING
import com.pesatrack.services.ai.DataDigestBuilder.Companion.computeDigest
import com.pesatrack.services.ai.DataDigestBuilder.DigestInputs
import com.pesatrack.services.ai.RecipientAnonymizer.RecipientAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure shaping logic of [DataDigestBuilder.computeDigest] with
 * hand-built inputs. No DAOs, no clock, no Room. The DAO-facing wrapper
 * [DataDigestBuilder.buildForCurrentPeriod] is exercised elsewhere — the
 * point of this file is to lock the *shaping* invariants: sorting, caps,
 * rounding, budget attachment, 3-mo averages, recurring filtering, and
 * that the digest never carries recipient PII.
 */
class DataDigestBuilderTest {

    // ── Totals ──────────────────────────────────────────────────────────

    @Test
    fun `empty inputs produce zero totals and empty lists`() {
        val build = computeDigest(inputs())
        assertEquals(0, build.digest.totals.spent)
        assertEquals(0, build.digest.totals.spentLastPeriod)
        assertEquals(0, build.digest.totals.spent3moAvg)
        assertEquals(0, build.digest.totals.incomeEst)
        assertEquals(0, build.digest.totals.investedThisPeriod)
        assertTrue(build.digest.categories.isEmpty())
        assertTrue(build.digest.recurring.isEmpty())
        assertTrue(build.digest.topRecipientsThisPeriod.isEmpty())
        assertTrue(build.digest.anomaliesThisWeek.isEmpty())
        assertTrue(build.rehydrationMap.isEmpty())
    }

    @Test
    fun `current period spent is sum of current category totals`() {
        val build = computeDigest(
            inputs(
                currentPeriodCategoryTotals = listOf(
                    catTotal(1L, "A", 4_000.0),
                    catTotal(2L, "B", 800.0),
                )
            )
        )
        assertEquals(4_800, build.digest.totals.spent)
    }

    @Test
    fun `previous period spent is sum of previous category totals`() {
        val build = computeDigest(
            inputs(
                previousPeriodCategoryTotals = listOf(catTotal(1L, "A", 3_000.0))
            )
        )
        assertEquals(3_000, build.digest.totals.spentLastPeriod)
    }

    @Test
    fun `three-month average of totals is the mean of historical period sums`() {
        val build = computeDigest(
            inputs(
                historicalPeriodCategoryTotals = listOf(
                    listOf(catTotal(1L, "A", 3_000.0), catTotal(2L, "B", 1_000.0)), // 4000
                    listOf(catTotal(1L, "A", 2_000.0)),                              // 2000
                    listOf(catTotal(1L, "A", 6_000.0)),                              // 6000
                )
            )
        )
        assertEquals(4_000, build.digest.totals.spent3moAvg) // (4000 + 2000 + 6000) / 3
    }

    @Test
    fun `income and invested pass through directly`() {
        val build = computeDigest(
            inputs(
                incomeThisPeriod = 85_000,
                investedThisPeriod = 6_000,
            )
        )
        assertEquals(85_000, build.digest.totals.incomeEst)
        assertEquals(6_000, build.digest.totals.investedThisPeriod)
    }

    // ── Period metadata ────────────────────────────────────────────────

    @Test
    fun `period label and metadata pass through unchanged when valid`() {
        val build = computeDigest(
            inputs(
                periodLabel = "2026-09",
                monthStartDay = 25,
                daysElapsed = 17,
                daysTotal = 30,
            )
        )
        assertEquals("2026-09", build.digest.period)
        assertEquals(25, build.digest.monthStartDay)
        assertEquals(17, build.digest.daysElapsed)
        assertEquals(30, build.digest.daysTotal)
    }

    @Test
    fun `monthStartDay is clamped to 1-28`() {
        assertEquals(1, computeDigest(inputs(monthStartDay = 0)).digest.monthStartDay)
        assertEquals(1, computeDigest(inputs(monthStartDay = -5)).digest.monthStartDay)
        assertEquals(28, computeDigest(inputs(monthStartDay = 31)).digest.monthStartDay)
        assertEquals(28, computeDigest(inputs(monthStartDay = 99)).digest.monthStartDay)
    }

    @Test
    fun `daysElapsed and daysTotal coerced to at least 1`() {
        val build = computeDigest(inputs(daysElapsed = 0, daysTotal = 0))
        assertEquals(1, build.digest.daysElapsed)
        assertEquals(1, build.digest.daysTotal)
    }

    // ── Categories ──────────────────────────────────────────────────────

    @Test
    fun `categories are sorted by spend descending`() {
        val build = computeDigest(
            inputs(
                currentPeriodCategoryTotals = listOf(
                    catTotal(3L, "C", 500.0),
                    catTotal(1L, "A", 2_500.0),
                    catTotal(2L, "B", 900.0),
                )
            )
        )
        assertEquals(listOf("A", "B", "C"), build.digest.categories.map { it.name })
        assertEquals(listOf(2_500, 900, 500), build.digest.categories.map { it.spent })
    }

    @Test
    fun `categories are capped at TOP_CATEGORIES`() {
        val inputs = inputs(
            currentPeriodCategoryTotals = (1..15).map {
                catTotal(it.toLong(), "cat$it", it * 100.0)
            }
        )
        val build = computeDigest(inputs)
        assertEquals(TOP_CATEGORIES, build.digest.categories.size)
    }

    @Test
    fun `category budget is attached from budgets map when categoryId matches`() {
        val build = computeDigest(
            inputs(
                currentPeriodCategoryTotals = listOf(
                    catTotal(7L, "Food", 12_400.0),
                    catTotal(8L, "Transport", 3_000.0),
                ),
                budgetsByCategoryId = mapOf(7L to 10_000),
            )
        )
        val food = build.digest.categories.first { it.name == "Food" }
        val transport = build.digest.categories.first { it.name == "Transport" }
        assertEquals(10_000, food.budget)
        assertNull(transport.budget)
    }

    @Test
    fun `category budget is null when categoryId is null`() {
        val build = computeDigest(
            inputs(
                currentPeriodCategoryTotals = listOf(catTotal(null, "Uncategorized", 500.0)),
                budgetsByCategoryId = mapOf(1L to 5_000),
            )
        )
        assertNull(build.digest.categories.single().budget)
    }

    @Test
    fun `category threeMoAvg is mean of that category's history`() {
        val build = computeDigest(
            inputs(
                currentPeriodCategoryTotals = listOf(catTotal(1L, "A", 5_000.0)),
                historicalPeriodCategoryTotals = listOf(
                    listOf(catTotal(1L, "A", 3_000.0)),
                    listOf(catTotal(1L, "A", 4_000.0)),
                    listOf(catTotal(1L, "A", 5_000.0)),
                ),
            )
        )
        assertEquals(4_000, build.digest.categories.single().threeMoAvg) // mean of 3,4,5k
    }

    @Test
    fun `category cv is null with fewer than two samples`() {
        val build = computeDigest(
            inputs(
                currentPeriodCategoryTotals = listOf(catTotal(1L, "A", 5_000.0)),
                historicalPeriodCategoryTotals = listOf(
                    listOf(catTotal(1L, "A", 3_000.0)),
                ),
            )
        )
        assertNull(build.digest.categories.single().cv)
    }

    @Test
    fun `category cv is computed when at least two samples exist`() {
        val build = computeDigest(
            inputs(
                currentPeriodCategoryTotals = listOf(catTotal(1L, "A", 5_000.0)),
                historicalPeriodCategoryTotals = listOf(
                    listOf(catTotal(1L, "A", 1_000.0)),
                    listOf(catTotal(1L, "A", 3_000.0)),
                ),
            )
        )
        val cv = build.digest.categories.single().cv
        // std-dev of {1000, 3000} = 1000; mean = 2000; cv = 0.5
        assertEquals(0.5, cv!!, 0.001)
    }

    // ── Recurring ──────────────────────────────────────────────────────

    @Test
    fun `recurring below confidence floor is dropped`() {
        val below = recurring(label = "Weak", confidence = MIN_RECURRING_CONFIDENCE - 0.1)
        val above = recurring(label = "Strong", confidence = MIN_RECURRING_CONFIDENCE + 0.1)
        val build = computeDigest(inputs(recurring = listOf(below, above)))
        assertEquals(listOf("Strong"), build.digest.recurring.map { it.label })
    }

    @Test
    fun `recurring is capped at TOP_RECURRING and sorted by monthly-equivalent desc`() {
        val many = (1..TOP_RECURRING + 3).map { i ->
            recurring(
                label = "rec$i",
                averageAmount = (i * 1_000).toDouble(),
                cycle = RecurrenceCycle.MONTHLY,
                confidence = 0.9,
            )
        }
        val build = computeDigest(inputs(recurring = many))
        assertEquals(TOP_RECURRING, build.digest.recurring.size)
        // largest amount first
        assertEquals("rec${TOP_RECURRING + 3}", build.digest.recurring.first().label)
    }

    @Test
    fun `recurring cycle is emitted as canonical enum name`() {
        val build = computeDigest(
            inputs(
                recurring = listOf(
                    recurring(label = "R", cycle = RecurrenceCycle.YEARLY, confidence = 0.9),
                )
            )
        )
        assertEquals("YEARLY", build.digest.recurring.single().period)
    }

    // ── Recipients ─────────────────────────────────────────────────────

    @Test
    fun `recipients are anonymised and appear in the digest with rN ids`() {
        val build = computeDigest(
            inputs(
                recipientAggregates = listOf(
                    aggregate("keyA", "Java House", spent = 8_400, count = 12, categoryId = 7L),
                    aggregate("keyB", "Naivas", spent = 4_200, count = 8, categoryId = 4L),
                )
            )
        )
        val recipients = build.digest.topRecipientsThisPeriod
        assertEquals(listOf("r1", "r2"), recipients.map { it.id })
        assertEquals("Java House", build.rehydrationMap["r1"])
        assertEquals("Naivas", build.rehydrationMap["r2"])
    }

    @Test
    fun `digest string does not carry recipient names or keys`() {
        val build = computeDigest(
            inputs(
                recipientAggregates = listOf(
                    aggregate(
                        key = "0712345678",
                        displayName = "Very Distinctive Merchant",
                        spent = 500,
                    ),
                )
            )
        )
        val serialized = build.digest.toString()
        assertTrue(
            "digest.toString must not carry the recipient key: $serialized",
            !serialized.contains("0712345678"),
        )
        assertTrue(
            "digest.toString must not carry the display name: $serialized",
            !serialized.contains("Very Distinctive"),
        )
    }

    // ── Anomalies ──────────────────────────────────────────────────────

    @Test
    fun `anomalies pass through unchanged`() {
        val anomaly = DigestAnomaly(type = "category_spike", categoryId = 16L, deltaPct = 68)
        val build = computeDigest(inputs(anomalies = listOf(anomaly)))
        assertEquals(listOf(anomaly), build.digest.anomaliesThisWeek)
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun inputs(
        periodLabel: String = "2026-09",
        monthStartDay: Int = 1,
        daysElapsed: Int = 15,
        daysTotal: Int = 30,
        currentPeriodCategoryTotals: List<CategoryTotal> = emptyList(),
        previousPeriodCategoryTotals: List<CategoryTotal> = emptyList(),
        historicalPeriodCategoryTotals: List<List<CategoryTotal>> = emptyList(),
        recipientAggregates: List<RecipientAggregate> = emptyList(),
        budgetsByCategoryId: Map<Long, Int> = emptyMap(),
        incomeThisPeriod: Int = 0,
        investedThisPeriod: Int = 0,
        recurring: List<RecurringExpense> = emptyList(),
        anomalies: List<DigestAnomaly> = emptyList(),
    ) = DigestInputs(
        periodLabel = periodLabel,
        monthStartDay = monthStartDay,
        daysElapsed = daysElapsed,
        daysTotal = daysTotal,
        currentPeriodCategoryTotals = currentPeriodCategoryTotals,
        previousPeriodCategoryTotals = previousPeriodCategoryTotals,
        historicalPeriodCategoryTotals = historicalPeriodCategoryTotals,
        recipientAggregates = recipientAggregates,
        budgetsByCategoryId = budgetsByCategoryId,
        incomeThisPeriod = incomeThisPeriod,
        investedThisPeriod = investedThisPeriod,
        recurring = recurring,
        anomalies = anomalies,
    )

    private fun catTotal(id: Long?, name: String, total: Double) = CategoryTotal(
        categoryId = id,
        categoryName = name,
        categoryColor = null,
        parentId = null,
        total = total,
        transactionCount = 1,
    )

    private fun aggregate(
        key: String,
        displayName: String,
        spent: Int,
        count: Int = 1,
        categoryId: Long? = null,
        threeMoAvg: Int = 0,
    ) = RecipientAggregate(
        key = key,
        displayName = displayName,
        spent = spent,
        count = count,
        categoryId = categoryId,
        threeMoAvg = threeMoAvg,
    )

    private fun recurring(
        label: String,
        averageAmount: Double = 1_000.0,
        cycle: RecurrenceCycle = RecurrenceCycle.MONTHLY,
        confidence: Double = 0.9,
    ) = RecurringExpense(
        recipientKey = label,
        recipientDisplayName = label,
        categoryId = null,
        categoryName = null,
        cycle = cycle,
        averageAmount = averageAmount,
        lastAmount = averageAmount,
        amountPattern = AmountPattern.FIXED,
        confidence = confidence,
        occurrenceCount = 3,
        lastOccurrence = 0L,
        nextExpected = 0L,
        expectedDayOfMonth = null,
        paymentType = PaymentType.PAY_BILL,
        isOverdue = false,
    )
}
