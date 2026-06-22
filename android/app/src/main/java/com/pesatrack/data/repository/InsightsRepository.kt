package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.MonthlyIncomeBudgetDao
import com.pesatrack.data.local.database.dao.ReportSnapshotDao
import com.pesatrack.data.local.database.entities.ReportSnapshotEntity
import com.pesatrack.domain.insights.BiggestChange
import com.pesatrack.domain.insights.CategoryShare
import com.pesatrack.domain.insights.Headroom
import com.pesatrack.domain.insights.MonthlyReviewGenerator
import com.pesatrack.domain.insights.MonthlyReviewSnapshot
import com.pesatrack.domain.insights.QuarterlyReviewGenerator
import com.pesatrack.domain.insights.QuarterlyReviewSnapshot
import com.pesatrack.domain.insights.WeeklyReviewGenerator
import com.pesatrack.domain.insights.WeeklyReviewSnapshot
import com.pesatrack.domain.insights.YearInReviewGenerator
import com.pesatrack.domain.insights.YearInReviewSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-and-persist façade for the Insights & Reports feature.
 *
 * Responsibilities (v1.0):
 * - Build a [WeeklyReviewSnapshot] for the past 7 days using existing DAOs.
 * - Persist it as a [ReportSnapshotEntity] so notifications can deep-link and
 *   the "Previous reports" list has something to render.
 * - Hydrate a stored snapshot back into a domain [WeeklyReviewSnapshot] for
 *   the screen.
 *
 * This class is intentionally thin — it owns no heuristics. All shaping
 * lives in [WeeklyReviewGenerator] (pure, unit-testable). All queries
 * delegate to existing DAOs (no new SQL beyond what is already shipped).
 */
