package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.IncomeDao
import com.pesatrack.data.local.database.dao.ReportSnapshotDao
import com.pesatrack.data.local.database.entities.ReportSnapshotEntity
import com.pesatrack.domain.insights.BiggestChange
import com.pesatrack.domain.insights.CategoryShare
import com.pesatrack.domain.insights.Headroom
import com.pesatrack.domain.insights.WeeklyReviewGenerator
import com.pesatrack.domain.insights.WeeklyReviewSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
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
    private val incomeDao: IncomeDao,
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
        val monthIncome = incomeDao.getByYearMonth(monthYearKeyFor(periodEnd))?.amount ?: 0.0
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

    companion object {
        const val CADENCE_WEEKLY = "WEEKLY"
        private const val WEEK_MS: Long = 7L * 24L * 60L * 60L * 1000L
        private const val TOP_FIELD_SEPARATOR = "|"
        private const val TOP_LINE_SEPARATOR = "\n"
        private val MONTH_LABEL_FORMAT = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        private val YEAR_MONTH_KEY_FORMAT = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    }
}
