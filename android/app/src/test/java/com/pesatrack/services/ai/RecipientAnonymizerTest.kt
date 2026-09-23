package com.pesatrack.services.ai

import com.pesatrack.services.ai.RecipientAnonymizer.RecipientAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the privacy-critical [RecipientAnonymizer].
 *
 * This file is the source of truth for two invariants that CANNOT
 * regress without leaking merchant identifiers to the AI backend:
 *
 *  1. The wire-side [DigestRecipient] never carries the original key
 *     or display name — only the opaque `rN` id.
 *  2. Two consecutive calls with the same input produce byte-identical
 *     output (deterministic ordering + id assignment), so we can lock
 *     the assignment in unit tests. This is deterministic *within a
 *     call*; it is NOT stable across calls with different inputs — see
 *     the class-level docs on `RecipientAnonymizer` for why that's the
 *     desired property.
 */
class RecipientAnonymizerTest {

    private val anon = RecipientAnonymizer()

    // ── Ordering & id assignment ──────────────────────────────────────────

    @Test
    fun `empty input produces empty batch`() {
        val batch = anon.anonymize(emptyList())
        assertTrue(batch.recipients.isEmpty())
        assertTrue(batch.rehydrationMap.isEmpty())
    }

    @Test
    fun `single recipient becomes r1 with all fields preserved`() {
        val agg = RecipientAggregate(
            key = "0712345678",
            displayName = "Java House",
            spent = 8_400,
            count = 12,
            categoryId = 7L,
            threeMoAvg = 3_200,
        )
        val batch = anon.anonymize(listOf(agg))

        assertEquals(1, batch.recipients.size)
        val r1 = batch.recipients.single()
        assertEquals("r1", r1.id)
        assertEquals(8_400, r1.spent)
        assertEquals(12, r1.count)
        assertEquals(7L, r1.categoryId)
        assertEquals(3_200, r1.threeMoAvg)

        assertEquals(mapOf("r1" to "Java House"), batch.rehydrationMap)
    }

    @Test
    fun `recipients are sorted by spend descending`() {
        val batch = anon.anonymize(
            listOf(
                aggregate(key = "small", displayName = "Small Corp", spent = 100),
                aggregate(key = "big", displayName = "Big Corp", spent = 10_000),
                aggregate(key = "medium", displayName = "Medium Corp", spent = 500),
            )
        )
        assertEquals(listOf("r1", "r2", "r3"), batch.recipients.map { it.id })
        assertEquals(listOf(10_000, 500, 100), batch.recipients.map { it.spent })
        assertEquals("Big Corp", batch.rehydrationMap["r1"])
        assertEquals("Medium Corp", batch.rehydrationMap["r2"])
        assertEquals("Small Corp", batch.rehydrationMap["r3"])
    }

    @Test
    fun `spend ties are broken by transaction count desc`() {
        val batch = anon.anonymize(
            listOf(
                aggregate(key = "a", displayName = "A", spent = 5_000, count = 2),
                aggregate(key = "b", displayName = "B", spent = 5_000, count = 8),
            )
        )
        // B has more transactions → wins r1 despite equal spend
        assertEquals("B", batch.rehydrationMap["r1"])
        assertEquals("A", batch.rehydrationMap["r2"])
    }

    @Test
    fun `spend and count ties are broken by key case-insensitive asc`() {
        val batch = anon.anonymize(
            listOf(
                aggregate(key = "Zeta", displayName = "Zeta", spent = 1_000, count = 3),
                aggregate(key = "alpha", displayName = "Alpha", spent = 1_000, count = 3),
                aggregate(key = "Beta", displayName = "Beta", spent = 1_000, count = 3),
            )
        )
        // 'alpha' < 'Beta' < 'Zeta' when lowercased
        assertEquals("Alpha", batch.rehydrationMap["r1"])
        assertEquals("Beta", batch.rehydrationMap["r2"])
        assertEquals("Zeta", batch.rehydrationMap["r3"])
    }

