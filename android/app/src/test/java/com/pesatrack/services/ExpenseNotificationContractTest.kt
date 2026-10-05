package com.pesatrack.services

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExpenseNotificationContractTest {
    @Test fun fullLongIdsKindsAndRolesHaveDistinctPendingIntentIdentities() {
        // The old toInt()/offset request codes collide for these IDs.
        val ids = listOf(7L, 500_007L, 600_007L, 800_007L, 7L + (1L shl 32), Long.MAX_VALUE)
        val identities = ids.flatMap { id ->
            listOf("new", "misc").flatMap { kind ->
                listOf("body", "categorize", "ignore", "undo").map { role ->
                    ExpenseNotificationContract.identity(id, kind, role)
                }
            }
        }
        assertEquals(48, identities.toSet().size)
        assertEquals(12, ids.flatMap { id -> listOf("new", "misc").map {
            ExpenseNotificationContract.tag(id, it)
        } }.toSet().size)
        assertEquals(ExpenseNotificationContract.identity(7, "new", "body"),
            ExpenseNotificationContract.identity(7, "new", "body"))
    }

    @Test fun onlyMatchingBodyAndCategorizeCanCancelTheirOwnNotification() {
        for (kind in listOf("new", "misc")) {
            for (role in listOf("body", "categorize")) {
                val data = ExpenseNotificationContract.identity(42, kind, role)
                assertTrue(ExpenseNotificationContract.isOpen(ExpenseNotificationContract.ACTION_OPEN, data, 42, kind))
                assertFalse(ExpenseNotificationContract.isOpen(ExpenseNotificationContract.ACTION_OPEN, data, 43, kind))
                assertFalse(ExpenseNotificationContract.isOpen(ExpenseNotificationContract.ACTION_IGNORE, data, 42, kind))
            }
        }
        for (role in listOf("ignore", "undo")) {
            assertFalse(ExpenseNotificationContract.isOpen(ExpenseNotificationContract.ACTION_OPEN,
                ExpenseNotificationContract.identity(42, "new", role), 42, "new"))
        }
        assertFalse(ExpenseNotificationContract.isOpen(ExpenseNotificationContract.ACTION_OPEN, null, -1, null))
        assertFalse(ExpenseNotificationContract.isOpen(ExpenseNotificationContract.ACTION_OPEN, null, 42, "budget"))
    }

    @Test fun ignoreCommitsOnlyTheSelectedExpenseAndOnlyOnce() = runBlocking {
        val pending = PendingExpenseIgnores()
        val writes = mutableListOf<Pair<Long, Boolean>>()
        val first = pending.begin(11)
        val second = pending.begin(22)
        assertTrue(pending.commit(11, first) { id, excluded -> writes += id to excluded })
        assertFalse(pending.commit(11, first) { id, excluded -> writes += id to excluded })
        assertTrue(pending.hasPending(22))
        assertTrue(pending.commit(22, second) { id, excluded -> writes += id to excluded })
        assertEquals(listOf(11L to true, 22L to true), writes)
    }

    @Test fun undoAndSupersededTimerNeverWriteToRepository() = runBlocking {
        val pending = PendingExpenseIgnores()
        val first = pending.begin(11)
        pending.undo(11)
        assertFalse(pending.commit(11, first) { _, _ -> fail("Undo must prevent exclusion") })
        val old = pending.begin(11)
        val latest = pending.begin(11)
        assertFalse(pending.commit(11, old) { _, _ -> fail("Old timer must not commit") })
        assertTrue(pending.hasPending(11))
        var writtenId = 0L
        assertTrue(pending.commit(11, latest) { id, excluded -> writtenId = id; assertTrue(excluded) })
        assertEquals(11L, writtenId)
    }
}