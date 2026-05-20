package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [QuarterlyReviewGenerator].
 */
class QuarterlyReviewGeneratorTest {

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
    fun `basic generation with sample data`() {
        val current = listOf(
            row(1, "Food", 15000.0),
            row(2, "Transport", 9000.0),
            row(3, "Entertainment", 3000.0)
        )
        val previous = listOf(
            row(1, "Food", 12000.0),
            row(2, "Transport", 8000.0)
        )
        val monthlyTotals = listOf(
            "January 2026" to 8000.0,
            "February 2026" to 9000.0,
            "March 2026" to 10000.0
        )

        val snap = QuarterlyReviewGenerator.generate(
            currentQuarterCategories = current,
            previousQuarterCategories = previous,
            quarterNumber = 1,
            year = 2026,
            monthlyIncome = 50000.0,
            monthlyTotals = monthlyTotals
        )

        assertEquals("Q1 2026", snap.periodLabel)
        assertEquals(27000.0, snap.periodTotal, 0.001)
        assertEquals(20000.0, snap.prevQuarterTotal, 0.001)
        assertEquals(7000.0, snap.delta, 0.001)
        assertNotNull(snap.deltaPercent)
        assertEquals(35.0, snap.deltaPercent!!, 0.001)
        assertTrue(snap.topCategories.isNotEmpty())
        assertEquals("Food", snap.topCategories.first().categoryName)
    }

    @Test
    fun `empty data handling`() {
        val snap = QuarterlyReviewGenerator.generate(
            currentQuarterCategories = emptyList(),
            previousQuarterCategories = emptyList(),
            quarterNumber = 2,
            year = 2026,
            monthlyIncome = null,
            monthlyTotals = emptyList()
        )

        assertEquals("Q2 2026", snap.periodLabel)
        assertEquals(0.0, snap.periodTotal, 0.001)
        assertEquals(0.0, snap.prevQuarterTotal, 0.001)
        assertEquals(0.0, snap.delta, 0.001)
        assertNull(snap.deltaPercent)
        assertTrue(snap.topCategories.isEmpty())
        assertNull(snap.biggestMover)
        assertNull(snap.savingsMomentum)
        // With zero spending and no income, investment illustration has principal=0
        assertNotNull(snap.investmentIllustration)
        assertEquals(0.0, snap.investmentIllustration!!.principalAmount, 0.001)
    }

    @Test
    fun `quarter boundary detection`() {
        assertEquals(1, QuarterlyReviewGenerator.quarterForMonth(1))
        assertEquals(1, QuarterlyReviewGenerator.quarterForMonth(2))
        assertEquals(1, QuarterlyReviewGenerator.quarterForMonth(3))
        assertEquals(2, QuarterlyReviewGenerator.quarterForMonth(4))
        assertEquals(2, QuarterlyReviewGenerator.quarterForMonth(5))
        assertEquals(2, QuarterlyReviewGenerator.quarterForMonth(6))
        assertEquals(3, QuarterlyReviewGenerator.quarterForMonth(7))
        assertEquals(3, QuarterlyReviewGenerator.quarterForMonth(8))
        assertEquals(3, QuarterlyReviewGenerator.quarterForMonth(9))
        assertEquals(4, QuarterlyReviewGenerator.quarterForMonth(10))
        assertEquals(4, QuarterlyReviewGenerator.quarterForMonth(11))
        assertEquals(4, QuarterlyReviewGenerator.quarterForMonth(12))

        assertEquals(1, QuarterlyReviewGenerator.firstMonthOfQuarter(1))
        assertEquals(4, QuarterlyReviewGenerator.firstMonthOfQuarter(2))
        assertEquals(7, QuarterlyReviewGenerator.firstMonthOfQuarter(3))
        assertEquals(10, QuarterlyReviewGenerator.firstMonthOfQuarter(4))
    }

