package com.pesatrack.data.local.preferences

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pesatrack.domain.models.*
import com.pesatrack.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RecurringReminderPreferencesTest {
    private fun withStore(test: suspend (AppPreferences, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("recurring-prefs-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { dir.resolve("test.preferences_pb") }
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = dir
        }
        try { test(AppPreferences(context, store), store) }
        finally { scope.coroutineContext[Job]!!.cancelAndJoin(); dir.deleteRecursively() }
    }

    private val rent = RecurringPaymentIdentity.create("SEND_MONEY", "0712345678", "Landlord")
    private val netflix = RecurringPaymentIdentity.create("BUY_GOODS", "Netflix", null)
    private fun payment(ambiguous: Boolean = false) = RecurringExpense(rent, "Landlord", 1009L, "Rent",
        RecurrenceCycle.MONTHLY, 10000.0, 10000.0, AmountPattern.FIXED, 1.0, 3, 0L, 0L, 1,
        PaymentType.SEND_MONEY, false, 1.0, legacyRecipientKeys = setOf("Landlord"), ambiguousLegacyIdentity = ambiguous)

    @Test fun absenceConcurrencyRemovalAndResetKeepMasterAndCooldowns() = withStore { prefs, _ ->
        assertTrue(prefs.getRecurringRemindersEnabled())
        assertFalse(prefs.getRecurringReminderSelections().present)
        prefs.setRecurringRemindersEnabled(false)
        coroutineScope {
            launch { prefs.setRecurringReminderOverride(rent, RecurringReminderOverride.DISABLED) }
            launch { prefs.setRecurringReminderOverride(netflix, RecurringReminderOverride.ENABLED) }
        }
        assertEquals(2, prefs.recurringReminderSelections.first().overrides.size)
        assertEquals(prefs.getRecurringReminderSelections(), prefs.recurringReminderSelections.first())
        val key = "recurring_remind_$rent"
        prefs.setLastRecurringNotifTime(key)
        prefs.setRecurringReminderOverride(rent, null)
        assertEquals(1, prefs.getRecurringReminderSelections().overrides.size)
        prefs.resetRecurringReminderOverrides()
        assertTrue(prefs.getRecurringReminderSelections().present)
        assertTrue(prefs.getRecurringReminderSelections().overrides.isEmpty())
        assertFalse(prefs.getRecurringRemindersEnabled())
        prefs.setRecurringRemindersEnabled(true)
        prefs.setRecurringReminderOverride(rent, RecurringReminderOverride.ENABLED)
        assertFalse(prefs.canSendRecurringNotification(key, 30))
    }

    @Test fun cooldownMigrationIdempotentAndIndependentByType() = withStore { prefs, _ ->
        prefs.setLastRecurringNotifTime("recurring_remind_Landlord")
        prefs.migrateRecurringCooldowns(listOf(payment()))
        assertFalse(prefs.canSendRecurringNotification("recurring_remind_$rent", 30))
        assertTrue(prefs.canSendRecurringNotification("recurring_overdue_$rent", 30))
        prefs.migrateRecurringCooldowns(listOf(payment()))
        assertFalse(prefs.canSendRecurringNotification("recurring_remind_$rent", 30))
    }

    @Test fun ambiguousMigrationAppliesFloorToSplitAccount() = withStore { prefs, _ ->
        prefs.setLastRecurringNotifTime("recurring_remind_Landlord", System.currentTimeMillis() - 90 * 86_400_000L)
        prefs.migrateRecurringCooldowns(listOf(payment(true)))
        assertFalse(prefs.canSendRecurringNotification("recurring_remind_$rent", 30))
    }

    @Test fun backupRestoresAtomicallyOldBackupResetsAndClearRetainsMaster() = withStore { prefs, store ->
        prefs.setRecurringRemindersEnabled(false)
        prefs.setRecurringReminderOverride(netflix, RecurringReminderOverride.ENABLED)
        val backup = prefs.getRecurringReminderBackup()
        prefs.resetRecurringReminderOverrides()
        prefs.restoreRecurringReminderSettings(RecurringReminderBackup.validate(backup.first.toString(), backup.second))
        assertEquals(RecurringReminderOverride.ENABLED, prefs.getRecurringReminderSelections().overrides[netflix])
        assertFalse(prefs.getRecurringRemindersEnabled())
        assertFalse(prefs.canSendRecurringNotification("recurring_remind_$netflix", 30))
        prefs.restoreRecurringReminderSettings(RecurringReminderBackup.validate(null, null))
        assertTrue(prefs.getRecurringReminderSelections().overrides.isEmpty())
        assertFalse(prefs.getRecurringRemindersEnabled())
        prefs.setLastRecurringNotifTime("recurring_remind_$rent")
        prefs.clearRecurringReminderData()
        assertFalse(prefs.getRecurringReminderSelections().present)
        assertFalse(prefs.getRecurringRemindersEnabled())
        assertTrue(store.data.first().asMap().keys.none { it.name.startsWith("recurring_notif_") || it.name == "recurring_restore_cooldown_floor" })
    }

    @Test fun accountChoicesAndCooldownsAreIndependentAndUiWorkerPolicyMatches() = withStore { prefs, _ ->
        val first = RecurringPaymentIdentity.create("PAY_BILL", "12345678", "Aggregator")
        val second = RecurringPaymentIdentity.create("PAY_BILL", "87654321", "Aggregator")
        prefs.setRecurringReminderOverride(first, RecurringReminderOverride.ENABLED)
        prefs.setRecurringReminderOverride(second, RecurringReminderOverride.DISABLED)
        prefs.setLastRecurringNotifTime("recurring_remind_$first")
        assertFalse(prefs.canSendRecurringNotification("recurring_remind_$first", 30))
        assertTrue(prefs.canSendRecurringNotification("recurring_remind_$second", 30))
        val enabled = payment().copy(recipientKey = first)
        val disabled = payment().copy(recipientKey = second)
        assertTrue(prefs.getRecurringReminderDecision(enabled).canDeliver)
        assertFalse(prefs.getRecurringReminderDecision(disabled).canDeliver)
        assertEquals(RecurringReminderPolicy.resolve(true, enabled, prefs.getRecurringReminderSelections()),
            prefs.getRecurringReminderDecision(enabled))
        prefs.setRecurringRemindersEnabled(false)
        assertTrue(prefs.getRecurringReminderDecision(enabled).selected)
        assertFalse(prefs.getRecurringReminderDecision(enabled).canDeliver)
        assertEquals(2, prefs.getRecurringReminderSelections().overrides.size)
    }

    @Test fun choicesPersistAfterDataStoreCloseAndDiskReopen() = runBlocking {
        val dir = Files.createTempDirectory("recurring-reopen-test").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = dir
        }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var store = PreferenceDataStoreFactory.create(scope = scope) { dir.resolve("test.preferences_pb") }
            val first = AppPreferences(context, store)
            first.setRecurringRemindersEnabled(false)
            first.setRecurringReminderOverride(netflix, RecurringReminderOverride.ENABLED)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = PreferenceDataStoreFactory.create(scope = scope) { dir.resolve("test.preferences_pb") }
            val reopened = AppPreferences(context, store)
            assertFalse(reopened.getRecurringRemindersEnabled())
            assertEquals(RecurringReminderOverride.ENABLED, reopened.getRecurringReminderSelections().overrides[netflix])
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun malformedMapDoesNotPartiallyApplyAndCanBeExplicitlyReset() = withStore { prefs, store ->
        store.edit { it[stringPreferencesKey("recurring_overrides_v1")] = "invalid" }
        assertFalse(prefs.getRecurringReminderSelections().valid)
        try { prefs.setRecurringReminderOverride(netflix, RecurringReminderOverride.ENABLED); fail("Expected validation failure") }
        catch (_: IllegalStateException) { }
        assertFalse(prefs.getRecurringReminderSelections().valid)
        prefs.resetRecurringReminderOverrides()
        assertTrue(prefs.getRecurringReminderSelections().valid)
    }
}