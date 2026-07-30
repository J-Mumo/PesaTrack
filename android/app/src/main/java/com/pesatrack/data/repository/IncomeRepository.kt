package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.IncomeSenderRuleDao
import com.pesatrack.data.local.database.dao.IncomeTransactionDao
import com.pesatrack.data.local.database.dao.MonthlyIncomeBudgetDao
import com.pesatrack.data.local.database.entities.IncomeSenderRuleEntity
import com.pesatrack.data.local.database.entities.IncomeTransactionEntity
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.domain.models.EffectiveIncome
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.domain.models.IncomeSourceTotal
import com.pesatrack.domain.models.IncomeTransaction
import com.pesatrack.utils.MonthPeriod
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
    private val incomeSenderRuleDao: IncomeSenderRuleDao,
    private val appPreferences: AppPreferences,
) {

    /**
     * Cached month start day, mirroring [com.pesatrack.data.repository.BudgetRepository].
     * Default 1 = standard calendar month. Call [refreshMonthStartDay] from a
     * coroutine on app/screen entry to pick up the user's preference.
     */
    @Volatile
    private var _monthStartDay: Int = 1
    val monthStartDay: Int get() = _monthStartDay

    suspend fun refreshMonthStartDay() {
        _monthStartDay = appPreferences.getMonthStartDay()
    }

    // ─────────────────────────────────────────────────────────────────
    //                            Transactions
    // ─────────────────────────────────────────────────────────────────

    /**
     * Insert if new (by `transactionId`). Returns the row id, or `null` on duplicate.
     *
     * When the incoming row is [IncomeSource.UNCATEGORIZED] and the sender has a
     * learned rule (see [learnSenderSource]), the rule is applied before insert
     * so the saved row is already categorized.
     */
    suspend fun insertIfNew(tx: IncomeTransaction): Long? {
        val resolved = applyLearnedRuleIfUncategorized(tx)
        val rowId = incomeTransactionDao.insertIgnoreOnConflict(resolved.toEntity())
        return rowId.takeIf { it >= 0L }
    }

    private suspend fun applyLearnedRuleIfUncategorized(tx: IncomeTransaction): IncomeTransaction {
        if (tx.source != IncomeSource.UNCATEGORIZED) return tx
        val sender = tx.sender?.takeIf { it.isNotBlank() } ?: return tx
        val rule = incomeSenderRuleDao.getBySender(sender) ?: return tx
        val learned = IncomeSource.fromName(rule.source)
        return if (learned == IncomeSource.UNCATEGORIZED) tx else tx.copy(
            source = learned,
            isCategorized = true,
        )
    }

    /**
     * Persist a learned sender → source rule so future income from the same
     * sender is auto-classified. No-ops on blank sender or `UNCATEGORIZED`.
     */
    suspend fun learnSenderSource(sender: String?, source: IncomeSource) {
        val trimmed = sender?.trim().orEmpty()
        if (trimmed.isEmpty() || source == IncomeSource.UNCATEGORIZED) return
        incomeSenderRuleDao.upsert(
            IncomeSenderRuleEntity(
                sender = trimmed,
                source = source.name,
                learnedAt = System.currentTimeMillis(),
            )
        )
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

    /**
     * Permanently remove an income transaction. Used when the row was entered
     * in error (e.g. via [ManualIncomeEntryDialog]) or when the user is sure a
     * detected income has no analytical value at all — different from
     * [setExcluded], which leaves the row visible but flagged. This is
     * destructive; the caller should confirm before invoking.
     */
    suspend fun delete(id: Long) {
        incomeTransactionDao.deleteById(id)
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
     *
     * NOTE: This overload preserves the legacy calendar-month interpretation
     * (string is `"yyyy-MM"`). Prefer [effectiveIncomeForCurrentMonth] /
     * [effectiveIncomeForMonth] for new code so the user's `monthStartDay`
     * preference is honoured.
     */
    suspend fun effectiveMonthlyIncome(yearMonth: String): EffectiveIncome {
        val bounds = monthBoundsFor(yearMonth)
        val detected = sumForRange(bounds.startMs, bounds.endMs, includeTransfers = false)
        val manual = getManualOverride(yearMonth)
        return reconcile(detected = detected, manual = manual)
    }

    /**
     * Bounds (startMs inclusive, endMs exclusive) for the period the user is
     * currently in, honouring their `monthStartDay` preference.
     */
    fun currentMonthBounds(nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> =
        MonthPeriod.currentRange(_monthStartDay, nowMs)

    /** Override lookup key for the period containing "now". */
    fun currentMonthKey(nowMs: Long = System.currentTimeMillis()): String =
        MonthPeriod.currentKey(_monthStartDay, nowMs)

    /**
     * [EffectiveIncome] for the period the user is currently in.
     * Same shape as [effectiveMonthlyIncome] but offset-aware.
     */
    suspend fun effectiveIncomeForCurrentMonth(
        nowMs: Long = System.currentTimeMillis()
    ): EffectiveIncome {
        val (start, end) = currentMonthBounds(nowMs)
        val detected = sumForRange(start, end, includeTransfers = false)
        val manual = getManualOverride(currentMonthKey(nowMs))
        return reconcile(detected = detected, manual = manual)
    }

    /**
     * [EffectiveIncome] for the period whose start date is in [year]/[month1Based].
     * Used by analytics surfaces that iterate backwards by period.
     */
    suspend fun effectiveIncomeForMonth(year: Int, month1Based: Int): EffectiveIncome {
        val (start, end) = MonthPeriod.rangeForPeriodStart(year, month1Based, _monthStartDay)
        val detected = sumForRange(start, end, includeTransfers = false)
        val manual = getManualOverride(
            MonthPeriod.keyForPeriodStart(year, month1Based, _monthStartDay)
        )
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
        require(parts.size == 2 || parts.size == 3) {
            "yearMonth must be 'yyyy-MM' or 'yyyy-MM-dd', got: $yearMonth"
        }
        val year = parts[0].toInt()
        val month = parts[1].toInt() // 1-based
        val day = if (parts.size == 3) parts[2].toInt() else 1
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
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
