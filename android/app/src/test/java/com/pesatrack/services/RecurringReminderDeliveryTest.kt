package com.pesatrack.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecurringReminderDeliveryTest {
    @Test fun disabledAndPermissionBlockedDoNotSendOrConsumeCooldown() = runBlocking {
        for (permission in listOf(false, true)) {
            var sent = 0; var recorded = 0
            val decision = RecurringReminderPolicy.resolve(true, 1009L, 1.0, 1.0, RecurringReminderOverride.DISABLED)
            assertFalse(RecurringReminderDelivery.attempt({ permission && decision.canDeliver }, { true },
                { sent++; true }, { recorded++ }))
            assertEquals(0, sent); assertEquals(0, recorded)
        }
        var recorded = 0
        assertFalse(RecurringReminderDelivery.attempt({ false }, { true }, { true }, { recorded++ }))
        assertEquals(0, recorded)
    }

    @Test fun eligibilityIsRecheckedAfterCooldownAndPlatformFailureIsNotRecorded() = runBlocking {
        var enabled = true; var sent = 0; var recorded = 0
        assertFalse(RecurringReminderDelivery.attempt({ enabled }, { enabled = false; true },
            { sent++; true }, { recorded++ }))
        assertEquals(0, sent); assertEquals(0, recorded)
        assertFalse(RecurringReminderDelivery.attempt({ true }, { true }, { false }, { recorded++ }))
        assertEquals(0, recorded)
    }

    @Test fun successfulSendConsumesCooldownAndToggleCannotRepeatIt() = runBlocking {
        var lastSent = 0L; var sent = 0
        val now = 100 * 86_400_000L
        suspend fun attempt(enabled: Boolean) = RecurringReminderDelivery.attempt(
            { enabled }, { RecurringReminderPolicy.cooldownElapsed(lastSent, 30, now) },
            { sent++; true }, { lastSent = now })
        assertTrue(attempt(true)); assertFalse(attempt(false)); assertFalse(attempt(true))
        assertEquals(1, sent); assertEquals(now, lastSent)
    }

    @Test fun cancellationPropagatesWithoutCooldown() = runBlocking {
        var recorded = false
        try {
            RecurringReminderDelivery.attempt({ throw CancellationException() }, { true }, { true }, { recorded = true })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(recorded)
    }
}