    @Test
    fun `investment illustration calculation`() {
        val current = listOf(row(1, "Food", 30000.0))
        val monthlyTotals = listOf(
            "January 2026" to 10000.0,
            "February 2026" to 10000.0,
            "March 2026" to 10000.0
        )

        val snap = QuarterlyReviewGenerator.generate(
            currentQuarterCategories = current,
            previousQuarterCategories = emptyList(),
            quarterNumber = 1,
            year = 2026,
            monthlyIncome = 50000.0,
            monthlyTotals = monthlyTotals
        )

        assertNotNull(snap.investmentIllustration)
        val illust = snap.investmentIllustration!!

        // Quarterly income = 50000*3 = 150000, totalSpent = 30000, headroom = 120000
        assertEquals(InvestmentSource.HEADROOM, illust.source)
        assertEquals(120000.0, illust.principalAmount, 0.001)
        assertEquals(0.10, illust.annualRate, 0.001)
        assertEquals(60, illust.horizonMonths)
        assertEquals(12, illust.compoundingPeriodsPerYear)

        // FV = 120000 * (1 + 0.10/12)^60
        val expectedFV = 120000.0 * Math.pow(1.0 + 0.10 / 12.0, 60.0)
        assertEquals(expectedFV, illust.futureValue, 0.01)
        assertTrue(illust.disclaimer.contains("Illustration"))
    }

    @Test
    fun `fees extraction category 606`() {
        val current = listOf(
            row(1, "Food", 15000.0),
            row(QuarterlyReviewGenerator.MPESA_TRANSACTION_COST_CATEGORY_ID, "Transaction Cost", 500.0)
        )

        val snap = QuarterlyReviewGenerator.generate(
            currentQuarterCategories = current,
            previousQuarterCategories = emptyList(),
            quarterNumber = 3,
            year = 2026,
            monthlyIncome = null,
            monthlyTotals = emptyList()
        )

        assertEquals(500.0, snap.totalFees, 0.001)
        // Fees should not be in top categories
        assertTrue(snap.topCategories.none { it.categoryName == "Transaction Cost" })
    }

    @Test
    fun `biggest mover detection`() {
        val current = listOf(
            row(1, "Food", 15000.0),
            row(2, "Transport", 8000.0)
        )
        val previous = listOf(
            row(1, "Food", 14000.0),
            row(2, "Transport", 3000.0)
        )

        val snap = QuarterlyReviewGenerator.generate(
            currentQuarterCategories = current,
            previousQuarterCategories = previous,
            quarterNumber = 4,
            year = 2025,
            monthlyIncome = null,
            monthlyTotals = emptyList()
        )

        assertNotNull(snap.biggestMover)
        assertEquals("Transport", snap.biggestMover!!.categoryName)
        assertEquals(8000.0, snap.biggestMover!!.currentAmount, 0.001)
        assertEquals(3000.0, snap.biggestMover!!.previousAmount, 0.001)
    }

    @Test
    fun `savings momentum with income`() {
        val current = listOf(row(1, "Food", 30000.0))
        val monthlyTotals = listOf(
            "Jan 2026" to 8000.0,
            "Feb 2026" to 10000.0,
            "Mar 2026" to 12000.0
        )

        val snap = QuarterlyReviewGenerator.generate(
            currentQuarterCategories = current,
            previousQuarterCategories = emptyList(),
            quarterNumber = 1,
            year = 2026,
            monthlyIncome = 50000.0,
            monthlyTotals = monthlyTotals
        )

        assertNotNull(snap.savingsMomentum)
        assertEquals(3, snap.savingsMomentum!!.headroomPerMonth.size)
        assertEquals(42000.0, snap.savingsMomentum!!.headroomPerMonth[0].headroom, 0.001)
        assertEquals(40000.0, snap.savingsMomentum!!.headroomPerMonth[1].headroom, 0.001)
        assertEquals(38000.0, snap.savingsMomentum!!.headroomPerMonth[2].headroom, 0.001)
    }

    @Test
    fun `investment illustration nudge without income`() {
        val current = listOf(row(1, "Food", 10000.0))

        val snap = QuarterlyReviewGenerator.generate(
            currentQuarterCategories = current,
            previousQuarterCategories = emptyList(),
            quarterNumber = 2,
            year = 2026,
            monthlyIncome = null,
            monthlyTotals = emptyList()
        )

        assertNotNull(snap.investmentIllustration)
        val illust = snap.investmentIllustration!!
        assertEquals(InvestmentSource.NUDGE_TARGET, illust.source)
        // 20% of totalSpent (10000) = 2000
        assertEquals(2000.0, illust.principalAmount, 0.001)
    }
}
