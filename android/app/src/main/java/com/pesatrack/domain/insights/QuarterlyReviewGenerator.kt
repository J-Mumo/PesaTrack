package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow

/**
 * Pure-function builder for [QuarterlyReviewSnapshot].
 *
 * Takes raw aggregates already produced by the repository / DAO layer and
 * applies plan-aligned shaping for the quarterly cadence.
 */
object QuarterlyReviewGenerator {

    const val MPESA_TRANSACTION_COST_CATEGORY_ID: Long = 606L
    private const val MAX_TOP_CATEGORIES = 5
    private const val INVESTMENT_ANNUAL_RATE = 0.10
    private const val INVESTMENT_COMPOUNDING_PERIODS = 12
    private const val INVESTMENT_HORIZON_MONTHS = 12
    private const val INVESTMENT_DISCLAIMER = "Illustration only — assumes 10% APY, monthly compounding. Not a recommendation."

    /**
     * Determine the quarter number (1-4) for a given month (1-12).
     */
    fun quarterForMonth(month: Int): Int = when (month) {
        in 1..3 -> 1
        in 4..6 -> 2
        in 7..9 -> 3
        else -> 4
    }

    /**
     * Get the label for a quarter, e.g. "Q1 2026".
     */
    fun quarterLabel(quarter: Int, year: Int): String = "Q$quarter $year"

    /**
     * First month (1-based) of a quarter.
     */
    fun firstMonthOfQuarter(quarter: Int): Int = (quarter - 1) * 3 + 1

    /**
     * Build a quarterly review snapshot.
     *
     * @param currentQuarterCategories category breakdown for the reviewed quarter.
     * @param previousQuarterCategories category breakdown for the previous quarter.
     * @param quarterNumber 1-4 for the reviewed quarter.
     * @param year year of the reviewed quarter.
     * @param monthlyIncome user's monthly income, or null if not set.
     * @param monthlyTotals list of (monthLabel, totalSpend) for each month of the quarter (up to 3).
     */
    fun generate(
        currentQuarterCategories: List<CategoryTotal>,
        previousQuarterCategories: List<CategoryTotal>,
        quarterNumber: Int,
        year: Int,
        monthlyIncome: Double?,
        monthlyTotals: List<Pair<String, Double>> = emptyList()
    ): QuarterlyReviewSnapshot {
        val periodLabel = quarterLabel(quarterNumber, year)

        // ── Totals ──
        val periodTotal = currentQuarterCategories.sumOf { it.total }
        val prevQuarterTotal = previousQuarterCategories.sumOf { it.total }
        val delta = periodTotal - prevQuarterTotal
        val deltaPercent = if (prevQuarterTotal > 0.0) {
            ((periodTotal - prevQuarterTotal) / prevQuarterTotal) * 100.0
        } else null

        // ── Fees (cat 606) ──
        val totalFees = currentQuarterCategories
            .firstOrNull { it.categoryId == MPESA_TRANSACTION_COST_CATEGORY_ID }
            ?.total ?: 0.0

        // ── Top 5 categories (exclude fees) ──
        val rankable = currentQuarterCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .filter { it.total > 0.0 }
            .sortedByDescending { it.total }

        val totalExcludingFees = rankable.sumOf { it.total }
        val top5 = rankable.take(MAX_TOP_CATEGORIES).map { row ->
            CategoryBreakdown(
                categoryName = row.categoryName,
                categoryId = (row.categoryId ?: 0L).toInt(),
                amount = row.total,
                percent = if (totalExcludingFees > 0.0) (row.total / totalExcludingFees) * 100.0 else 0.0
            )
        }

        // ── Biggest mover ──
        val previousByCategoryId = previousQuarterCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .associateBy { it.categoryId }
        val currentByCategoryId = currentQuarterCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .associateBy { it.categoryId }
        val allIds = previousByCategoryId.keys + currentByCategoryId.keys

        val biggestMover = allIds
            .mapNotNull { id ->
                val current = currentByCategoryId[id]
                val previous = previousByCategoryId[id]
                val name = current?.categoryName ?: previous?.categoryName ?: return@mapNotNull null
                val curAmt = current?.total ?: 0.0
                val prevAmt = previous?.total ?: 0.0
                val absDelta = abs(curAmt - prevAmt)
                if (absDelta <= 0.0) return@mapNotNull null
                val changePct = if (prevAmt > 0.0) ((curAmt - prevAmt) / prevAmt) * 100.0 else 100.0
                BiggestMover(
                    categoryName = name,
                    currentAmount = curAmt,
                    previousAmount = prevAmt,
                    changePercent = changePct
                )
            }
            .maxByOrNull { abs(it.currentAmount - it.previousAmount) }

        // ── Savings Momentum ──
        val savingsMomentum = if (monthlyIncome != null && monthlyIncome > 0.0 && monthlyTotals.isNotEmpty()) {
            SavingsMomentum(
                headroomPerMonth = monthlyTotals.map { (label, spend) ->
                    MonthlyHeadroom(monthLabel = label, headroom = monthlyIncome - spend)
                }
            )
        } else null

        // ── Investment Illustration ──
        // Principal = average monthly headroom × 3 months (quarterly savings)
        val investmentIllustration = if (monthlyIncome != null && monthlyIncome > 0.0) {
            val avgMonthlySpend = if (monthlyTotals.isNotEmpty()) {
                monthlyTotals.map { it.second }.average()
            } else {
                periodTotal / 3.0
            }
            val monthlySavings = (monthlyIncome - avgMonthlySpend).coerceAtLeast(0.0)
            val quarterlySavings = monthlySavings * 3.0
            if (quarterlySavings > 0.0) {
                val r = INVESTMENT_ANNUAL_RATE
                val n = INVESTMENT_COMPOUNDING_PERIODS
                val t = INVESTMENT_HORIZON_MONTHS / 12.0
                val futureValue = quarterlySavings * (1.0 + r / n).pow(n * t)
                InvestmentIllustration(
                    principalAmount = quarterlySavings,
                    annualRate = r,
                    compoundingPeriodsPerYear = n,
                    horizonMonths = INVESTMENT_HORIZON_MONTHS,
                    futureValue = futureValue,
                    disclaimer = INVESTMENT_DISCLAIMER
                )
            } else null
        } else null

        return QuarterlyReviewSnapshot(
            id = UUID.randomUUID().toString(),
            periodLabel = periodLabel,
            periodTotal = periodTotal,
            prevQuarterTotal = prevQuarterTotal,
            delta = delta,
            deltaPercent = deltaPercent,
            topCategories = top5,
            biggestMover = biggestMover,
            totalFees = totalFees,
            savingsMomentum = savingsMomentum,
            investmentIllustration = investmentIllustration,
            generatedAt = System.currentTimeMillis()
        )
    }
}
