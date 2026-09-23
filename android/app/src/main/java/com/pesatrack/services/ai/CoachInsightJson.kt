package com.pesatrack.services.ai

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/**
 * DataStore-persisted envelope wrapping a [CoachInsight] with the local
 * date it was fetched on. The date lets [CoachInsightRepository]
 * distinguish "fresh for today" from "kept as yesterday's fallback"
 * without any wall-clock storage of its own.
 *
 * [date] is stored as an ISO-8601 `YYYY-MM-DD` string so it round-trips
 * cleanly through Moshi without a custom adapter.
 */
@JsonClass(generateAdapter = true)
data class CachedCoachInsight(
    /** ISO-8601 local date `YYYY-MM-DD` this insight was persisted for. */
    val date: String,
    /** The insight itself (already recipient-rehydrated before caching). */
    val insight: CoachInsight,
)

/**
 * JSON codec for [CachedCoachInsight] persisted under DataStore key
 * `coach_insight_cache_v1` (see `AppPreferences.KEY_COACH_INSIGHT`).
 *
 * Mirrors the [com.pesatrack.services.pro.ProStateJson] pattern:
 *  - Kept out of `AppPreferences` so the encoding is trivially
 *    unit-testable on the JVM with no Android scaffolding.
 *  - Moshi codegen (`@JsonClass(generateAdapter = true)` + KSP) — no
 *    reflection, R8-safe without consumer rules.
 *  - `parse` returns `null` on any failure (malformed JSON, unknown
 *    field types, forward-schema surprises). Callers substitute "cache
 *    miss" — a corrupted blob must never wedge the app on cold start.
 *
 * Rehydration of recipient ids happens **before** persistence in
 * [CoachInsightRepository], so the cached insight already has real
 * merchant names — the id → name map is never persisted.
 */
object CoachInsightJson {

    private val moshi: Moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(CachedCoachInsight::class.java)

    /** Serialize to the stable v1 JSON wire format. */
    fun serialize(cached: CachedCoachInsight): String = adapter.toJson(cached)

    /**
     * Parse a v1 JSON string. Returns `null` on any parse failure so a
     * bad blob translates to a clean cache miss and a fresh fetch.
     */
    fun parse(json: String): CachedCoachInsight? =
        runCatching { adapter.fromJson(json) }.getOrNull()
}
