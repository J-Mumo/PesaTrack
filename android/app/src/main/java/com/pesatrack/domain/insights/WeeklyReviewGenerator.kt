package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal

/**
 * Pure-function builder for [WeeklyReviewSnapshot].
 *
 * Takes raw aggregates already produced by the repository / DAO layer and
 * applies plan-aligned shaping:
 * - Top 5 categories with rolled-up "others" tail (see "Where it went" rule).
 * - Biggest-change category by absolute delta vs. last week.
 * - Fees-paid total surfaced separately (Principle 5).
 * - Headroom included only when income > 0 (intentionally suppressed otherwise).
 * - Limited-data flag set when previous-period spend is zero (no honest comparison).
 *
 * The generator is intentionally side-effect-free and stateless; it does not
 * touch persistence, time, or any framework class. That makes it trivial to
 * unit-test in `app/src/test`.
 */
object WeeklyReviewGenerator {

    /** Constant from plans/insights-and-reports-plan.md (Resolved decisions). */
    const val MPESA_TRANSACTION_COST_CATEGORY_ID: Long = 606L

    /**
     * Build a snapshot.
     *
     * @param periodStart inclusive start of the week window
     * @param periodEnd exclusive end of the week window
     * @param generatedAt timestamp the report was generated at
     * @param currentPeriodCategories category breakdown for [periodStart, periodEnd)
     *   (output of [com.pesatrack.data.local.database.dao.ExpenseDao.getCategoryTotalsForMonth]
     *   applied to a week window — the DAO is window-agnostic).
     * @param previousPeriodCategories category breakdown for the immediately
     *   preceding 7 days. Used to compute biggest change + period delta.
     * @param monthIncome income for the current calendar month in KES, or 0 if unknown.
     * @param monthSpendSoFar total spend so far in the current calendar month.
     * @param monthLabel human-readable label for the headroom window, e.g. "May 2026".
     * @param daysRemainingInMonth integer days remaining in the current month (>= 0).
     */
    fun generate(
        periodStart: Long,
        periodEnd: Long,
        generatedAt: Long,
        currentPeriodCategories: List<CategoryTotal>,
        previousPeriodCategories: List<CategoryTotal>,
        monthIncome: Double,
        monthSpendSoFar: Double,
        monthLabel: String,
        daysRemainingInMonth: Int
    ): WeeklyReviewSnapshot {
        // ── Totals ──
        val periodTotal = currentPeriodCategories.sumOf { it.total }
        val previousPeriodTotal = previousPeriodCategories.sumOf { it.total }
        val periodDays = ((periodEnd - periodStart) / MS_PER_DAY).toInt().coerceAtLeast(1)
        val averagePerDay = if (periodDays > 0) periodTotal / periodDays else 0.0

        // ── Fees (cat 606) ──
        val feesTotal = currentPeriodCategories
            .firstOrNull { it.categoryId == MPESA_TRANSACTION_COST_CATEGORY_ID }
            ?.total
            ?: 0.0

        // ── Top 5 categories (exclude fees so they don't dominate the breakdown) ──
        val rankableCategories = currentPeriodCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .filter { it.total > 0.0 }
            .sortedByDescending { it.total }

        val periodTotalExcludingFees = rankableCategories.sumOf { it.total }
        val top5 = rankableCategories.take(MAX_TOP_CATEGORIES).map { row ->
            CategoryShare(
                categoryId = row.categoryId,
                name = row.categoryName,
                amount = row.total,
                percentageOfPeriod = if (periodTotalExcludingFees > 0.0) {
                    (row.total / periodTotalExcludingFees) * 100.0
                } else 0.0
            )
        }
        val others = rankableCategories.drop(MAX_TOP_CATEGORIES)
        val othersAmount = others.sumOf { it.total }
        val othersCount = others.size

        // ── Biggest change ──
        // Compare every category present in either week. Skip fees (already surfaced).
        val previousByCategoryId = previousPeriodCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .associateBy { it.categoryId }
        val currentByCategoryId = currentPeriodCategories
            .filter { it.categoryId != MPESA_TRANSACTION_COST_CATEGORY_ID }
            .associateBy { it.categoryId }
        val allIds = previousByCategoryId.keys + currentByCategoryId.keys

        val biggestChange = allIds
            .map { id ->
                val current = currentByCategoryId[id]
                val previous = previousByCategoryId[id]
                val name = current?.categoryName ?: previous?.categoryName ?: "Uncategorized"
                val delta = (current?.total ?: 0.0) - (previous?.total ?: 0.0)
                BiggestChange(categoryId = id, name = name, deltaAmount = delta)
            }
            .filter { kotlin.math.abs(it.deltaAmount) > 0.0 }
            .maxByOrNull { kotlin.math.abs(it.deltaAmount) }

        // ── Headroom (only when income is set) ──
        val headroom = if (monthIncome > 0.0) {
            Headroom(
                label = monthLabel,
                income = monthIncome,
                spendSoFar = monthSpendSoFar,
                daysRemaining = daysRemainingInMonth.coerceAtLeast(0)
            )
        } else null

        // ── Limited data: no honest comparison when prior week is empty ──
        val limitedData = previousPeriodTotal <= 0.0

        return WeeklyReviewSnapshot(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = generatedAt,
            periodTotal = periodTotal,
            previousPeriodTotal = previousPeriodTotal,
            averagePerDay = averagePerDay,
            periodDays = periodDays,
            biggestChange = biggestChange,
            topCategories = top5,
            othersAmount = othersAmount,
            othersCount = othersCount,
            feesTotal = feesTotal,
            headroom = headroom,
            limitedData = limitedData
        )
    }

    private const val MS_PER_DAY: Long = 24L * 60L * 60L * 1000L
    private const val MAX_TOP_CATEGORIES = 5
}