    @Test
    fun `same input two calls produces identical output`() {
        val input = listOf(
            aggregate(key = "keyC", displayName = "Charlie", spent = 500),
            aggregate(key = "keyA", displayName = "Alpha", spent = 1_500),
            aggregate(key = "keyB", displayName = "Bravo", spent = 750),
        )
        val first = anon.anonymize(input)
        val second = anon.anonymize(input)
        assertEquals(first.recipients, second.recipients)
        assertEquals(first.rehydrationMap, second.rehydrationMap)
    }

    // ── Privacy invariant ─────────────────────────────────────────────────

    @Test
    fun `DigestRecipient carries no key or display name`() {
        val agg = RecipientAggregate(
            key = "0712345678",
            displayName = "Very Distinctive Merchant Name",
            spent = 500,
            count = 1,
            categoryId = null,
            threeMoAvg = 0,
        )
        val batch = anon.anonymize(listOf(agg))
        val serialized = batch.recipients.single().toString()

        assertTrue(
            "DigestRecipient toString must not contain the recipient key: $serialized",
            !serialized.contains("0712345678"),
        )
        assertTrue(
            "DigestRecipient toString must not contain the display name: $serialized",
            !serialized.contains("Very Distinctive"),
        )
    }

    @Test
    fun `zero recipients returns empty rehydration map`() {
        assertTrue(anon.anonymize(emptyList()).rehydrationMap.isEmpty())
    }

    @Test
    fun `null categoryId is preserved`() {
        val batch = anon.anonymize(
            listOf(aggregate(key = "k", displayName = "n", spent = 100, categoryId = null))
        )
        assertNull(batch.recipients.single().categoryId)
    }

    // ── Rehydration ──────────────────────────────────────────────────────

    @Test
    fun `rehydrate replaces known ids with display names`() {
        val map = mapOf("r1" to "Java House", "r2" to "Naivas Supermarket")
        val out = RecipientAnonymizer.rehydrate(
            "You spent KES 8,400 at r1 and KES 4,200 at r2 this week.",
            map,
        )
        assertEquals(
            "You spent KES 8,400 at Java House and KES 4,200 at Naivas Supermarket this week.",
            out,
        )
    }

    @Test
    fun `rehydrate does not collide between r1 and r10`() {
        val map = mapOf("r1" to "Java House")
        // Only r1 is in the map; r10 must survive verbatim (not become "Java House0").
        val out = RecipientAnonymizer.rehydrate("Compare r1 with r10 here.", map)
        assertEquals("Compare Java House with r10 here.", out)
    }

    @Test
    fun `rehydrate does not touch tokens that are not word-boundary r-ids`() {
        val map = mapOf("r1" to "Java House")
        // Only bare "r1" gets replaced. "abr1" and "r1a" and "R1" don't match.
        val out = RecipientAnonymizer.rehydrate(
            "abr1 and r1a and R1 and r1 are different tokens.",
            map,
        )
        assertEquals(
            "abr1 and r1a and R1 and Java House are different tokens.",
            out,
        )
    }

    @Test
    fun `rehydrate leaves unknown ids untouched`() {
        val map = mapOf("r1" to "Java House")
        val out = RecipientAnonymizer.rehydrate("r1 and r7 both appear.", map)
        assertEquals("Java House and r7 both appear.", out)
    }

    @Test
    fun `rehydrate with empty map returns source unchanged`() {
        val src = "This mentions r1 and r2 but the map is empty."
        assertEquals(src, RecipientAnonymizer.rehydrate(src, emptyMap()))
    }

    @Test
    fun `batch rehydrate convenience method matches static rehydrate`() {
        val batch = anon.anonymize(
            listOf(aggregate(key = "a", displayName = "Alpha", spent = 1_000))
        )
        val text = "at r1 today"
        assertEquals(
            RecipientAnonymizer.rehydrate(text, batch.rehydrationMap),
            batch.rehydrate(text),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun aggregate(
        key: String,
        displayName: String,
        spent: Int,
        count: Int = 1,
        categoryId: Long? = null,
        threeMoAvg: Int = 0,
    ) = RecipientAggregate(
        key = key,
        displayName = displayName,
        spent = spent,
        count = count,
        categoryId = categoryId,
        threeMoAvg = threeMoAvg,
    )
}