@Singleton
class InsightsRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val monthlyIncomeBudgetDao: MonthlyIncomeBudgetDao,
    private val incomeRepository: IncomeRepository,
    private val reportSnapshotDao: ReportSnapshotDao
) {

    // ──────────────────────────────────────────────────────────────────────
    //                 Public API — generation & persistence
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Build a fresh weekly review for the 7 days ending [windowEndExclusive]
     * and persist it. Returns the persisted entity (with assigned id).
     *
     * Replaces any prior snapshot for the same (cadence, periodStart).
     */
    suspend fun generateAndStoreWeeklyReview(
        windowEndExclusive: Long = System.currentTimeMillis()
    ): ReportSnapshotEntity {
        val periodEnd = windowEndExclusive
        val periodStart = periodEnd - WEEK_MS
        val previousPeriodEnd = periodStart
        val previousPeriodStart = previousPeriodEnd - WEEK_MS

        val currentBreakdown = expenseDao.getCategoryTotalsForMonth(periodStart, periodEnd)
        val previousBreakdown = expenseDao.getCategoryTotalsForMonth(previousPeriodStart, previousPeriodEnd)

        val monthBounds = monthBoundsFor(periodEnd)
        val monthLabel = monthLabelFor(periodEnd)
        val monthIncome = monthlyIncomeBudgetDao.getByYearMonth(monthYearKeyFor(periodEnd))?.amount ?: 0.0
        val monthSpendSoFar = if (monthIncome > 0.0) {
            // We only ask for the month total when we actually have an income to compare against.
            expenseDao.getCategoryTotalsForMonth(monthBounds.start, periodEnd).sumOf { it.total }
        } else 0.0
        val daysRemaining = daysRemainingInMonth(periodEnd)

        val snapshot = WeeklyReviewGenerator.generate(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = System.currentTimeMillis(),
            currentPeriodCategories = currentBreakdown,
            previousPeriodCategories = previousBreakdown,
            monthIncome = monthIncome,
            monthSpendSoFar = monthSpendSoFar,
            monthLabel = monthLabel,
            daysRemainingInMonth = daysRemaining
        )

        val entity = snapshot.toEntity()
        val id = reportSnapshotDao.upsert(entity)
        return entity.copy(id = id)
    }

    /** Mark a snapshot as viewed (first time the user opens it). */
    suspend fun markViewed(snapshotId: Long) {
        reportSnapshotDao.markViewed(snapshotId)
    }

    /** Fetch a stored snapshot by id. */
    suspend fun getSnapshot(id: Long): WeeklyReviewSnapshot? =
        reportSnapshotDao.getById(id)?.toDomain()

    /** Fetch the most recent weekly snapshot. */
    suspend fun getLatestWeekly(): WeeklyReviewSnapshot? =
        reportSnapshotDao.getLatestForCadence(CADENCE_WEEKLY)?.toDomain()

    /** Observe the most recent weekly snapshot (for live UI updates). */
    fun observeLatestWeekly(): Flow<WeeklyReviewSnapshot?> =
        reportSnapshotDao.observeLatestForCadence(CADENCE_WEEKLY).map { it?.toDomain() }

    /** List previous weekly snapshots (newest first). */
    suspend fun getPreviousWeeklySnapshots(limit: Int = 12): List<ReportSnapshotEntity> =
        reportSnapshotDao.getRecentForCadence(CADENCE_WEEKLY, limit)

    // ──────────────────────────────────────────────────────────────────────
    //              Monthly Review — Public API (v1.1)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Build a fresh monthly review for the previous calendar month and persist it.
     * Returns the generated [MonthlyReviewSnapshot].
     */
    suspend fun generateAndStoreMonthlyReview(): MonthlyReviewSnapshot {
        val now = System.currentTimeMillis()
        val currentMonthBounds = monthBoundsFor(now)
        // Review the previous month
        val prevMonthEnd = currentMonthBounds.start
        val prevMonthBounds = monthBoundsFor(prevMonthEnd - 1)
        val prevPrevMonthBounds = monthBoundsFor(prevMonthBounds.start - 1)

        val currentCategories = expenseDao.getCategoryGroupTotals(prevMonthBounds.start, prevMonthBounds.endExclusive)
        val previousCategories = expenseDao.getCategoryGroupTotals(prevPrevMonthBounds.start, prevPrevMonthBounds.endExclusive)

        val monthStart = Instant.ofEpochMilli(prevMonthBounds.start)
            .atZone(ZoneId.systemDefault()).toLocalDate()

        val monthIncomeKey = monthYearKeyFor(prevMonthBounds.start)
        // Use reconciled income (detected SMS or manual override) so the
        // monthly review reflects the same figure the rest of the app shows
        // (plan §6.5, §9.3). Surface the source on the snapshot for honesty.
        val effective = incomeRepository.effectiveMonthlyIncome(monthIncomeKey)
        val monthlyIncome = effective.value
        val incomeBreakdown = incomeRepository.sourceBreakdown(
            prevMonthBounds.start,
            prevMonthBounds.endExclusive
        )

        val investmentTotal = expenseDao.getInvestmentTotalInRange(prevMonthBounds.start, prevMonthBounds.endExclusive)

        val snapshot = MonthlyReviewGenerator.generate(
            currentMonthCategories = currentCategories,
            previousMonthCategories = previousCategories,
            monthStart = monthStart,
            monthlyIncome = monthlyIncome,
            actualInvestmentAmount = investmentTotal,
            incomeBreakdown = incomeBreakdown,
            effectiveIncomeSource = effective.source,
            currentDate = monthStart.plusMonths(1) // month is complete
        )

        // Persist using the same entity schema
        val entity = snapshot.toEntity()
        reportSnapshotDao.upsert(entity)

        return snapshot
    }

    /** Fetch the most recent monthly snapshot. */
    suspend fun getLatestMonthly(): MonthlyReviewSnapshot? =
        reportSnapshotDao.getLatestForCadence(CADENCE_MONTHLY)?.toMonthlyDomain()

    /** Observe the most recent monthly snapshot (for live UI updates). */
    fun observeLatestMonthly(): Flow<MonthlyReviewSnapshot?> =
        reportSnapshotDao.observeLatestForCadence(CADENCE_MONTHLY).map { it?.toMonthlyDomain() }

    /** List previous monthly snapshots (newest first). */
    suspend fun getPreviousMonthlySnapshots(limit: Int = 6): List<MonthlyReviewSnapshot> =
        reportSnapshotDao.getRecentForCadence(CADENCE_MONTHLY, limit)
            .mapNotNull { entity -> entity.toMonthlyDomain() }

    /** Fetch a stored monthly snapshot by id. */
    suspend fun getMonthlySnapshot(id: Long): MonthlyReviewSnapshot? =
        reportSnapshotDao.getById(id)?.toMonthlyDomain()

    // ──────────────────────────────────────────────────────────────────────
    //                          Entity ⇄ domain
    // ──────────────────────────────────────────────────────────────────────

    private fun WeeklyReviewSnapshot.toEntity(): ReportSnapshotEntity {
        val encoded = topCategories.joinToString(separator = TOP_LINE_SEPARATOR) { c ->
            // name|amount|pct  (name escapes for | are unnecessary in practice;
            // category names never legitimately contain '|'. If they do we replace.)
            listOf(
                c.name.replace(TOP_FIELD_SEPARATOR, "/"),
                c.amount.toString(),
                c.percentageOfPeriod.toString()
            ).joinToString(TOP_FIELD_SEPARATOR)
        }
        return ReportSnapshotEntity(
            cadence = CADENCE_WEEKLY,
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = generatedAt,
            periodTotal = periodTotal,
            previousPeriodTotal = previousPeriodTotal,
            averagePerDay = averagePerDay,
            periodDays = periodDays,
            biggestChangeCategoryName = biggestChange?.name,
            biggestChangeDelta = biggestChange?.deltaAmount ?: 0.0,
            feesTotal = feesTotal,
            headroomAmount = headroom?.available,
            headroomLabel = headroom?.label,
            topCategories = encoded,
            othersAmount = othersAmount,
            othersCount = othersCount,
            limitedData = limitedData
        )
    }

    private fun ReportSnapshotEntity.toDomain(): WeeklyReviewSnapshot {
        val categories = topCategories
            .split(TOP_LINE_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(TOP_FIELD_SEPARATOR)
                if (parts.size < 3) return@mapNotNull null
                val amount = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val pct = parts[2].toDoubleOrNull() ?: 0.0
                CategoryShare(
                    categoryId = null,
                    name = parts[0],
                    amount = amount,
                    percentageOfPeriod = pct
                )
            }
        val biggest = biggestChangeCategoryName?.let { name ->
            BiggestChange(categoryId = null, name = name, deltaAmount = biggestChangeDelta)
        }
        val headroom = if (headroomAmount != null && headroomLabel != null) {
            // We don't persist the raw income/spend split — only the resolved
            // "available" figure. Reconstruct using available + spend = income
            // wouldn't be honest, so we just store/show what we computed.
            Headroom(
                label = headroomLabel,
                income = headroomAmount, // sentinel — UI only reads .available
                spendSoFar = 0.0,
                daysRemaining = 0
            )
        } else null
        return WeeklyReviewSnapshot(
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = generatedAt,
            periodTotal = periodTotal,
            previousPeriodTotal = previousPeriodTotal,
            averagePerDay = averagePerDay,
            periodDays = periodDays,
            biggestChange = biggest,
            topCategories = categories,
            othersAmount = othersAmount,
            othersCount = othersCount,
            feesTotal = feesTotal,
            headroom = headroom,
            limitedData = limitedData
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    //                            Helpers
    // ──────────────────────────────────────────────────────────────────────

    private data class MonthBounds(val start: Long, val endExclusive: Long)

    private fun monthBoundsFor(timestamp: Long): MonthBounds {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return MonthBounds(start = start, endExclusive = cal.timeInMillis)
    }

    private fun monthLabelFor(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return MONTH_LABEL_FORMAT.format(cal.time)
    }

    private fun monthYearKeyFor(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return YEAR_MONTH_KEY_FORMAT.format(cal.time)
    }

    private fun daysRemainingInMonth(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val last = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (last - today).coerceAtLeast(0)
    }

    private fun MonthlyReviewSnapshot.toEntity(): ReportSnapshotEntity {
        val encoded = topCategories.joinToString(separator = TOP_LINE_SEPARATOR) { c ->
            listOf(
                c.categoryName.replace(TOP_FIELD_SEPARATOR, "/"),
                c.amount.toString(),
                c.percent.toString()
            ).joinToString(TOP_FIELD_SEPARATOR)
        }
        return ReportSnapshotEntity(
            cadence = CADENCE_MONTHLY,
            periodStart = monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            periodEnd = monthEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            generatedAt = generatedAt,
            periodTotal = totalSpent,
            previousPeriodTotal = previousMonthTotal,
            averagePerDay = averagePerDay,
            periodDays = daysInMonth,
            biggestChangeCategoryName = biggestChangeCategory?.categoryName,
            biggestChangeDelta = biggestChangeCategory?.let { it.currentAmount - it.previousAmount } ?: 0.0,
            feesTotal = feesPaid,
            headroomAmount = headroom,
            headroomLabel = monthName,
            topCategories = encoded,
            othersAmount = topCategories.find { it.categoryId == -1 }?.amount ?: 0.0,
            othersCount = topCategories.count { it.categoryId == -1 },
            limitedData = previousMonthTotal <= 0.0
        )
    }

    private suspend fun ReportSnapshotEntity.toMonthlyDomain(): MonthlyReviewSnapshot? {
        val start = Instant.ofEpochMilli(periodStart).atZone(ZoneId.systemDefault()).toLocalDate()
        val end = Instant.ofEpochMilli(periodEnd).atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1)
        val categories = topCategories
            .split(TOP_LINE_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(TOP_FIELD_SEPARATOR)
                if (parts.size < 3) return@mapNotNull null
                val amount = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val pct = parts[2].toDoubleOrNull() ?: 0.0
                com.pesatrack.domain.insights.CategoryBreakdown(
                    categoryName = parts[0],
                    categoryId = -1,
                    amount = amount,
                    percent = pct
                )
            }
        val deltaPercent = if (previousPeriodTotal > 0.0) {
            ((periodTotal - previousPeriodTotal) / previousPeriodTotal) * 100.0
        } else null
        val biggestChange = biggestChangeCategoryName?.let { name ->
            com.pesatrack.domain.insights.CategoryChange(
                categoryName = name,
                categoryId = 0,
                currentAmount = biggestChangeDelta.coerceAtLeast(0.0),
                previousAmount = 0.0,
                changePercent = 0.0
            )
        }
        // Re-query live data so the illustration reflects actual investments,
        // not a derived "discretionary" figure from total spending.
        val investmentTotal = expenseDao.getInvestmentTotalInRange(periodStart, periodEnd)
        val storedIncome = monthlyIncomeBudgetDao.getByYearMonth(monthYearKeyFor(periodStart))?.amount
        val derivedIncome = headroomAmount?.let { it + periodTotal }
        val monthlyIncome = storedIncome ?: derivedIncome
        val illustration = MonthlyReviewGenerator.buildInvestmentIllustration(
            actualInvestmentAmount = investmentTotal,
            monthlyIncome = monthlyIncome,
            totalSpent = periodTotal,
            feesPaid = feesTotal
        )
        return MonthlyReviewSnapshot(
            id = id.toString(),
            monthStart = start,
            monthEnd = end,
            monthName = headroomLabel ?: "${start.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${start.year}",
            totalSpent = periodTotal,
            previousMonthTotal = previousPeriodTotal,
            deltaAmount = periodTotal - previousPeriodTotal,
            deltaPercent = deltaPercent,
            averagePerDay = averagePerDay,
            daysInMonth = periodDays,
            topCategories = categories,
            biggestChangeCategory = biggestChange,
            feesPaid = feesTotal,
            headroom = headroomAmount,
            monthlyIncome = monthlyIncome,
            pace = averagePerDay * periodDays,
            investmentIllustration = illustration,
            generatedAt = generatedAt
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    //              Quarterly Review — Public API (v1.3)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Build a fresh quarterly review for the previous calendar quarter and persist it.
     * Returns the generated [QuarterlyReviewSnapshot].
     */
    suspend fun generateAndStoreQuarterlyReview(): QuarterlyReviewSnapshot {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentMonth = cal.get(Calendar.MONTH) + 1 // 1-based
        val currentYear = cal.get(Calendar.YEAR)

        // Review the current quarter (partial data up to today)
        val currentQ = QuarterlyReviewGenerator.quarterForMonth(currentMonth)
        val firstMonth = QuarterlyReviewGenerator.firstMonthOfQuarter(currentQ)
        val quarterStart = monthBoundsForYearMonth(currentYear, firstMonth).start
        val quarterEnd = now // Up to now (current moment)

        // Previous quarter for comparison
        val prevQ = if (currentQ == 1) 4 else currentQ - 1
        val prevYear = if (currentQ == 1) currentYear - 1 else currentYear
        val prevFirstMonth = QuarterlyReviewGenerator.firstMonthOfQuarter(prevQ)
        val ppStart = monthBoundsForYearMonth(prevYear, prevFirstMonth).start
        val ppEnd = monthBoundsForYearMonth(prevYear, prevFirstMonth + 2).endExclusive

        val currentCategories = expenseDao.getCategoryGroupTotals(quarterStart, quarterEnd)
        val previousCategories = expenseDao.getCategoryGroupTotals(ppStart, ppEnd)

        // Monthly income — average of effective monthly incomes across the
        // quarter months we have data for. Pre-Phase 1 this was the
        // first-month-of-quarter override only, which under-reported income
        // whenever it varied across the quarter (or was set only on later
        // months). The QuarterlyReviewGenerator still multiplies by 3 to
        // produce a quarterly figure, so we hand it the average here.
        val monthsInQuarter = currentMonth - firstMonth + 1
        val monthlyEffectiveIncomes = (0 until monthsInQuarter).map { offset ->
            val m = firstMonth + offset
            val bounds = monthBoundsForYearMonth(currentYear, m)
            incomeRepository.effectiveMonthlyIncome(monthYearKeyFor(bounds.start)).value
        }
        val knownIncomes = monthlyEffectiveIncomes.filterNotNull().filter { it > 0.0 }
        val monthlyIncome: Double? = if (knownIncomes.isNotEmpty()) {
            knownIncomes.average()
        } else null

        // Monthly totals for savings momentum (months in current quarter so far)
        val monthlyTotals = (0 until monthsInQuarter).map { offset ->
            val m = firstMonth + offset
            val bounds = monthBoundsForYearMonth(currentYear, m)
            val endMs = if (m == currentMonth) now else bounds.endExclusive
            val label = monthLabelFor(bounds.start)
            val total = expenseDao.getCategoryTotalsForMonth(bounds.start, endMs).sumOf { it.total }
            label to total
        }

        val investmentTotal = expenseDao.getInvestmentTotalInRange(quarterStart, quarterEnd)

        val snapshot = QuarterlyReviewGenerator.generate(
            currentQuarterCategories = currentCategories,
            previousQuarterCategories = previousCategories,
            quarterNumber = currentQ,
            year = currentYear,
            monthlyIncome = monthlyIncome,
            monthlyTotals = monthlyTotals,
            actualInvestmentAmount = investmentTotal
        )

        val entity = snapshot.toEntity()
        reportSnapshotDao.upsert(entity)

        return snapshot
    }

    /** Fetch the most recent quarterly snapshot. */
    suspend fun getLatestQuarterly(): QuarterlyReviewSnapshot? =
        reportSnapshotDao.getLatestForCadence(CADENCE_QUARTERLY)?.toQuarterlyDomain()

    /** List previous quarterly snapshots (newest first). */
    suspend fun getPreviousQuarterlySnapshots(limit: Int = 4): List<QuarterlyReviewSnapshot> =
        reportSnapshotDao.getRecentForCadence(CADENCE_QUARTERLY, limit).mapNotNull { it.toQuarterlyDomain() }

    /** Fetch a stored quarterly snapshot by id. */
    suspend fun getQuarterlySnapshot(id: Long): QuarterlyReviewSnapshot? =
        reportSnapshotDao.getById(id)?.toQuarterlyDomain()

    // ──────────────────────────────────────────────────────────────────────

    private fun QuarterlyReviewSnapshot.toEntity(): ReportSnapshotEntity {
        val encoded = topCategories.joinToString(separator = TOP_LINE_SEPARATOR) { c ->
            listOf(
                c.categoryName.replace(TOP_FIELD_SEPARATOR, "/"),
                c.amount.toString(),
                c.percent.toString()
            ).joinToString(TOP_FIELD_SEPARATOR)
        }
        return ReportSnapshotEntity(
            cadence = CADENCE_QUARTERLY,
            periodStart = 0L, // Not critical for quarterly — label-based lookup
            periodEnd = 0L,
            generatedAt = generatedAt,
            periodTotal = periodTotal,
            previousPeriodTotal = prevQuarterTotal,
            averagePerDay = 0.0,
            periodDays = 90,
            biggestChangeCategoryName = biggestMover?.categoryName,
            biggestChangeDelta = biggestMover?.let { it.currentAmount - it.previousAmount } ?: 0.0,
            feesTotal = totalFees,
            headroomAmount = savingsMomentum?.headroomPerMonth?.lastOrNull()?.headroom,
            headroomLabel = periodLabel,
            topCategories = encoded,
            othersAmount = 0.0,
            othersCount = 0,
            limitedData = prevQuarterTotal <= 0.0
        )
    }

    private fun ReportSnapshotEntity.toQuarterlyDomain(): QuarterlyReviewSnapshot? {
        val categories = topCategories
            .split(TOP_LINE_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(TOP_FIELD_SEPARATOR)
                if (parts.size < 3) return@mapNotNull null
                val amount = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val pct = parts[2].toDoubleOrNull() ?: 0.0
                com.pesatrack.domain.insights.CategoryBreakdown(
                    categoryName = parts[0],
                    categoryId = -1,
                    amount = amount,
                    percent = pct
                )
            }
        val deltaPercent = if (previousPeriodTotal > 0.0) {
            ((periodTotal - previousPeriodTotal) / previousPeriodTotal) * 100.0
        } else null
        val biggest = biggestChangeCategoryName?.let { name ->
            com.pesatrack.domain.insights.BiggestMover(
                categoryName = name,
                currentAmount = biggestChangeDelta.coerceAtLeast(0.0),
                previousAmount = 0.0,
                changePercent = 0.0
            )
        }
        return QuarterlyReviewSnapshot(
            id = id.toString(),
            periodLabel = headroomLabel ?: "Quarter",
            periodTotal = periodTotal,
            prevQuarterTotal = previousPeriodTotal,
            delta = periodTotal - previousPeriodTotal,
            deltaPercent = deltaPercent,
            topCategories = categories,
            biggestMover = biggest,
            totalFees = feesTotal,
            savingsMomentum = null, // Not persisted in detail
            investmentIllustration = null, // Recomputed on screen if needed
            generatedAt = generatedAt
        )
    }

    private fun monthBoundsForYearMonth(year: Int, month: Int): MonthBounds {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1) // 0-based
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return MonthBounds(start = start, endExclusive = cal.timeInMillis)
    }

    // ──────────────────────────────────────────────────────────────────────
    //              Yearly Review — Public API (v1.4)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Build a fresh year-in-review for the given [year] and persist it.
     * Returns the generated [YearInReviewSnapshot].
     */
    suspend fun generateAndStoreYearlyReview(year: Int): YearInReviewSnapshot {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1

        val yearStart = monthBoundsForYearMonth(year, 1).start
        // If reviewing current year, end at now; otherwise end of year
        val yearEnd = if (year == currentYear) now else monthBoundsForYearMonth(year + 1, 1).start

        val prevYearStart = monthBoundsForYearMonth(year - 1, 1).start
        val prevYearEnd = yearStart

        val currentCategories = expenseDao.getCategoryGroupTotals(yearStart, yearEnd)
        val previousCategories = expenseDao.getCategoryGroupTotals(prevYearStart, prevYearEnd)

        // Monthly data for savings story (only months that have passed or are current)
        val maxMonth = if (year == currentYear) currentMonth else 12
        val monthlyData = (1..maxMonth).map { month ->
            val bounds = monthBoundsForYearMonth(year, month)
            val endMs = if (year == currentYear && month == currentMonth) now else bounds.endExclusive
            val label = monthLabelFor(bounds.start)
            val spend = expenseDao.getCategoryTotalsForMonth(bounds.start, endMs).sumOf { it.total }
            val incomeKey = monthYearKeyFor(bounds.start)
            val income = monthlyIncomeBudgetDao.getByYearMonth(incomeKey)?.amount ?: 0.0
            YearInReviewGenerator.MonthData(label = label, income = income, spend = spend)
        }

        val investmentTotal = expenseDao.getInvestmentTotalInRange(yearStart, yearEnd)

        val snapshot = YearInReviewGenerator.generate(
            year = year,
            currentYearCategories = currentCategories,
            previousYearCategories = previousCategories,
            monthlyData = monthlyData,
            actualInvestmentAmount = investmentTotal
        )

        val entity = snapshot.toEntity()
        reportSnapshotDao.upsert(entity)

        return snapshot
    }

    /** Fetch the most recent yearly snapshot. */
    suspend fun getLatestYearly(): YearInReviewSnapshot? =
        reportSnapshotDao.getLatestForCadence(CADENCE_YEARLY)?.toYearlyDomain()

    /** List previous yearly snapshots (newest first). */
    suspend fun getPreviousYearlySnapshots(limit: Int = 5): List<YearInReviewSnapshot> =
        reportSnapshotDao.getRecentForCadence(CADENCE_YEARLY, limit).mapNotNull { it.toYearlyDomain() }

    /** Fetch a stored yearly snapshot by year. */
    suspend fun getYearlySnapshot(year: Int): YearInReviewSnapshot? {
        // Find among recent snapshots matching the year label
        return getPreviousYearlySnapshots(10).firstOrNull { it.year == year }
    }

    // ──────────────────────────────────────────────────────────────────────

    private fun YearInReviewSnapshot.toEntity(): ReportSnapshotEntity {
        val encoded = topCategories.joinToString(separator = TOP_LINE_SEPARATOR) { c ->
            listOf(
                c.categoryName.replace(TOP_FIELD_SEPARATOR, "/"),
                c.amount.toString(),
                c.percent.toString()
            ).joinToString(TOP_FIELD_SEPARATOR)
        }
        return ReportSnapshotEntity(
            cadence = CADENCE_YEARLY,
            periodStart = 0L,
            periodEnd = 0L,
            generatedAt = generatedAt,
            periodTotal = annualTotal,
            previousPeriodTotal = prevYearTotal,
            averagePerDay = 0.0,
            periodDays = 365,
            biggestChangeCategoryName = biggestMover?.categoryName,
            biggestChangeDelta = biggestMover?.let { it.currentAmount - it.previousAmount } ?: 0.0,
            feesTotal = totalFees,
            headroomAmount = savingsStory?.totalHeadroom,
            headroomLabel = "$year",
            topCategories = encoded,
            othersAmount = 0.0,
            othersCount = 0,
            limitedData = prevYearTotal <= 0.0
        )
    }

    private fun ReportSnapshotEntity.toYearlyDomain(): YearInReviewSnapshot? {
        val yearInt = headroomLabel?.toIntOrNull() ?: return null
        val categories = topCategories
            .split(TOP_LINE_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(TOP_FIELD_SEPARATOR)
                if (parts.size < 3) return@mapNotNull null
                val amount = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val pct = parts[2].toDoubleOrNull() ?: 0.0
                com.pesatrack.domain.insights.CategoryBreakdown(
                    categoryName = parts[0],
                    categoryId = -1,
                    amount = amount,
                    percent = pct
                )
            }
        val biggest = biggestChangeCategoryName?.let { name ->
            com.pesatrack.domain.insights.BiggestMover(
                categoryName = name,
                currentAmount = biggestChangeDelta.coerceAtLeast(0.0),
                previousAmount = 0.0,
                changePercent = 0.0
            )
        }
        return YearInReviewSnapshot(
            year = yearInt,
            annualTotal = periodTotal,
            prevYearTotal = previousPeriodTotal,
            delta = periodTotal - previousPeriodTotal,
            topCategories = categories,
            biggestMover = biggest,
            totalFees = feesTotal,
            monthlyAvgFees = feesTotal / 12.0,
            quietLeaks = emptyList(),
            savingsStory = null,
            investmentIllustration = null,
            goalsProgress = null,
            generatedAt = generatedAt
        )
    }

    companion object {
        const val CADENCE_WEEKLY = "WEEKLY"
        const val CADENCE_MONTHLY = "MONTHLY"
        const val CADENCE_QUARTERLY = "QUARTERLY"
        const val CADENCE_YEARLY = "YEARLY"
        private const val WEEK_MS: Long = 7L * 24L * 60L * 60L * 1000L
        private const val TOP_FIELD_SEPARATOR = "|"
        private const val TOP_LINE_SEPARATOR = "\n"
        private val MONTH_LABEL_FORMAT = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        private val YEAR_MONTH_KEY_FORMAT = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    }
}
