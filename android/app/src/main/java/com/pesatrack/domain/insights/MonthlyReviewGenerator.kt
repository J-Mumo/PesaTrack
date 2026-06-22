package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.IncomeSourceTotal
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
    private const val INVESTMENT_HORIZON_MONTHS = 60 // 5 years
    private const val INVESTMENT_DISCLAIMER = "Illustration only. Assumes a single deposit of this amount left to grow at 10% annual return compounded monthly. Actual returns vary."
    private const val RECOMMENDED_INVESTMENT_PERCENT = 0.20

    /**
     * Build a monthly review snapshot.
     *
     * @param currentMonthCategories category breakdown for the reviewed month.
     * @param previousMonthCategories category breakdown for the previous month.
     * @param monthStart first day of the reviewed month.
     * @param monthlyIncome user's monthly income, or null if not set.
     * @param currentDate today's date (for pace calculation when reviewing current month).
     */
    /**
     * @param actualInvestmentAmount total amount invested (Savings & Investments group 18)
     *   during the reviewed month. 0.0 if none.
     */
    fun generate(
        currentMonthCategories: List<CategoryTotal>,
        previousMonthCategories: List<CategoryTotal>,
        monthStart: LocalDate,
        monthlyIncome: Double?,
        actualInvestmentAmount: Double = 0.0,
        incomeBreakdown: List<IncomeSourceTotal> = emptyList(),
        effectiveIncomeSource: EffectiveIncomeSource? = null,
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

        // ── Investment Illustration (tier-based) ──
        val investmentIllustration = buildInvestmentIllustration(
            actualInvestmentAmount = actualInvestmentAmount,
            monthlyIncome = monthlyIncome,
            totalSpent = totalSpent,
            feesPaid = feesPaid,
            effectiveIncomeSource = effectiveIncomeSource
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
            incomeBreakdown = incomeBreakdown,
            effectiveIncomeSource = effectiveIncomeSource,
            generatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Build tier-based investment illustration.
     *
     * Priority:
     * 1. C — Actual investments detected (Savings & Investments group 18)
     * 2. A — Income set + headroom > 0 (but no investments)
     * 3. B — No income or headroom ≤ 0 → 20% nudge
     *
     * For users already investing, shows next target tier:
     * - < 20% → target 20%
     * - 20-29% → target 30%
     * - 30-49% → target 50%
     * - ≥ 50% → no higher target (celebrate)
     */
    fun buildInvestmentIllustration(
        actualInvestmentAmount: Double,
        monthlyIncome: Double?,
        totalSpent: Double,
        feesPaid: Double = 0.0,
        effectiveIncomeSource: EffectiveIncomeSource? = null
    ): InvestmentIllustration {
        val r = INVESTMENT_ANNUAL_RATE
        val n = INVESTMENT_COMPOUNDING_PERIODS
        val t = INVESTMENT_HORIZON_MONTHS / 12.0

        // Determine source, principal, and tier info
        val source: InvestmentSource
        val principal: Double
        val currentPercent: Double?
        val nextTargetPercent: Double?
        val gapAmount: Double?

        if (actualInvestmentAmount > 0.0) {
            // C — Actual investments found
            source = InvestmentSource.ACTUAL_INVESTMENT
            principal = actualInvestmentAmount
            currentPercent = if (monthlyIncome != null && monthlyIncome > 0.0) {
                (actualInvestmentAmount / monthlyIncome) * 100.0
            } else null

            val pct = currentPercent ?: 0.0
            nextTargetPercent = when {
                pct >= 50.0 -> null
                pct >= 30.0 -> 50.0
                pct >= 20.0 -> 30.0
                else -> 20.0
            }
            gapAmount = if (nextTargetPercent != null && monthlyIncome != null && monthlyIncome > 0.0) {
                ((monthlyIncome * nextTargetPercent / 100.0) - actualInvestmentAmount).coerceAtLeast(0.0)
            } else null

        } else if (monthlyIncome != null && monthlyIncome > 0.0 && (monthlyIncome - totalSpent) > 0.0) {
            // A — Headroom available
            source = InvestmentSource.HEADROOM
            principal = monthlyIncome - totalSpent
            currentPercent = 0.0
            nextTargetPercent = RECOMMENDED_INVESTMENT_PERCENT * 100.0
            gapAmount = (monthlyIncome * RECOMMENDED_INVESTMENT_PERCENT)

        } else {
            // B — Nudge: 20% of income (or 20% of spending if no income)
            source = InvestmentSource.NUDGE_TARGET
            principal = if (monthlyIncome != null && monthlyIncome > 0.0) {
                monthlyIncome * RECOMMENDED_INVESTMENT_PERCENT
            } else {
                (totalSpent - feesPaid).coerceAtLeast(0.0) * RECOMMENDED_INVESTMENT_PERCENT
            }
            currentPercent = 0.0
            nextTargetPercent = RECOMMENDED_INVESTMENT_PERCENT * 100.0
            gapAmount = principal // The full target amount since current is 0
        }

        val futureValue = principal * (1.0 + r / n).pow(n * t)

        // Append source attribution to the disclaimer so the user can see
        // whether the underlying income figure came from SMS detection or
        // their own manual override (Phase 4 honesty requirement).
        val attribution = when (effectiveIncomeSource) {
            EffectiveIncomeSource.DETECTED ->
                if (monthlyIncome != null && monthlyIncome > 0.0)
                    " Based on detected income of KES ${"%,.0f".format(monthlyIncome)}."
                else ""
            EffectiveIncomeSource.MANUAL_OVERRIDE,
            EffectiveIncomeSource.DETECTED_BELOW_OVERRIDE ->
                if (monthlyIncome != null && monthlyIncome > 0.0)
                    " Based on the income you set (KES ${"%,.0f".format(monthlyIncome)})."
                else ""
            EffectiveIncomeSource.NONE, null -> ""
        }
        val disclaimer = INVESTMENT_DISCLAIMER + attribution

        return InvestmentIllustration(
            source = source,
            principalAmount = principal,
            annualRate = r,
            compoundingPeriodsPerYear = n,
            horizonMonths = INVESTMENT_HORIZON_MONTHS,
            futureValue = futureValue,
            currentPercent = currentPercent,
            nextTargetPercent = nextTargetPercent,
            gapAmount = gapAmount,
            disclaimer = disclaimer
        )
    }
}
