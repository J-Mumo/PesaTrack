package com.pesatrack.presentation.navigation

import org.junit.Assert.*
import org.junit.Test

class NotificationNavigationTest {
    @Test fun coldWarmAndLockedDeliveriesRetainExactExpenseUntilAllGatesAreReady() {
        val request = NotificationNavigation(1, "categorize", expenseId = 4_294_967_303L)
        val gates = listOf(
            listOf(false, true, false, true, true), // onboarding
            listOf(true, false, false, true, true), // PIN still loading
            listOf(true, true, true, true, true), // locked
            listOf(true, true, false, false, true), // activity not resumed
            listOf(true, true, false, true, false) // NavHost not installed
        )
        for (gate in gates) {
            assertNull(request.routeWhenReady(gate[0], gate[1], gate[2], gate[3], gate[4]))
            assertEquals(4_294_967_303L, request.expenseId)
        }
        assertEquals("categorize/4294967303", request.routeWhenReady(true, true, false, true, true))
        assertNull(request.acknowledge(request))
    }

    @Test fun consecutiveSameTargetAndRepeatedSameExpenseAreDifferentDeliveries() {
        val first = NotificationNavigation(1, "categorize", expenseId = 11)
        val second = NotificationNavigation(2, "categorize", expenseId = 22)
        val repeated = NotificationNavigation(3, "categorize", expenseId = 22)
        assertNotEquals(first, second)
        assertNotEquals(second, repeated)
        assertEquals("categorize/11", first.route())
        assertEquals("categorize/22", second.route())
        assertEquals(second, second.acknowledge(first))
        assertEquals(repeated, repeated.acknowledge(second))
        assertNull(repeated.acknowledge(repeated))
    }

    @Test fun missingOrInvalidIdsNeverOpenAnOldExpenseOrIncome() {
        for (id in listOf(null, -1L, 0L)) {
            assertNull(NotificationNavigation(1, "categorize", expenseId = id).route())
            assertNull(NotificationNavigation(1, "categorize_income", incomeId = id).route())
        }
        assertNull(NotificationNavigation(1, "ignore", expenseId = 11).route())
        assertNull(NotificationNavigation(1, "unknown", expenseId = 11).route())
    }

    @Test fun existingIncomeBudgetAndReportRoutesKeepTheirOwnArguments() {
        assertEquals("categorize_income/22", NotificationNavigation(1, "categorize_income", incomeId = 22).route())
        assertEquals("budget", NotificationNavigation(2, "budget").route())
        assertEquals("weekly_review?snapshotId=33", NotificationNavigation(3, "weekly_review", snapshotId = 33).route())
        assertEquals("monthly_review", NotificationNavigation(4, "monthly_review").route())
        assertEquals("quarterly_review?snapshotId=44", NotificationNavigation(5, "quarterly_review", snapshotId = 44).route())
        assertEquals("year_in_review?year=2025", NotificationNavigation(6, "year_in_review", year = 2025).route())
    }
}