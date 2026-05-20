package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal
import kotlin.math.abs
import kotlin.math.pow

/**
 * Pure-function builder for [YearInReviewSnapshot].
 *
 * Takes raw aggregates already produced by the repository / DAO layer and
 * applies plan-aligned shaping for the annual cadence.
 */
object YearInReviewGenerator {

    private const val MPESA_TRANSACTION_COST_CATEGORY_ID: Long = 606L
    private const val MAX_TOP_CATEGORIES = 5
    private const val INVESTMENT_ANNUAL_RATE = 0.10
    private const val INVESTMENT_COMPOUNDING_PERIODS = 12
    private const val INVESTMENT_HORIZON_MONTHS = 60
    private const val INVESTMENT_DISCLAIMER =
        "Illustration only. Assumes 10% annual return compounded monthly. Actual returns vary."

    private const val QUIET_LEAK_MIN_TRANSACTIONS = 50
    private const val QUIET_LEAK_MAX_AVG = 300.0
    private const val MAX_QUIET_LEAKS = 3

    /**
     * Build a year-in-review snapshot.
     *
     * @param year The year being reviewed.
     * @param currentYearCategories Category breakdown for the reviewed year.
     * @param previousYearCategories Category breakdown for the previous year.
     * @param monthlyData List of (monthLabel, income, spend) for each month of the year (up to 12).
     */
    fun generate(
        year: Int,
        currentYearCategories: List<CategoryTotal>,
        previousYearCategories: List<CategoryTotal>,
        monthlyData: List<MonthData> = emptyList(),
        actualInvestmentAmount: Double = 0.0
    ): YearInReviewSnapshot {
        // ── Totals ──
        val annualTotal = currentYearCategories.sumOf { it.total }
        val prevYearTotal = previousYearCategories.sumOf { it.total }
        val delta = annualTotal - prevYearTotal

        // ── Fees (cat 606) ──
        val totalFees = currentYearCategories
            .firstOrNull { it.categoryId == MPESA_TRANSACTION_COST_CATEGORY_ID }
            ?.total ?: 0.0
        val monthsWithData = monthlyData.size.coerceAtLeast(1)
        val monthlyAvgFees = totalFees / monthsWithData

        // ── Top 5 categories (exclude fees) ──
        val rankable = currentYearCategories
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
        val previousByCategoryId = previousYearCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .associateBy { it.categoryId }
        val currentByCategoryId = currentYearCategories
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

        // ── Quiet Leaks ──
        val quietLeaks = currentYearCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .filter { it.transactionCount >= QUIET_LEAK_MIN_TRANSACTIONS }
            .filter { it.total > 0.0 && (it.total / it.transactionCount) <= QUIET_LEAK_MAX_AVG }
            .sortedByDescending { it.total }
            .take(MAX_QUIET_LEAKS)
            .map { row ->
                QuietLeakSummary(
                    categoryName = row.categoryName,
                    totalTransactions = row.transactionCount,
                    totalAmount = row.total
                )
            }

        // ── Savings Story ──
        val savingsStory = if (monthlyData.isNotEmpty() && monthlyData.any { it.income > 0.0 }) {
            val headroomMonths = monthlyData.filter { it.income > it.spend }
            val bestMonth = headroomMonths.maxByOrNull { it.income - it.spend }
            val totalHeadroom = headroomMonths.sumOf { it.income - it.spend }
            if (headroomMonths.isNotEmpty() && bestMonth != null) {
                SavingsStory(
                    monthsInHeadroom = headroomMonths.size,
                    bestMonth = bestMonth.label,
                    bestMonthHeadroom = bestMonth.income - bestMonth.spend,
                    totalHeadroom = totalHeadroom
                )
            } else null
        } else null

        // ── Investment Illustration (tier-based) ──
        val annualIncome = monthlyData.sumOf { it.income }
        val investmentIllustration = MonthlyReviewGenerator.buildInvestmentIllustration(
            actualInvestmentAmount = actualInvestmentAmount,
            monthlyIncome = if (annualIncome > 0.0) annualIncome else null,
            totalSpent = annualTotal,
            feesPaid = totalFees
        )

        return YearInReviewSnapshot(
            year = year,
            annualTotal = annualTotal,
            prevYearTotal = prevYearTotal,
            delta = delta,
            topCategories = top5,
            biggestMover = biggestMover,
            totalFees = totalFees,
            monthlyAvgFees = monthlyAvgFees,
            quietLeaks = quietLeaks,
            savingsStory = savingsStory,
            investmentIllustration = investmentIllustration,
            goalsProgress = null,
            generatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Monthly data used for savings story calculation.
     */
    data class MonthData(
        val label: String,
        val income: Double,
        val spend: Double
    )
}
