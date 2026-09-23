package com.pesatrack.services.ai

import com.pesatrack.data.local.database.dao.BudgetDao
import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.MonthlyIncomeBudgetDao
import com.pesatrack.data.local.database.dao.RecipientCategoryMappingDao
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.domain.models.RecurringExpense
import com.pesatrack.services.RecurringExpenseService
import com.pesatrack.services.ai.RecipientAnonymizer.RecipientAggregate
import com.pesatrack.utils.MonthPeriod
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Round a double KES value to the nearest whole shilling; never negative. */
private fun Double.roundToWholeKes(): Int =
    if (this.isNaN() || this <= 0.0) 0 else this.roundToInt().coerceAtLeast(0)

private const val MS_PER_DAY = 24L * 60L * 60L * 1_000L

private fun calendarField(ms: Long, field: Int): Int =
    Calendar.getInstance().apply { timeInMillis = ms }.get(field)

/** Days from [startMs] inclusive to [endMs] exclusive, minimum 1. */
private fun daysBetween(startMs: Long, endMs: Long): Int =
    ((endMs - startMs) / MS_PER_DAY).toInt().coerceAtLeast(1)

/**
 * Days elapsed from [startMs] inclusive to [nowMs] inclusive.
 * Coerced to at least 1 so downstream pace math never divides by zero.
 */
private fun daysElapsedIn(startMs: Long, nowMs: Long): Int =
    (((nowMs - startMs) / MS_PER_DAY).toInt() + 1).coerceAtLeast(1)

/** Coefficient of variation. Null when < 2 samples or mean is zero. */
private fun coefficientOfVariation(values: List<Double>): Double? {
    if (values.size < 2) return null
    val mean = values.average()
    if (mean == 0.0) return null
    val variance = values.sumOf { v -> (v - mean).let { it * it } } / values.size
    val stdev = sqrt(variance)
    return stdev / abs(mean)
}

/**
 * Narrow contract implemented by [DataDigestBuilder] so
 * [CoachInsightRepository] (and later slices) can depend on the
 * *capability* of producing a fresh digest without pulling in the six
 * DAOs the concrete builder needs.
 *
 * Also gives unit tests a trivial way to inject a fixed [Build] without
 * touching Room, DataStore, or the recurring-expense service.
 */
interface DigestBuilder {
    /** See [DataDigestBuilder.buildForCurrentPeriod]. */
    suspend fun buildForCurrentPeriod(nowMs: Long = System.currentTimeMillis()): DataDigestBuilder.Build
}

/**
 * Assembles the privacy-safe [DataDigest] payload the client sends to
 * the AI backend.
 *
 * ---
 *
 * ## Architecture
 *
 *  - [buildForCurrentPeriod] is the DAO-facing wrapper. It reads the
 *    user's `monthStartDay`, resolves the current and three previous
 *    budget periods, fetches per-category group totals, top recipients,
 *    active budgets, income budget, recurring detections, then delegates
 *    to [computeDigest].
 *  - [computeDigest] is a **pure function** with no DAO access and no
 *    clock. It's the target of every unit test. The DAO wrapper only
 *    orchestrates; the shaping logic lives inside the pure helper.
 *
 * That split lets tests lock the shaping without needing an in-memory
 * Room database, mirroring the pattern already established by
 * [com.pesatrack.domain.insights.MonthlyReviewGenerator].
 *
 * ---
 *
 * ## What is (and isn't) in the digest today
 *
 *  - `totals.spent` / `spent_last_period` / `spent_3mo_avg` — always
 *    filled from the provided [CategoryTotal] lists.
 *  - `totals.income_est` — from the manual monthly income row for the
 *    current period key. Zero if the user hasn't set one.
 *  - `totals.invested_this_period` — sum of current-period category
 *    totals whose id (or parent group id) equals
 *    [SAVINGS_INVESTMENTS_GROUP_ID].
 *  - `categories` — top ten by absolute spend in the current period,
 *    with budget attached from [activeBudgetsByCategoryId] and
 *    `3mo_avg`/`cv` from the last three complete periods.
 *  - `recurring` — top five by monthly-equivalent amount, filtered to
 *    those meeting [MIN_RECURRING_CONFIDENCE].
 *  - `top_recipients_this_period` — top eight by absolute spend,
 *    anonymised through [RecipientAnonymizer].
 *  - `anomalies_this_week` — always empty in v1. The schema allows the
 *    list to grow additively when we plug in a real detector.
 *
 * See [plans/ai-pro-plan.md](../../../../../../../../plans/ai-pro-plan.md)
 * §7 for the wire schema this class produces.
 */
