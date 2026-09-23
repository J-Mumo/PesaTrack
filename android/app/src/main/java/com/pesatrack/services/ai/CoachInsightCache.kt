package com.pesatrack.services.ai

import java.time.LocalDate

/**
 * Thin persistence contract for the daily Coach Insight cache.
 *
 * The client holds **one** cached insight at a time — the most recent
 * successful fetch, tagged with the local date it was fetched on.
 * That single slot is enough to serve two distinct use cases per
 * `plans/ai-pro-phase2-spec.md §6.4`:
 *
 *  1. **Cache hit for today**: if a fresh fetch produced an insight for
 *     the current `LocalDate`, [getIfFreshForToday] returns it and the
 *     repository skips the network round-trip.
 *  2. **Stale fallback for yesterday**: if today's fetch fails
 *     (provider error, network, deny-list, etc.), [getYesterday] hands
 *     back the previous day's insight so the Home card still renders
 *     something useful while the client waits until tomorrow to try
 *     again. Two-day-old (or older) cache is treated as absent — the
 *     principle is "yesterday's advice is stale but still relevant;
 *     older than that is misleading".
 *
 * Implementations MUST be tolerant of corrupted persistence: any
 * failure to decode returns `null` for both reads, and callers treat
 * that as a clean cache miss. See [CoachInsightJson.parse].
 *
 * See plans/ai-pro-phase2-spec.md §6.4.
 */
interface CoachInsightCache {

    /**
     * Return the cached insight if it was persisted on exactly [today].
     * `null` otherwise — including "cache holds yesterday's" and
     * "cache is empty".
     */
    suspend fun getIfFreshForToday(today: LocalDate): CoachInsight?

    /**
     * Return the cached insight if it was persisted on
     * `today.minusDays(1)`. `null` otherwise — including today's own
     * entry (that would be a fresh hit, not a fallback) and
     * two-or-more-days-old entries.
     */
    suspend fun getYesterday(today: LocalDate): CoachInsight?

    /**
     * Overwrite the single cache slot with [insight] tagged for
     * [today]. Any older entry is dropped in the same write.
     */
    suspend fun putForToday(today: LocalDate, insight: CoachInsight)

    /** Drop the cache entirely — used on Pro-entitlement loss. */
    suspend fun clear()
}
