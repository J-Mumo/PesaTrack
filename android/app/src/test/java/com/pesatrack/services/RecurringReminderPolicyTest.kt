package com.pesatrack.services

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.Locale

class RecurringReminderPolicyTest {
    @Test fun exactAllowListAndSupportBoundary() {
        for (category in listOf(1009L, 1002L, 1012L, 1007L, 1004L)) {
            assertTrue(RecurringReminderPolicy.resolve(true, category, 0.7, 0.7, null).canDeliver)
            assertFalse(RecurringReminderPolicy.resolve(true, category, 0.699, 1.0, null).selected)
        }
        for (category in listOf(null, 10L, 606L, 1001L, 1003L, 1005L, 1006L, 1008L, 1010L, 1011L, 99999L))
            assertFalse(RecurringReminderPolicy.resolve(true, category, 1.0, 1.0, null).selected)
    }

    @Test fun precedenceAndConfidenceAndPausedSelections() {
        assertFalse(RecurringReminderPolicy.resolve(true, 1009L, 1.0, 1.0, RecurringReminderOverride.DISABLED).selected)
        assertTrue(RecurringReminderPolicy.resolve(true, null, 0.0, 0.7, RecurringReminderOverride.ENABLED).canDeliver)
        val medium = RecurringReminderPolicy.resolve(true, 999L, 0.0, 0.69, RecurringReminderOverride.ENABLED)
        assertTrue(medium.selected); assertFalse(medium.canDeliver)
        val paused = RecurringReminderPolicy.resolve(false, 999L, 0.0, 1.0, RecurringReminderOverride.ENABLED)
        assertTrue(paused.selected); assertFalse(paused.canDeliver)
        assertFalse(RecurringReminderPolicy.resolve(true, 606L, 1.0, 1.0, RecurringReminderOverride.ENABLED).selected)
        assertTrue(RecurringReminderPolicy.resolve(true, 1009L, 1.0, 1.0, null).selected)
    }

    @Test fun accountsNormalizationAndDelimiterCollisions() {
        val first = RecurringPaymentIdentity.create("PAY_BILL", " abc  123. ", " kplc  prepaid. ")
        assertEquals(first, RecurringPaymentIdentity.create(" pay_bill ", "ABC 123", "KPLC PREPAID"))
        assertNotEquals(first, RecurringPaymentIdentity.create("PAY_BILL", "ABC 124", "KPLC PREPAID"))
        assertNotEquals(RecurringPaymentIdentity.create("PAY_BILL", "C", "A::B"),
            RecurringPaymentIdentity.create("PAY_BILL", "B::C", "A"))
        assertTrue(RecurringPaymentIdentity.isValid(first))
        assertEquals("Account ••••1234", RecurringPaymentIdentity.maskedAccount("PAY_BILL", "98761234"))
        assertNull(RecurringPaymentIdentity.maskedAccount("SEND_MONEY", "0712345678"))
    }

    @Test fun stableStoredRecipientAndNameOnlyFallback() {
        assertEquals(RecurringPaymentIdentity.create("SEND_MONEY", "0712345678", "Old name"),
            RecurringPaymentIdentity.create("SEND_MONEY", "0712345678", "Edited name"))
        assertEquals(RecurringPaymentIdentity.create("BUY_GOODS", "", "shop."),
            RecurringPaymentIdentity.create("BUY_GOODS", "", " SHOP "))
        assertNotEquals(RecurringPaymentIdentity.create("BUY_GOODS", "", "Old"),
            RecurringPaymentIdentity.create("BUY_GOODS", "", "New"))
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("TILL", RecurringPaymentIdentity.normalize("till"))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun codecRoundTripAbsentEmptyMalformedAndFutureSchema() {
        val key = RecurringPaymentIdentity.create("PAY_BILL", "O'NEIL::123", "UTILITY")
        val map = mapOf(key to RecurringReminderOverride.DISABLED)
        assertEquals(map, RecurringReminderOverridesCodec.decode(RecurringReminderOverridesCodec.encode(map)).overrides)
        assertFalse(RecurringReminderOverridesCodec.decode(null).present)
        assertTrue(RecurringReminderOverridesCodec.decode(RecurringReminderOverridesCodec.encode(emptyMap())).present)
        for (raw in listOf("", "not base64", "A".repeat(1_000_001))) {
            val decoded = RecurringReminderOverridesCodec.decode(raw)
            assertFalse(decoded.valid); assertTrue(decoded.overrides.isEmpty())
        }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.writeInt(2); it.writeInt(0) }
        assertFalse(RecurringReminderOverridesCodec.decode(Base64.getEncoder().encodeToString(bytes.toByteArray())).valid)
        assertFalse(RecurringReminderOverridesCodec.decode(RecurringReminderOverridesCodec.encode(map) + "AAAA").valid)
    }

    @Test fun backupRoundTripOldAndSafeFallback() {
        val key = RecurringPaymentIdentity.create("BUY_GOODS", "NETFLIX", null)
        val map = mapOf(key to RecurringReminderOverride.ENABLED)
        val restored = RecurringReminderBackup.validate("false", RecurringReminderOverridesCodec.encode(map))
        assertEquals(false, restored.master); assertEquals(map, restored.selections.overrides); assertNull(restored.warning)
        val old = RecurringReminderBackup.validate(null, null)
        assertNull(old.master); assertTrue(old.selections.overrides.isEmpty()); assertNotNull(old.warning)
        for ((master, raw) in listOf("true" to "bad", "invalid" to RecurringReminderOverridesCodec.encode(map), "true" to null)) {
            val invalid = RecurringReminderBackup.validate(master, raw)
            assertEquals(false, invalid.master); assertTrue(invalid.selections.overrides.isEmpty()); assertNotNull(invalid.warning)
        }
    }

    @Test fun duplicateKeysUnknownValuesInvalidIdentitiesAndCountsRejectWholeMap() {
        val key = RecurringPaymentIdentity.create("SEND_MONEY", "0712345678", null)
        fun raw(count: Int, entries: List<Pair<String, String>>): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeInt(1); out.writeInt(count)
                entries.forEach { (identity, value) -> out.writeUTF(identity); out.writeUTF(value) }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray())
        }
        val inputs = listOf(raw(2, listOf(key to "ENABLED", key to "DISABLED")),
            raw(1, listOf(key to "FUTURE")), raw(1, listOf("recipient" to "ENABLED")),
            raw(-1, emptyList()), raw(2001, emptyList()), raw(2, listOf(key to "ENABLED")))
        inputs.forEach {
            val decoded = RecurringReminderOverridesCodec.decode(it)
            assertFalse(decoded.valid); assertTrue(decoded.overrides.isEmpty())
        }
    }

    @Test fun migrationAndCooldownAreConservative() {
        val now = 100 * 86_400_000L
        assertEquals(now - 1000, RecurringReminderPolicy.migratedTimestamp(listOf(0, now - 1000), false, now))
        assertEquals(now, RecurringReminderPolicy.migratedTimestamp(listOf(now - 1000), true, now))
        assertEquals(0L, RecurringReminderPolicy.migratedTimestamp(listOf(0), true, now))
        assertFalse(RecurringReminderPolicy.cooldownElapsed(now, 30, now))
        assertFalse(RecurringReminderPolicy.cooldownElapsed(now + 1000, 30, now))
        assertTrue(RecurringReminderPolicy.cooldownElapsed(now, 30, now + 28 * 86_400_000L))
    }
}