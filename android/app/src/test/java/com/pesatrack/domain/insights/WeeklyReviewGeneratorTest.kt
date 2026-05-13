package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WeeklyReviewGenerator].
 *
 * The generator is pure, so these tests use plain JUnit + hard-coded
 * [CategoryTotal] inputs. They lock in the rules from
 * plans/insights-and-reports-plan.md so future refactors don't accidentally
 * drift away from the documented behavior.
 */
class WeeklyReviewGeneratorTest {

    private val periodStart = 1_000_000_000_000L
    private val periodEnd = periodStart + 7L * 24 * 60 * 60 * 1000

    private fun row(
        id: Long?,
        name: String,
        total: Double,
        count: Int = 1
    ) = CategoryTotal(
        categoryId = id,
        categoryName = name,
        categoryColor = null,
        parentId = null,
        total = total,
        transactionCount = count
    )

    @Test
    fun `happy path computes totals delta and average`() {
        val current = listOf(
            row(1, "Food", 2000.0),
            row(2, "Transport", 1000.0)
        )
        val previous = listOf(
            row(1, "Food", 1500.0),
            row(2, "Transport", 500.0)
        )

        val snap = WeeklyReviewGenerator.generate(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = periodEnd,
            currentPeriodCategories = current,
            previousPeriodCategories = previous,
            monthIncome = 0.0,
            monthSpendSoFar = 0.0,
            monthLabel = "X",
            daysRemainingInMonth = 0
        )

        assertEquals(3000.0, snap.periodTotal, 0.001)
        assertEquals(2000.0, snap.previousPeriodTotal, 0.001)
        assertEquals(1000.0, snap.periodDelta, 0.001)
        assertEquals(50.0, snap.periodDeltaPercent!!, 0.001)
        assertEquals(7, snap.periodDays)
        assertEquals(3000.0 / 7.0, snap.averagePerDay, 0.001)
        assertFalse(snap.limitedData)
    }

    @Test
    fun `fees category 606 is excluded from top 5 and surfaced separately`() {
        val current = listOf(
            row(1, "Food", 2000.0),
            row(WeeklyReviewGenerator.MPESA_TRANSACTION_COST_CATEGORY_ID, "Transaction Cost", 150.0)
        )

        val snap = WeeklyReviewGenerator.generate(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = periodEnd,
            currentPeriodCategories = current,
            previousPeriodCategories = emptyList(),
            monthIncome = 0.0,
            monthSpendSoFar = 0.0,
            monthLabel = "X",
            daysRemainingInMonth = 0
        )

        assertEquals(150.0, snap.feesTotal, 0.001)
        assertEquals(1, snap.topCategories.size)
        assertEquals("Food", snap.topCategories.first().name)
        assertTrue(snap.topCategories.none { it.categoryId == WeeklyReviewGenerator.MPESA_TRANSACTION_COST_CATEGORY_ID })
    }

    @Test
    fun `headroom is null when income is zero`() {
        val snap = WeeklyReviewGenerator.generate(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = periodEnd,
            currentPeriodCategories = listOf(row(1, "Food", 100.0)),
            previousPeriodCategories = listOf(row(1, "Food", 100.0)),
            monthIncome = 0.0,
            monthSpendSoFar = 100.0,
            monthLabel = "X",
            daysRemainingInMonth = 10
        )
        assertNull(snap.headroom)
    }

    @Test
    fun `headroom is populated when income is positive`() {
        val snap = WeeklyReviewGenerator.generate(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = periodEnd,
            currentPeriodCategories = listOf(row(1, "Food", 100.0)),
            previousPeriodCategories = listOf(row(1, "Food", 100.0)),
            monthIncome = 50_000.0,
            monthSpendSoFar = 10_000.0,
            monthLabel = "May 2026",
            daysRemainingInMonth = 12
        )
        assertNotNull(snap.headroom)
        assertEquals(50_000.0, snap.headroom!!.income, 0.001)
        assertEquals(10_000.0, snap.headroom!!.spendSoFar, 0.001)
        assertEquals("May 2026", snap.headroom!!.label)
    }

    @Test
    fun `limitedData is true when previous period total is zero`() {
        val snap = WeeklyReviewGenerator.generate(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = periodEnd,
            currentPeriodCategories = listOf(row(1, "Food", 100.0)),
            previousPeriodCategories = emptyList(),
            monthIncome = 0.0,
            monthSpendSoFar = 0.0,
            monthLabel = "X",
            daysRemainingInMonth = 0
        )
        assertTrue(snap.limitedData)
        assertNull(snap.periodDeltaPercent)
    }

    @Test
    fun `top 5 truncates with others rollup`() {
        val current = (1..8).map { i -> row(i.toLong(), "Cat $i", (100 * i).toDouble()) }

        val snap = WeeklyReviewGenerator.generate(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = periodEnd,
            currentPeriodCategories = current,
            previousPeriodCategories = emptyList(),
            monthIncome = 0.0,
            monthSpendSoFar = 0.0,
            monthLabel = "X",
            daysRemainingInMonth = 0
        )

        assertEquals(5, snap.topCategories.size)
        // Top 5 = 800, 700, 600, 500, 400
        assertEquals(800.0, snap.topCategories[0].amount, 0.001)
        assertEquals(400.0, snap.topCategories[4].amount, 0.001)
        // Others = 300 + 200 + 100 = 600, count = 3
        assertEquals(3, snap.othersCount)
        assertEquals(600.0, snap.othersAmount, 0.001)
    }

    @Test
    fun `biggest change is the category with largest absolute delta`() {
        val current = listOf(
            row(1, "Food", 2000.0),
            row(2, "Transport", 500.0)
        )
        val previous = listOf(
            row(1, "Food", 1900.0),     // +100
            row(2, "Transport", 100.0)  // +400 (largest)
        )
        val snap = WeeklyReviewGenerator.generate(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = periodEnd,
            currentPeriodCategories = current,
            previousPeriodCategories = previous,
            monthIncome = 0.0,
            monthSpendSoFar = 0.0,
            monthLabel = "X",
            daysRemainingInMonth = 0
        )
        assertNotNull(snap.biggestChange)
        assertEquals("Transport", snap.biggestChange!!.name)
        assertEquals(400.0, snap.biggestChange!!.deltaAmount, 0.001)
    }
}
