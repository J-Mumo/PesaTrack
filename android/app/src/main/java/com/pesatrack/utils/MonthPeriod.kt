package com.pesatrack.utils

import java.util.Calendar
import java.util.Locale

/**
 * Single source of truth for "budget month" period arithmetic.
 *
 * Mirrors the convention already used by `BudgetRepository.getPeriodRange` /
 * `getPeriodKey` for [BudgetPeriod.MONTHLY] so that income, expenses and budgets
 * all agree on what "this month" means when the user sets a non-default
 * `monthStartDay` (e.g. salary on the 25th).
 *
 * Convention: a period is named after the calendar year/month of its **start**
 * date. With `monthStartDay = 25`, the period Mar 25 – Apr 24 is the "March"
 * period and keys as `"2026-03-25"`; with `monthStartDay = 1` it keys as the
 * plain `"2026-03"` for backwards compatibility with rows written before this
 * feature existed.
 */
object MonthPeriod {

    /** Bounds (startMs inclusive, endMs exclusive) for the period containing [ms]. */
    fun rangeContaining(monthStartDay: Int, ms: Long): Pair<Long, Long> {
        val startDay = monthStartDay.coerceIn(1, 28)
        val cal = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.get(Calendar.DAY_OF_MONTH) >= startDay) {
            cal.set(Calendar.DAY_OF_MONTH, startDay)
        } else {
            cal.add(Calendar.MONTH, -1)
            cal.set(Calendar.DAY_OF_MONTH, startDay)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    /** Bounds for the period containing "now". */
    fun currentRange(
        monthStartDay: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Pair<Long, Long> = rangeContaining(monthStartDay, nowMs)

    /**
     * Bounds for the period whose start date falls in [year]/[month1Based].
     * Useful for iterating backwards by N periods.
     */
    fun rangeForPeriodStart(
        year: Int,
        month1Based: Int,
        monthStartDay: Int
    ): Pair<Long, Long> {
        val startDay = monthStartDay.coerceIn(1, 28)
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1Based - 1)
            set(Calendar.DAY_OF_MONTH, startDay)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    /**
     * Lookup key for the per-month income override.
     *
     * - `monthStartDay = 1` → `"yyyy-MM"` (backwards-compatible with rows
     *   written before the offset-aware lookup existed).
     * - `monthStartDay ≠ 1` → `"yyyy-MM-dd"` (matches what `BudgetRepository`
     *   already writes from the Budget screen).
     */
    fun keyForPeriodStart(year: Int, month1Based: Int, monthStartDay: Int): String {
        val startDay = monthStartDay.coerceIn(1, 28)
        return if (startDay == 1) {
            String.format(Locale.US, "%04d-%02d", year, month1Based)
        } else {
            String.format(Locale.US, "%04d-%02d-%02d", year, month1Based, startDay)
        }
    }

    /** Convenience: key for the period containing "now". */
    fun currentKey(
        monthStartDay: Int,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val (start, _) = currentRange(monthStartDay, nowMs)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        return keyForPeriodStart(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            monthStartDay
        )
    }

    /**
     * Human-readable label.
     *
     * - `monthStartDay = 1` → `"March 2026"`
     * - `monthStartDay ≠ 1` → `"Mar 25 – Apr 24, 2026"`
     */
    fun labelForRange(startMs: Long, endMs: Long, monthStartDay: Int): String {
        val startCal = Calendar.getInstance().apply { timeInMillis = startMs }
        val startDay = monthStartDay.coerceIn(1, 28)
        if (startDay == 1) {
            return String.format(
                Locale.getDefault(),
                "%s %d",
                MONTH_NAMES[startCal.get(Calendar.MONTH)],
                startCal.get(Calendar.YEAR)
            )
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = endMs
            add(Calendar.DAY_OF_MONTH, -1) // end is exclusive, show last inclusive day
        }
        return String.format(
            Locale.getDefault(),
            "%s %d – %s %d, %d",
            SHORT_MONTH_NAMES[startCal.get(Calendar.MONTH)],
            startCal.get(Calendar.DAY_OF_MONTH),
            SHORT_MONTH_NAMES[endCal.get(Calendar.MONTH)],
            endCal.get(Calendar.DAY_OF_MONTH),
            endCal.get(Calendar.YEAR)
        )
    }

    private val MONTH_NAMES = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private val SHORT_MONTH_NAMES = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
}
