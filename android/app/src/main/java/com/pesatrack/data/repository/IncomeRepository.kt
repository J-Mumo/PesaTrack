package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.IncomeTransactionDao
import com.pesatrack.data.local.database.dao.MonthlyIncomeBudgetDao
import com.pesatrack.data.local.database.entities.IncomeTransactionEntity
import com.pesatrack.domain.models.EffectiveIncome
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.domain.models.IncomeSourceTotal
import com.pesatrack.domain.models.IncomeTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for income (detected SMS transactions + manual override).
 *
 * Exposes [effectiveMonthlyIncome] which all analytics surfaces should use
 * in place of the raw monthly override read — see the reconciliation table
 * in `plans/income-tracking-plan.md` §6.4.
 *
 * Phase 1 ships the data foundation. SMS-detection patterns land in Phase 2;
 * until then `detectedAmount` is always 0 and behaviour matches the legacy
 * "manual override only" path.
 */
@Singleton
class IncomeRepository @Inject constructor(
    private val incomeTransactionDao: IncomeTransactionDao,
    private val monthlyIncomeBudgetDao: MonthlyIncomeBudgetDao,
) {

    // ──────────────────────────────────────────────────────────────────────
    //                            Transactions
    // ──────────────────────────────────────────────────────────────────────

    /** Insert if new (by `transactionId`). Returns the row id, or `null` on duplicate. */
    suspend fun insertIfNew(tx: IncomeTransaction): Long? {
        val rowId = incomeTransactionDao.insertIgnoreOnConflict(tx.toEntity())
        return rowId.takeIf { it >= 0L }
    }

    suspend fun getById(id: Long): IncomeTransaction? =
        incomeTransactionDao.getById(id)?.toDomain()

    fun observeForMonth(yearMonth: String): Flow<List<IncomeTransaction>> {
        val bounds = monthBoundsFor(yearMonth)
        return incomeTransactionDao
            .observeForRange(bounds.startMs, bounds.endMs)
            .map { rows -> rows.map { it.toDomain() } }
    }

    suspend fun getForRange(startMs: Long, endMs: Long): List<IncomeTransaction> =
        incomeTransactionDao.getForRange(startMs, endMs).map { it.toDomain() }

    /**
     * Sum of detected income in the range.
     *
     * When [includeTransfers] is false (the default), only sources with
     * [IncomeSource.isInflow] = true are summed — i.e. self-transfers and
     * other pass-through flows are excluded so the value can safely act as
     * a savings-rate denominator.
     */
    suspend fun sumForRange(
        startMs: Long,
        endMs: Long,
        includeTransfers: Boolean = false
    ): Double {
        return if (includeTransfers) {
            incomeTransactionDao.sumForRange(startMs, endMs)
        } else {
            val names = IncomeSource.INFLOW_SOURCES.map { it.name }
            incomeTransactionDao.sumForRangeBySources(startMs, endMs, names)
        }
    }

    /** Per-source totals for a date range (excludes [isExcluded] rows). */
    suspend fun sourceBreakdown(startMs: Long, endMs: Long): List<IncomeSourceTotal> {
        val rows = incomeTransactionDao.getForRange(startMs, endMs)
            .filter { !it.isExcluded }
        return rows
            .groupBy { IncomeSource.fromName(it.source) }
            .map { (source, txs) ->
                IncomeSourceTotal(
                    source = source,
                    total = txs.sumOf { it.amount },
                    transactionCount = txs.size
                )
            }
            .sortedByDescending { it.total }
    }

    suspend fun updateSource(id: Long, source: IncomeSource) {
        incomeTransactionDao.updateSource(id, source.name)
    }

    suspend fun setExcluded(id: Long, excluded: Boolean) {
        incomeTransactionDao.setExcluded(id, excluded)
    }

    // ──────────────────────────────────────────────────────────────────────
    //                       Manual monthly override
    // ──────────────────────────────────────────────────────────────────────

    /** Return the manual override amount for a month, or null. */
    suspend fun getManualOverride(yearMonth: String): Double? =
        monthlyIncomeBudgetDao.getByYearMonth(yearMonth)?.amount

    // ──────────────────────────────────────────────────────────────────────
    //                          Reconciliation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * The income value analytics should use for [yearMonth], plus a label
     * describing where it came from. See plan §6.4 for the rules table.
     */
    suspend fun effectiveMonthlyIncome(yearMonth: String): EffectiveIncome {
        val bounds = monthBoundsFor(yearMonth)
        val detected = sumForRange(bounds.startMs, bounds.endMs, includeTransfers = false)
        val manual = getManualOverride(yearMonth)
        return reconcile(detected = detected, manual = manual)
    }

    // ──────────────────────────────────────────────────────────────────────
    //                       Internal — testable helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun IncomeTransaction.toEntity(): IncomeTransactionEntity =
        IncomeTransactionEntity(
            id = id,
            transactionId = transactionId,
            amount = amount,
            timestamp = timestamp,
            source = source.name,
            sender = sender,
            rawSms = rawSms,
            parserSource = parserSource,
            note = note,
            isExcluded = isExcluded,
            isCategorized = isCategorized
        )

    private fun IncomeTransactionEntity.toDomain(): IncomeTransaction =
        IncomeTransaction(
            id = id,
            transactionId = transactionId,
            amount = amount,
            timestamp = timestamp,
            source = IncomeSource.fromName(source),
            sender = sender,
            rawSms = rawSms,
            parserSource = parserSource,
            note = note,
            isExcluded = isExcluded,
            isCategorized = isCategorized
        )

    private data class MonthBounds(val startMs: Long, val endMs: Long)

    private fun monthBoundsFor(yearMonth: String): MonthBounds {
        val parts = yearMonth.split('-')
        require(parts.size == 2) { "yearMonth must be in 'yyyy-MM' format, got: $yearMonth" }
        val year = parts[0].toInt()
        val month = parts[1].toInt() // 1-based
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return MonthBounds(start, cal.timeInMillis)
    }

    companion object {
        /** Override must exceed detected by more than this fraction to win. */
        const val OVERRIDE_OVER_DETECTED_THRESHOLD: Double = 0.10

        /**
         * Pure reconciliation rule — exposed for unit testing.
         * See plan §6.4 for the rules table.
         */
        fun reconcile(detected: Double, manual: Double?): EffectiveIncome {
            val safeDetected = detected.coerceAtLeast(0.0)
            return when {
                safeDetected <= 0.0 && manual == null -> EffectiveIncome(
                    value = null,
                    source = EffectiveIncomeSource.NONE,
                    detectedAmount = 0.0,
                    manualAmount = null
                )

                safeDetected <= 0.0 -> EffectiveIncome(
                    value = manual,
                    source = EffectiveIncomeSource.MANUAL_OVERRIDE,
                    detectedAmount = 0.0,
                    manualAmount = manual
                )

                manual == null -> EffectiveIncome(
                    value = safeDetected,
                    source = EffectiveIncomeSource.DETECTED,
                    detectedAmount = safeDetected,
                    manualAmount = null
                )

                manual > safeDetected * (1.0 + OVERRIDE_OVER_DETECTED_THRESHOLD) -> EffectiveIncome(
                    value = manual,
                    source = EffectiveIncomeSource.DETECTED_BELOW_OVERRIDE,
                    detectedAmount = safeDetected,
                    manualAmount = manual
                )

                else -> EffectiveIncome(
                    value = safeDetected,
                    source = EffectiveIncomeSource.DETECTED,
                    detectedAmount = safeDetected,
                    manualAmount = manual
                )
            }
        }
    }
}
