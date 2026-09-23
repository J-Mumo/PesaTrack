package com.pesatrack.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CoachInsightJson] — the DataStore blob codec for the
 * daily insight cache.
 *
 * Mirrors the coverage of Slice A2's [com.pesatrack.services.pro.ProStateJsonTest]:
 * a full round-trip lock (so a rename regression on any field breaks
 * one specific assert), plus corruption tolerance (malformed / garbage
 * strings return null so a bad blob can't wedge cold start).
 */
class CoachInsightJsonTest {

    private val fullInsight = CoachInsight(
        title = "You spent KES 8,400 on Food this week",
        body = "That is KES 2,600 above your typical pace. Reducing takeout would leave KES 2,000 for savings.",
        actionLabel = "See Food breakdown",
        actionDeeplink = "pesatrack://category/7",
        saveableAmountKes = 2_000,
        assumptions = listOf("Assumes Food spending stays flat", "Assumes no new recurring bills"),
        referencedRecipientIds = listOf("r1", "r2"),
    )

    private val fullEntry = CachedCoachInsight(date = "2026-09-23", insight = fullInsight)

    @Test
    fun `round-trip preserves every field`() {
        val json = CoachInsightJson.serialize(fullEntry)
        val parsed = CoachInsightJson.parse(json)
        assertNotNull(parsed)
        assertEquals(fullEntry, parsed)
    }

    @Test
    fun `nullable optional fields survive a round-trip`() {
        val minimal = fullEntry.copy(
            insight = fullInsight.copy(
                actionLabel = null,
                actionDeeplink = null,
                saveableAmountKes = null,
                assumptions = emptyList(),
                referencedRecipientIds = emptyList(),
            )
        )
        val json = CoachInsightJson.serialize(minimal)
        val parsed = CoachInsightJson.parse(json)
        assertEquals(minimal, parsed)
    }

    @Test
    fun `wire-format field names are snake_case (drift guard)`() {
        // If any @Json(name = "…") is dropped or renamed, this asserts
        // break immediately — that would silently corrupt on-disk cache
        // reads after an update.
        val json = CoachInsightJson.serialize(fullEntry)
        assertTrue(json.contains("\"action_label\""))
        assertTrue(json.contains("\"action_deeplink\""))
        assertTrue(json.contains("\"saveable_amount_kes\""))
        assertTrue(json.contains("\"referenced_recipient_ids\""))
        // Kotlin camelCase names must NOT appear on the wire.
        assertTrue(!json.contains("\"actionLabel\""))
        assertTrue(!json.contains("\"actionDeeplink\""))
        assertTrue(!json.contains("\"saveableAmountKes\""))
        assertTrue(!json.contains("\"referencedRecipientIds\""))
    }

    @Test
    fun `malformed JSON returns null (never crashes cold start)`() {
        assertNull(CoachInsightJson.parse(""))
        assertNull(CoachInsightJson.parse("{"))
        assertNull(CoachInsightJson.parse("garbage"))
        assertNull(CoachInsightJson.parse("[]")) // wrong root type
    }

    @Test
    fun `missing required field returns null`() {
        // Insight body missing the required `title` field.
        val badInsight = """{"date":"2026-09-23","insight":{"body":"something","action_label":null,"action_deeplink":null,"saveable_amount_kes":null,"assumptions":[],"referenced_recipient_ids":[]}}"""
        assertNull(CoachInsightJson.parse(badInsight))
    }
}
