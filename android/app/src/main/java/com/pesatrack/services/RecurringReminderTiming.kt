package com.pesatrack.services

import com.pesatrack.domain.models.RecurrenceCycle
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Calendar-based reminder rules, shared by detection and delivery. */
internal object RecurringReminderTiming {
    fun nextExpected(
        lastOccurrence: Long,
        cycle: RecurrenceCycle,
        expectedDayOfMonth: Int?,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val last = Instant.ofEpochMilli(lastOccurrence).atZone(zone).toLocalDate()
        val next = when (cycle) {
            RecurrenceCycle.MONTHLY -> {
                val month = last.withDayOfMonth(1).plusMonths(1)
                month.withDayOfMonth((expectedDayOfMonth ?: last.dayOfMonth).coerceIn(1, month.lengthOfMonth()))
            }
            RecurrenceCycle.YEARLY -> last.plusYears(1)
            else -> last.plusDays(cycle.expectedDays.toLong())
        }
        // Do not roll an unpaid occurrence into the future just because time passed.
        return next.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }

    fun daysUntil(expected: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(expected).atZone(zone).toLocalDate()
        )

    fun isOverdue(expected: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        daysUntil(expected, now, zone) < -RecurringExpenseService.OVERDUE_GRACE_DAYS

    fun initialDelayMs(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val current = Instant.ofEpochMilli(now).atZone(zone)
        var morning = current.toLocalDate().atTime(9, 0).atZone(zone)
        if (!morning.isAfter(current)) morning = morning.plusDays(1)
        return morning.toInstant().toEpochMilli() - now
    }
}