package com.pesatrack.services

import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.RecurrenceCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class RecurringExpenseServiceTest {
    private fun service(rows: List<RecurrenceCandidate>): RecurringExpenseService {
        val expenses = Proxy.newProxyInstance(ExpenseDao::class.java.classLoader, arrayOf(ExpenseDao::class.java)) { _, method, _ ->
            when (method.name) { "getExpensesForRecurrenceDetection" -> rows; else -> error("Unexpected DAO call ${method.name}") }
        } as ExpenseDao
        val categories = Proxy.newProxyInstance(CategoryDao::class.java.classLoader, arrayOf(CategoryDao::class.java)) { _, _, _ -> emptyList<Any>() } as CategoryDao
        return RecurringExpenseService(expenses, categories)
    }

    private fun rows(account: String, category: Long?, count: Int = 3) = (0 until count).map {
        RecurrenceCandidate("Aggregator", account, "Aggregator", "PAY_BILL", category, 1000.0,
            System.currentTimeMillis() - (count - it) * 7 * 86_400_000L)
    }

    @Test fun separateAccountsAndRemoveFeesBeforeAnalysis() = runBlocking {
        val base = rows("1234", 1002L) + rows("5678", 999L)
        val fees = rows("1234", 606L).map { it.copy(amount = 20.0, timestamp = it.timestamp + 1) }
        val summary = service((base + fees).reversed()).getRecurringExpenses(true)
        assertEquals(2, summary.recurringExpenses.size)
        assertEquals(2, summary.recurringExpenses.map { it.recipientKey }.toSet().size)
        summary.recurringExpenses.forEach {
            assertEquals(3, it.occurrenceCount); assertEquals(1000.0, it.averageAmount, 0.001)
            assertEquals(1.0, it.confidence, 0.001); assertEquals(1.0, it.categorySupport, 0.001)
            assertTrue(it.ambiguousLegacyIdentity)
        }
        assertTrue(service(fees).getRecurringExpenses(true).recurringExpenses.isEmpty())
    }

    @Test fun uncategorizedOccurrencesCountAgainstDominantCategory() = runBlocking {
        val input = rows("1234", 1009L, 10).mapIndexed { i, row -> row.copy(categoryId = if (i < 6) 1009L else null) }
        val payment = service(input).getRecurringExpenses(true).recurringExpenses.single()
        assertEquals(0.6, payment.categorySupport, 0.001)
        assertFalse(RecurringReminderPolicy.resolve(true, payment, RecurringReminderSelections()).selected)
        assertTrue(RecurringReminderPolicy.resolve(true, payment, RecurringReminderSelections(
            mapOf(payment.recipientKey to RecurringReminderOverride.ENABLED))).canDeliver)
    }

    @Test fun recategorizationDoesNotChangeIdentityOrExplicitChoice() = runBlocking {
        val first = service(rows("1234", 1009L)).getRecurringExpenses(true).recurringExpenses.single()
        val edited = service(rows("1234", 999L)).getRecurringExpenses(true).recurringExpenses.single()
        assertEquals(first.recipientKey, edited.recipientKey)
        assertTrue(RecurringReminderPolicy.resolve(true, edited,
            RecurringReminderSelections(mapOf(first.recipientKey to RecurringReminderOverride.ENABLED))).selected)
    }
}