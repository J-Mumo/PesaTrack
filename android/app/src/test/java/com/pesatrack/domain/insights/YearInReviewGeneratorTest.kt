package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [YearInReviewGenerator].
 */
class YearInReviewGeneratorTest {

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
            row(1, "Food", 120000.0, 200),
            row(2, "Transport", 80000.0, 100),
            row(3, "Entertainment", 30000.0, 50)
        )
        val previous = listOf(
            row(1, "Food", 100000.0, 180),
            row(2, "Transport", 70000.0, 90)
        )

        val snap = YearInReviewGenerator.generate(
            year = 2025,
            currentYearCategories = current,
            previousYearCategories = previous
        )

        assertEquals(2025, snap.year)
        assertEquals(230000.0, snap.annualTotal, 0.001)
        assertEquals(170000.0, snap.prevYearTotal, 0.001)
        assertEquals(60000.0, snap.delta, 0.001)
        assertTrue(snap.topCategories.isNotEmpty())
        assertEquals("Food", snap.topCategories.first().categoryName)
    }

    @Test
    fun `empty data handling`() {
        val snap = YearInReviewGenerator.generate(
            year = 2025,
            currentYearCategories = emptyList(),
            previousYearCategories = emptyList(),
            monthlyData = emptyList()
        )

        assertEquals(2025, snap.year)
        assertEquals(0.0, snap.annualTotal, 0.001)
        assertEquals(0.0, snap.prevYearTotal, 0.001)
        assertTrue(snap.topCategories.isEmpty())
        assertNull(snap.biggestMover)
        assertNull(snap.savingsStory)
        assertNotNull(snap.investmentIllustration)
        assertEquals(0.0, snap.investmentIllustration!!.principalAmount, 0.001)
        assertTrue(snap.quietLeaks.isEmpty())
    }

    @Test
    fun `quiet leaks detection`() {
        val current = listOf(
            row(1, "Food", 120000.0, 200),
            row(2, "Airtime", 12000.0, 60),  // 60 txns, avg 200 → qualifies
            row(3, "Parking", 8000.0, 55),   // 55 txns, avg ~145 → qualifies
            row(4, "Entertainment", 30000.0, 20) // 20 txns → does NOT qualify
        )

        val snap = YearInReviewGenerator.generate(
            year = 2025,
            currentYearCategories = current,
            previousYearCategories = emptyList()
        )

        assertEquals(2, snap.quietLeaks.size)
        assertEquals("Airtime", snap.quietLeaks[0].categoryName)
        assertEquals(60, snap.quietLeaks[0].totalTransactions)
        assertEquals(12000.0, snap.quietLeaks[0].totalAmount, 0.001)
        assertEquals("Parking", snap.quietLeaks[1].categoryName)
    }

    @Test
    fun `savings story calculation`() {
        val monthlyData = listOf(
            YearInReviewGenerator.MonthData("January 2025", 50000.0, 40000.0),
            YearInReviewGenerator.MonthData("February 2025", 50000.0, 55000.0),
            YearInReviewGenerator.MonthData("March 2025", 50000.0, 30000.0),
            YearInReviewGenerator.MonthData("April 2025", 50000.0, 45000.0),
        )

        val snap = YearInReviewGenerator.generate(
            year = 2025,
            currentYearCategories = listOf(row(1, "Food", 170000.0)),
            previousYearCategories = emptyList(),
            monthlyData = monthlyData
        )

        assertNotNull(snap.savingsStory)
        val story = snap.savingsStory!!
        assertEquals(3, story.monthsInHeadroom) // Jan, Mar, Apr
        assertEquals("March 2025", story.bestMonth)
        assertEquals(20000.0, story.bestMonthHeadroom, 0.001)
        // Total headroom: 10000 + 20000 + 5000 = 35000
        assertEquals(35000.0, story.totalHeadroom, 0.001)
    }

    @Test
    fun `investment illustration`() {
        val monthlyData = listOf(
            YearInReviewGenerator.MonthData("January 2025", 50000.0, 40000.0),
            YearInReviewGenerator.MonthData("February 2025", 50000.0, 30000.0),
        )

        val snap = YearInReviewGenerator.generate(
            year = 2025,
            currentYearCategories = listOf(row(1, "Food", 70000.0)),
            previousYearCategories = emptyList(),
            monthlyData = monthlyData
        )

        assertNotNull(snap.investmentIllustration)
        val illust = snap.investmentIllustration!!

        // Total headroom = 10000 + 20000 = 30000
        // annualIncome = 100000, annualTotal = 70000, headroom = 30000
        assertEquals(InvestmentSource.HEADROOM, illust.source)
        assertEquals(30000.0, illust.principalAmount, 0.001)
        assertEquals(0.10, illust.annualRate, 0.001)
        assertEquals(60, illust.horizonMonths)

        val expectedFV = 30000.0 * Math.pow(1.0 + 0.10 / 12.0, 60.0)
        assertEquals(expectedFV, illust.futureValue, 0.01)
        assertTrue(illust.disclaimer.contains("Illustration"))
    }
}
