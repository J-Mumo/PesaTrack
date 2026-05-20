package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [MonthlyReviewGenerator].
 */
class MonthlyReviewGeneratorTest {

    private val monthStart = LocalDate.of(2026, 5, 1)
    private val currentDate = LocalDate.of(2026, 5, 18) // mid-month

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
    fun `correct total calculation`() {
        val current = listOf(row(1, "Food", 5000.0), row(2, "Transport", 3000.0))
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        assertEquals(8000.0, snap.totalSpent, 0.001)
    }

    @Test
    fun `delta and percent calculation`() {
        val current = listOf(row(1, "Food", 6000.0))
        val previous = listOf(row(1, "Food", 4000.0))
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = previous,
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        assertEquals(2000.0, snap.deltaAmount, 0.001)
        assertNotNull(snap.deltaPercent)
        assertEquals(50.0, snap.deltaPercent!!, 0.001)
    }

    @Test
    fun `delta percent is null when previous month is zero`() {
        val current = listOf(row(1, "Food", 5000.0))
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        assertNull(snap.deltaPercent)
    }

    @Test
    fun `top 5 plus Others rollup`() {
        val current = (1..8).map { i -> row(i.toLong(), "Cat $i", (100 * i).toDouble()) }
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        // Top 5 + 1 Others entry = 6
        assertEquals(6, snap.topCategories.size)
        assertTrue(snap.topCategories.last().categoryName.startsWith("Others"))
        // Others = 100 + 200 + 300 = 600
        assertEquals(600.0, snap.topCategories.last().amount, 0.001)
    }

    @Test
    fun `biggest change category detection`() {
        val current = listOf(row(1, "Food", 5000.0), row(2, "Transport", 2000.0))
        val previous = listOf(row(1, "Food", 4800.0), row(2, "Transport", 500.0))
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = previous,
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        assertNotNull(snap.biggestChangeCategory)
        assertEquals("Transport", snap.biggestChangeCategory!!.categoryName)
    }

    @Test
    fun `fees extraction category 606`() {
        val current = listOf(
            row(1, "Food", 5000.0),
            row(MonthlyReviewGenerator.MPESA_TRANSACTION_COST_CATEGORY_ID, "Transaction Cost", 200.0)
        )
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        assertEquals(200.0, snap.feesPaid, 0.001)
        // Fees should not be in top categories
        assertTrue(snap.topCategories.none { it.categoryName == "Transaction Cost" })
    }

    @Test
    fun `headroom with income`() {
        val current = listOf(row(1, "Food", 5000.0))
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = 50000.0,
            currentDate = currentDate
        )
        assertNotNull(snap.headroom)
        assertEquals(45000.0, snap.headroom!!, 0.001)
    }

    @Test
    fun `headroom without income`() {
        val current = listOf(row(1, "Food", 5000.0))
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        assertNull(snap.headroom)
    }

    @Test
    fun `pace calculation`() {
        val current = listOf(row(1, "Food", 9000.0))
        // 18 days elapsed, 31 days in May
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        val expectedAvg = 9000.0 / 18.0
        val expectedPace = expectedAvg * 31.0
        assertEquals(expectedPace, snap.pace, 0.001)
    }

    @Test
    fun `investment illustration FV calculation`() {
        val current = listOf(row(1, "Food", 10000.0))
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        // NUDGE_TARGET: P = 10000 * 0.20 = 2000, r = 0.10, n = 12, t = 5
        val expectedPrincipal = 10000.0 * 0.20
        val expectedFV = expectedPrincipal * Math.pow(1.0 + 0.10 / 12.0, 60.0)
        assertEquals(InvestmentSource.NUDGE_TARGET, snap.investmentIllustration.source)
        assertEquals(expectedPrincipal, snap.investmentIllustration.principalAmount, 0.01)
        assertEquals(expectedFV, snap.investmentIllustration.futureValue, 0.01)
        assertEquals(0.10, snap.investmentIllustration.annualRate, 0.001)
        assertEquals(60, snap.investmentIllustration.horizonMonths)
    }

    @Test
    fun `empty expenses produce zero snapshot`() {
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = emptyList(),
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        assertEquals(0.0, snap.totalSpent, 0.001)
        assertTrue(snap.topCategories.isEmpty())
        assertNull(snap.biggestChangeCategory)
    }

    @Test
    fun `single category no Others`() {
        val current = listOf(row(1, "Food", 5000.0))
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = current,
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        assertEquals(1, snap.topCategories.size)
        assertEquals("Food", snap.topCategories.first().categoryName)
        assertEquals(100.0, snap.topCategories.first().percent, 0.001)
    }

    @Test
    fun `month name is correctly formatted`() {
        val snap = MonthlyReviewGenerator.generate(
            currentMonthCategories = listOf(row(1, "Food", 100.0)),
            previousMonthCategories = emptyList(),
            monthStart = monthStart,
            monthlyIncome = null,
            currentDate = currentDate
        )
        assertTrue(snap.monthName.contains("2026"))
        assertTrue(snap.monthName.contains("May") || snap.monthName.contains("mai"))
    }
}