@Singleton
class DataDigestBuilder @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao,
    private val monthlyIncomeBudgetDao: MonthlyIncomeBudgetDao,
    private val recipientMappingDao: RecipientCategoryMappingDao,
    private val appPreferences: AppPreferences,
    private val recurringExpenseService: RecurringExpenseService,
) : DigestBuilder {

    /**
     * Output of a digest build. [digest] is the wire-safe payload that
     * may be sent to the backend. [rehydrationMap] MUST stay on device —
     * it's the caller's responsibility to hold it in a local val for the
     * duration of the AI request and drop it afterwards.
     */
    data class Build(
        val digest: DataDigest,
        val rehydrationMap: Map<String, String>,
    )

    /**
     * Fetches all required aggregates and builds today's digest for the
     * current budget period.
     *
     * @param nowMs the "now" instant used for period resolution. Injectable
     *   for testability of the wrapper (rarely tested — the pure helper
     *   [computeDigest] carries the coverage).
     */
    override suspend fun buildForCurrentPeriod(nowMs: Long): Build {
        val monthStartDay = appPreferences.getMonthStartDay()
        val (currentStart, currentEnd) = MonthPeriod.currentRange(monthStartDay, nowMs)

        // Previous complete period (the one immediately before the current).
        val previousEnd = currentStart
        val (previousStart, _) = MonthPeriod.rangeContaining(monthStartDay, previousEnd - 1)

        // Three completed periods back from previousEnd for the 3-mo avg.
        val historicalRanges = generateSequence(previousStart) { prev ->
            MonthPeriod.rangeContaining(monthStartDay, prev - 1).first
        }.take(3).toList().map { start ->
            val end = MonthPeriod.rangeContaining(monthStartDay, start).second
            start to end
        }

        val currentCategoryTotals = expenseDao.getCategoryGroupTotals(currentStart, currentEnd)
        val previousCategoryTotals = expenseDao.getCategoryGroupTotals(previousStart, previousEnd)
        val historicalCategoryTotals = historicalRanges.map { (start, end) ->
            expenseDao.getCategoryGroupTotals(start, end)
        }

        val topSpenders = expenseDao.getTopSpendersForMonth(currentStart, currentEnd, TOP_RECIPIENTS)
        val recipientAggregates = topSpenders.map { spender ->
            val primary = recipientMappingDao.getPrimaryMapping(spender.recipientKey)
            RecipientAggregate(
                key = spender.recipientKey,
                displayName = spender.recipientKey,
                spent = spender.total.roundToWholeKes(),
                count = spender.transactionCount,
                categoryId = primary?.categoryId,
                threeMoAvg = 0, // per-recipient 3mo history is a deferred v1.x refinement
            )
        }

        val activeBudgets = budgetDao.getActiveBudgetsList()
        val budgetsByCategoryId: Map<Long, Int> = activeBudgets
            .filter { it.categoryId != null }
            .associate { it.categoryId!! to it.amount.roundToWholeKes() }

        val periodLabel = MonthPeriod.keyForPeriodStart(
            year = calendarField(currentStart, Calendar.YEAR),
            month1Based = calendarField(currentStart, Calendar.MONTH) + 1,
            monthStartDay = monthStartDay,
        )
        val incomeThisPeriod = monthlyIncomeBudgetDao.getByYearMonth(periodLabel)
            ?.amount
            ?.roundToWholeKes()
            ?: 0

        val investedThisPeriod = currentCategoryTotals
            .filter { it.categoryId == SAVINGS_INVESTMENTS_GROUP_ID }
            .sumOf { it.total }
            .roundToWholeKes()

        val recurring = try {
            recurringExpenseService.getRecurringExpenses().recurringExpenses
        } catch (_: Throwable) {
            emptyList()
        }

        val daysElapsed = daysElapsedIn(currentStart, nowMs)
        val daysTotal = daysBetween(currentStart, currentEnd)

        return computeDigest(
            DigestInputs(
                periodLabel = periodLabel,
                monthStartDay = monthStartDay,
                daysElapsed = daysElapsed,
                daysTotal = daysTotal,
                currentPeriodCategoryTotals = currentCategoryTotals,
                previousPeriodCategoryTotals = previousCategoryTotals,
                historicalPeriodCategoryTotals = historicalCategoryTotals,
                recipientAggregates = recipientAggregates,
                budgetsByCategoryId = budgetsByCategoryId,
                incomeThisPeriod = incomeThisPeriod,
                investedThisPeriod = investedThisPeriod,
                recurring = recurring,
                anomalies = emptyList(),
            )
        )
    }

    /**
     * Bundle of pre-fetched aggregates handed to [computeDigest]. Kept as
     * a top-level input DTO so tests can construct it directly without
     * touching any DAO.
     */
    data class DigestInputs(
        val periodLabel: String,
        val monthStartDay: Int,
        val daysElapsed: Int,
        val daysTotal: Int,
        val currentPeriodCategoryTotals: List<CategoryTotal>,
        val previousPeriodCategoryTotals: List<CategoryTotal>,
        /** Three most-recent complete periods, ordered most-recent-first. */
        val historicalPeriodCategoryTotals: List<List<CategoryTotal>>,
        val recipientAggregates: List<RecipientAggregate>,
        val budgetsByCategoryId: Map<Long, Int>,
        val incomeThisPeriod: Int,
        val investedThisPeriod: Int,
        val recurring: List<RecurringExpense>,
        val anomalies: List<DigestAnomaly>,
    )

    companion object {

        /**
         * Group id of the "Savings & Investments" category tree. Any
         * expense whose category is this id (or whose parent is this id
         * after group rollup) counts toward `invested_this_period`.
         */
        const val SAVINGS_INVESTMENTS_GROUP_ID = 18L

        /** Max categories emitted in the digest, per the plan. */
        const val TOP_CATEGORIES = 10

        /** Max recipients emitted in the digest, per the plan. */
        const val TOP_RECIPIENTS = 8

        /** Max recurring detections emitted in the digest, per the plan. */
        const val TOP_RECURRING = 5

        /** Confidence floor for a recurring detection to enter the digest. */
        const val MIN_RECURRING_CONFIDENCE = 0.7

        /**
         * Pure shaping function — no I/O, no clock, no DAOs. Given a
         * bundle of aggregates, produces the final [DataDigest] and the
         * request-scoped recipient rehydration map.
         *
         * All KES aggregates are rounded to whole shillings; the digest
         * schema uses [Int] amounts. Categories are ordered by spend
         * desc, capped at [TOP_CATEGORIES]. Recurring is ordered by
         * monthly-equivalent desc, capped at [TOP_RECURRING], filtered
         * to `confidence >= `[MIN_RECURRING_CONFIDENCE].
         */
        internal fun computeDigest(inputs: DigestInputs): Build {
            val currentSpent = inputs.currentPeriodCategoryTotals.sumOf { it.total }.roundToWholeKes()
            val previousSpent = inputs.previousPeriodCategoryTotals.sumOf { it.total }.roundToWholeKes()
            val threeMoAvgTotal = if (inputs.historicalPeriodCategoryTotals.isEmpty()) {
                0
            } else {
                (inputs.historicalPeriodCategoryTotals
                    .map { totals -> totals.sumOf { it.total } }
                    .average())
                    .takeIf { !it.isNaN() }
                    ?.roundToWholeKes()
                    ?: 0
            }

            val historicalByCategory: Map<Long?, List<Double>> = buildMap {
                for (totals in inputs.historicalPeriodCategoryTotals) {
                    for (row in totals) {
                        merge(row.categoryId, listOf(row.total)) { a, b -> a + b }
                    }
                }
            }

            val digestCategories = inputs.currentPeriodCategoryTotals
                .sortedByDescending { it.total }
                .take(TOP_CATEGORIES)
                .map { row ->
                    val history = historicalByCategory[row.categoryId].orEmpty()
                    val avg = if (history.isNotEmpty()) history.average() else 0.0
                    val cv = coefficientOfVariation(history)
                    DigestCategory(
                        id = row.categoryId,
                        name = row.categoryName,
                        spent = row.total.roundToWholeKes(),
                        budget = row.categoryId?.let(inputs.budgetsByCategoryId::get),
                        threeMoAvg = avg.roundToWholeKes(),
                        cv = cv,
                    )
                }

            val digestRecurring = inputs.recurring
                .asSequence()
                .filter { it.confidence >= MIN_RECURRING_CONFIDENCE }
                .sortedByDescending { it.monthlyEquivalent }
                .take(TOP_RECURRING)
                .map { rec ->
                    DigestRecurring(
                        label = rec.recipientDisplayName,
                        amount = rec.averageAmount.roundToWholeKes(),
                        period = rec.cycle.name,
                        confidence = rec.confidence,
                    )
                }
                .toList()

            val batch = RecipientAnonymizer().anonymize(inputs.recipientAggregates)

            val digest = DataDigest(
                period = inputs.periodLabel,
                monthStartDay = inputs.monthStartDay.coerceIn(1, 28),
                daysElapsed = inputs.daysElapsed.coerceAtLeast(1),
                daysTotal = inputs.daysTotal.coerceAtLeast(1),
                totals = DigestTotals(
                    spent = currentSpent,
                    spentLastPeriod = previousSpent,
                    spent3moAvg = threeMoAvgTotal,
                    incomeEst = inputs.incomeThisPeriod,
                    investedThisPeriod = inputs.investedThisPeriod,
                ),
                categories = digestCategories,
                recurring = digestRecurring,
                topRecipientsThisPeriod = batch.recipients,
                anomaliesThisWeek = inputs.anomalies,
            )
            return Build(digest = digest, rehydrationMap = batch.rehydrationMap)
        }
    }
}
