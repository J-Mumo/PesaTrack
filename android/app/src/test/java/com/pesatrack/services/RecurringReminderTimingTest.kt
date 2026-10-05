package com.pesatrack.services

import com.pesatrack.domain.models.RecurrenceCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecurringReminderTimingTest {
    private val zone = ZoneId.of("Africa/Nairobi")
    private fun time(value: String, z: ZoneId = zone) =
        LocalDateTime.parse(value).atZone(z).toInstant().toEpochMilli()

    @Test fun `monthly due day clamps at February end`() {
        assertEquals(time("2026-02-28T12:00"), RecurringReminderTiming.nextExpected(
            time("2026-01-31T18:00"), RecurrenceCycle.MONTHLY, 31, zone))
    }

    @Test fun `monthly due day returns to 31 after short month`() {
        assertEquals(time("2026-03-31T12:00"), RecurringReminderTiming.nextExpected(
            time("2026-02-28T18:00"), RecurrenceCycle.MONTHLY, 31, zone))
    }

    @Test fun `unpaid monthly occurrence remains overdue rather than rolling forward`() {
        val expected = RecurringReminderTiming.nextExpected(time("2026-08-15T12:00"), RecurrenceCycle.MONTHLY, 15, zone)
        assertEquals(time("2026-09-15T12:00"), expected)
        assertTrue(RecurringReminderTiming.isOverdue(expected, time("2026-10-05T09:00"), zone))
    }

    @Test fun `due date and two following calendar days are not overdue`() {
        val due = time("2026-10-05T12:00")
        assertFalse(RecurringReminderTiming.isOverdue(due, time("2026-10-05T18:00"), zone))
        assertFalse(RecurringReminderTiming.isOverdue(due, time("2026-10-07T23:59"), zone))
        assertTrue(RecurringReminderTiming.isOverdue(due, time("2026-10-08T00:00"), zone))
    }

    @Test fun `tomorrow is not today despite less than 24 hours remaining`() {
        assertEquals(1L, RecurringReminderTiming.daysUntil(time("2026-10-06T12:00"), time("2026-10-05T23:00"), zone))
        assertEquals(0L, RecurringReminderTiming.daysUntil(time("2026-10-05T12:00"), time("2026-10-05T18:00"), zone))
    }

    @Test fun `weekly dates use local calendar across DST`() {
        val dstZone = ZoneId.of("America/New_York")
        assertEquals(time("2026-03-09T12:00", dstZone), RecurringReminderTiming.nextExpected(
            time("2026-03-02T12:00", dstZone), RecurrenceCycle.WEEKLY, null, dstZone))
    }

    @Test fun `yearly leap day clamps to following February end`() {
        assertEquals(time("2025-02-28T12:00"), RecurringReminderTiming.nextExpected(
            time("2024-02-29T12:00"), RecurrenceCycle.YEARLY, null, zone))
    }

    @Test fun `biweekly adds fourteen calendar days`() {
        assertEquals(time("2026-10-19T12:00"), RecurringReminderTiming.nextExpected(
            time("2026-10-05T18:00"), RecurrenceCycle.BIWEEKLY, null, zone))
    }

    @Test fun `schedule waits for next local morning`() {
        assertEquals(60 * 60 * 1000L, RecurringReminderTiming.initialDelayMs(time("2026-10-05T08:00"), zone))
        assertEquals(24 * 60 * 60 * 1000L, RecurringReminderTiming.initialDelayMs(time("2026-10-05T09:00"), zone))
        assertEquals(23 * 60 * 60 * 1000L, RecurringReminderTiming.initialDelayMs(time("2026-10-05T10:00"), zone))
    }
}