package com.pesatrack.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [CoachInsight] domain type — specifically the
 * [CoachInsight.withRehydratedRecipients] behaviour, which is the
 * on-device counterpart to the wire-format `r1..rN` anonymisation that
 * [RecipientAnonymizer] performs at digest-build time.
 *
 * These tests lock two invariants together:
 *  1. Rehydration happens *only* in [CoachInsight.title] and
 *     [CoachInsight.body]. Fields carrying structured data
 *     ([CoachInsight.actionDeeplink], [CoachInsight.referencedRecipientIds])
 *     stay untouched — the deep-link routing and any recipient-detail
 *     screen wiring reads the raw ids.
 *  2. Empty-map is a strict no-op — returns the same instance unchanged.
 */
class CoachInsightTest {

    private val sample = CoachInsight(
        title = "You spent KES 8,400 at r1 this week",
        body = "That is KES 2,600 above your usual pace at r1. Reducing to KES 500 per visit would free ~KES 2,000 for savings.",
        actionLabel = "See r1",
        actionDeeplink = "pesatrack://recipient/r1",
        saveableAmountKes = 2_000,
        assumptions = listOf("Assumes 4 visits per week"),
        referencedRecipientIds = listOf("r1"),
    )

    @Test
    fun `empty map is a no-op`() {
        val out = sample.withRehydratedRecipients(emptyMap())
        assertEquals(sample, out)
    }

    @Test
    fun `title and body get rehydrated`() {
        val out = sample.withRehydratedRecipients(mapOf("r1" to "Java House"))
        assertEquals("You spent KES 8,400 at Java House this week", out.title)
        assertTrue(out.body.contains("Java House"))
        assertTrue(!out.body.contains(" r1"))
    }

    @Test
    fun `deep link and referenced ids stay in raw form`() {
        val out = sample.withRehydratedRecipients(mapOf("r1" to "Java House"))
        // The deep-link routing and any recipient-detail screen still
        // key off the raw id. Rehydrating those would break navigation.
        assertEquals("pesatrack://recipient/r1", out.actionDeeplink)
        assertEquals(listOf("r1"), out.referencedRecipientIds)
    }

    @Test
    fun `unknown ids are left untouched by rehydrate`() {
        val out = sample.withRehydratedRecipients(mapOf("r99" to "Some Other Merchant"))
        // r1 not in the map ⇒ title still contains r1.
        assertTrue(out.title.contains("r1"))
    }

    @Test
    fun `r1 and r10 do not collide`() {
        val insight = sample.copy(
            title = "Compare r1 with r10",
            body = "r1 spent KES 1,000. r10 spent KES 500. Together: KES 1,500.",
        )
        val out = insight.withRehydratedRecipients(mapOf("r1" to "Java House"))
        assertEquals("Compare Java House with r10", out.title)
        assertTrue(out.body.contains("Java House"))
        assertTrue(out.body.contains(" r10"))
    }

    @Test
    fun `nullable optional fields survive rehydration`() {
        val no_action = sample.copy(actionLabel = null, actionDeeplink = null, saveableAmountKes = null)
        val out = no_action.withRehydratedRecipients(mapOf("r1" to "Java House"))
        assertNull(out.actionLabel)
        assertNull(out.actionDeeplink)
        assertNull(out.saveableAmountKes)
    }

    @Test
    fun `title and body are the only fields changed`() {
        val out = sample.withRehydratedRecipients(mapOf("r1" to "Java House"))
        assertNotEquals(sample.title, out.title)
        assertNotEquals(sample.body, out.body)
        assertEquals(sample.actionLabel, out.actionLabel)
        assertEquals(sample.actionDeeplink, out.actionDeeplink)
        assertEquals(sample.saveableAmountKes, out.saveableAmountKes)
        assertEquals(sample.assumptions, out.assumptions)
        assertEquals(sample.referencedRecipientIds, out.referencedRecipientIds)
    }
}
