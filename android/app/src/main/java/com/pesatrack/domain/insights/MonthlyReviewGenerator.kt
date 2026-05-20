package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow

/**
 * Pure-function builder for [MonthlyReviewSnapshot].
 *
 * Takes raw aggregates already produced by the repository / DAO layer and
 * applies plan-aligned shaping for the monthly cadence. Mirrors
 * [WeeklyReviewGenerator] but operates on calendar-month boundaries.
 */
object MonthlyReviewGenerator {

    const val MPESA_TRANSACTION_COST_CATEGORY_ID: Long = 606L
    private const val MAX_TOP_CATEGORIES = 5
    private const val INVESTMENT_ANNUAL_RATE = 0.10
    private const val INVESTMENT_COMPOUNDING_PERIODS = 12
    private const val INVESTMENT_HORIZON_MONTHS = 12
    private const val INVESTMENT_DISCLAIMER = "Illustration only — not a recommendation."

    /**
     * Build a monthly review snapshot.
     *
     * @param currentMonthCategories category breakdown for the reviewed month.
     * @param previousMonthCategories category breakdown for the previous month.
     * @param monthStart first day of the reviewed month.
     * @param monthlyIncome user's monthly income, or null if not set.
     * @param currentDate today's date (for pace calculation when reviewing current month).
     */
    fun generate(
        currentMonthCategories: List<CategoryTotal>,
        previousMonthCategories: List<CategoryTotal>,
        monthStart: LocalDate,
        monthlyIncome: Double?,
        currentDate: LocalDate = LocalDate.now()
    ): MonthlyReviewSnapshot {
        val yearMonth = YearMonth.from(monthStart)
        val daysInMonth = yearMonth.lengthOfMonth()
        val monthEnd = yearMonth.atEndOfMonth()
        val monthName = monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

        // ── Totals ──
        val totalSpent = currentMonthCategories.sumOf { it.total }
        val previousMonthTotal = previousMonthCategories.sumOf { it.total }
        val deltaAmount = totalSpent - previousMonthTotal
        val deltaPercent = if (previousMonthTotal > 0.0) {
            ((totalSpent - previousMonthTotal) / previousMonthTotal) * 100.0
        } else null

        // ── Average per day ──
        val daysSoFar = if (currentDate.isBefore(monthEnd) || currentDate.isEqual(monthEnd)) {
            if (currentDate.isBefore(monthStart)) 1
            else (currentDate.dayOfMonth).coerceAtLeast(1)
        } else {
            daysInMonth
        }
        val averagePerDay = if (daysSoFar > 0) totalSpent / daysSoFar else 0.0

        // ── Pace (projected end-of-month) ──
        val pace = averagePerDay * daysInMonth

        // ── Fees (cat 606) ──
        val feesPaid = currentMonthCategories
            .firstOrNull { it.categoryId == MPESA_TRANSACTION_COST_CATEGORY_ID }
            ?.total ?: 0.0

        // ── Top 5 categories (exclude fees) ──
        val rankable = currentMonthCategories
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
        // Others rollup
        val others = rankable.drop(MAX_TOP_CATEGORIES)
        if (others.isNotEmpty()) {
            val othersTotal = others.sumOf { it.total }
            val othersPct = if (totalExcludingFees > 0.0) (othersTotal / totalExcludingFees) * 100.0 else 0.0
            val topWithOthers = top5 + CategoryBreakdown(
                categoryName = "Others (${others.size})",
                categoryId = -1,
                amount = othersTotal,
                percent = othersPct
            )
            // will use topWithOthers below
        }
        val topCategories = if (others.isNotEmpty()) {
            val othersTotal = others.sumOf { it.total }
            val othersPct = if (totalExcludingFees > 0.0) (othersTotal / totalExcludingFees) * 100.0 else 0.0
            top5 + CategoryBreakdown(
                categoryName = "Others (${others.size})",
                categoryId = -1,
                amount = othersTotal,
                percent = othersPct
            )
        } else top5

        // ── Biggest change category ──
        val previousByCategoryId = previousMonthCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .associateBy { it.categoryId }
        val currentByCategoryId = currentMonthCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .associateBy { it.categoryId }
        val allIds = previousByCategoryId.keys + currentByCategoryId.keys

        val biggestChangeCategory = allIds
            .mapNotNull { id ->
                val current = currentByCategoryId[id]
                val previous = previousByCategoryId[id]
                val name = current?.categoryName ?: previous?.categoryName ?: return@mapNotNull null
                val curAmt = current?.total ?: 0.0
                val prevAmt = previous?.total ?: 0.0
                val delta = curAmt - prevAmt
                if (abs(delta) <= 0.0) return@mapNotNull null
                val changePct = if (prevAmt > 0.0) ((curAmt - prevAmt) / prevAmt) * 100.0 else 100.0
                CategoryChange(
                    categoryName = name,
                    categoryId = (id ?: 0L).toInt(),
                    currentAmount = curAmt,
                    previousAmount = prevAmt,
                    changePercent = changePct
                )
            }
            .maxByOrNull { abs(it.currentAmount - it.previousAmount) }

        // ── Headroom ──
        val headroom = if (monthlyIncome != null && monthlyIncome > 0.0) {
            monthlyIncome - totalSpent
        } else null

        // ── Investment Illustration ──
        val discretionary = (totalSpent - feesPaid).coerceAtLeast(0.0)
        val r = INVESTMENT_ANNUAL_RATE
        val n = INVESTMENT_COMPOUNDING_PERIODS
        val t = INVESTMENT_HORIZON_MONTHS / 12.0
        val futureValue = discretionary * (1.0 + r / n).pow(n * t)
        val investmentIllustration = InvestmentIllustration(
            principalAmount = discretionary,
            annualRate = r,
            compoundingPeriodsPerYear = n,
            horizonMonths = INVESTMENT_HORIZON_MONTHS,
            futureValue = futureValue,
            disclaimer = INVESTMENT_DISCLAIMER
        )

        return MonthlyReviewSnapshot(
            id = UUID.randomUUID().toString(),
            monthStart = monthStart,
            monthEnd = monthEnd,
            monthName = monthName,
            totalSpent = totalSpent,
            previousMonthTotal = previousMonthTotal,
            deltaAmount = deltaAmount,
            deltaPercent = deltaPercent,
            averagePerDay = averagePerDay,
            daysInMonth = daysInMonth,
            topCategories = topCategories,
            biggestChangeCategory = biggestChangeCategory,
            feesPaid = feesPaid,
            headroom = headroom,
            monthlyIncome = monthlyIncome,
            pace = pace,
            investmentIllustration = investmentIllustration,
            generatedAt = System.currentTimeMillis()
        )
    }
}
