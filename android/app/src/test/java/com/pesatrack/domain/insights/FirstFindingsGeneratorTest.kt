package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstFindingsGeneratorTest {

    private fun cat(id: Long?, name: String, total: Double, count: Int) =
        CategoryTotal(id, name, null, null, total, count)

    @Test
    fun `returns null when fewer than minimum transactions`() {
        val result = FirstFindingsGenerator.generate(
            listOf(cat(101, "Food", 500.0, 2)), "Last 30 days"
        )
        assertNull(result)
    }

    @Test
    fun `surfaces fees separately and excludes them from top category`() {
        val result = FirstFindingsGenerator.generate(
            listOf(
                cat(606, "Mpesa Transaction Cost", 9_000.0, 40),
                cat(301, "Transport", 3_000.0, 10),
                cat(101, "Food", 1_000.0, 5)
            ),
            "Last 30 days"
        )
        assertNotNull(result)
        result!!
        assertEquals(9_000.0, result.feesTotal, 0.001)
        assertEquals("Transport", result.topCategoryName)
        assertEquals(13_000.0, result.totalSpent, 0.001)
        assertEquals(23, result.topCategorySharePct)
        assertEquals("fees_total", result.headlineKind)
    }

    @Test
    fun `uncategorized only yields monthly_total headline`() {
        val result = FirstFindingsGenerator.generate(
            listOf(cat(null, "Uncategorized", 4_000.0, 8)), "Last 30 days"
        )!!
        assertNull(result.topCategoryName)
        assertEquals("monthly_total", result.headlineKind)
    }

    @Test
    fun `example is clearly flagged`() {
        assertTrue(FirstFindingsGenerator.example().isExample)
    }
}
