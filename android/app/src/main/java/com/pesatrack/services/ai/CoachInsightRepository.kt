package com.pesatrack.services.ai

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Narrow entitlement-check surface used by [CoachInsightRepository].
 *
 * Extracted so tests can supply a one-line fake without constructing a
 * real [ProEntitlementRepository] (which itself needs a token cache,
 * telemetry client, HTTP client, and a persistent state store). The
 * concrete production impl is
 * [com.pesatrack.services.pro.ProEntitlementRepository] via a Hilt
 * `@Binds`.
 *
 * Plain `interface` rather than `fun interface` because we want the
 * default parameter value on [isCurrentlyEntitled] — SAM interfaces
 * disallow those.
 */
interface EntitlementSource {
    /** True when a currently valid Pro entitlement exists. */
    suspend fun isCurrentlyEntitled(nowMs: Long = System.currentTimeMillis()): Boolean
}

/**
 * The single owner of AI Coach Insight lifecycle on the Android side.
 *
 * Public surface is deliberately narrow — one nullable-returning method,
 * [getForToday]. `null` is the "render the existing template card"
 * signal. The repository never surfaces error states to the ViewModel;
 * every fallback branch (not entitled, cache hit, network error,
 * backend guardrail rejection, ...) simply resolves to either a fresh
 * [CoachInsight] or `null`.
 *
 * ---
 *
 * ## Flow
 *
 * ```
 *  getForToday()
 *     │
 *     ├── !isCurrentlyEntitled ─────────────► null   (free user)
 *     │
 *     ├── cache.getIfFreshForToday(today) ──► CoachInsight   (cache hit)
 *     │
 *     ├── digest = digestBuilder.buildForCurrentPeriod(nowMs)
 *     │   POST /ai/coach-insight
 *     │      │
 *     │      ├── HTTP 2xx, fallback == false, insight != null
 *     │      │     │
 *     │      │     ├── withRehydratedRecipients(map)
 *     │      │     ├── cache.putForToday(today, hydrated)
 *     │      │     └── ► hydrated
 *     │      │
 *     │      ├── HTTP 2xx, fallback == true         ─► cache.getYesterday(today)
 *     │      ├── HTTP 4xx (401/429/other client bug) ─► cache.getYesterday(today)
 *     │      ├── HTTP 5xx                            ─► cache.getYesterday(today)
 *     │      └── IOException                        ─► cache.getYesterday(today)
 * ```
 *
 * The "yesterday fallback" branch is the entire reason the cache stores
 * a `date` alongside the insight — a stale day-old insight is honest
 * enough to render while the client waits until tomorrow to retry, but
 * an older one gets treated as absent and returns `null` so the
 * template card takes over.
 *
 * ---
 *
 * ## What this repository does NOT do
 *
 *  - Check the `pro_ai_enabled` ship-gate flag. That gate lives at the
 *    call site ([com.pesatrack.presentation.screens.home.HomeViewModel]
 *    in Slice B4) so tests here don't need a fake DataStore. Repository
 *    stays responsible only for entitlement + cache + network.
 *  - Retry on failure. One request per user per day (with the client
 *    cache) is the whole rate story. Retries would only add cost and
 *    could double-charge OpenAI for the same digest hash.
 *  - Fetch pre-emptively on cold start. `HomeViewModel` calls
 *    [getForToday] once per Home surface — B4 will wire that.
 *  - Surface bucketed reasons to the caller. Reasons are logged
 *    server-side; the client side gets a boolean "did we get an
 *    insight or not". Telemetry lands in B5.
 *  - Own the deep-link routing. The composable in B4 parses
 *    [CoachInsight.actionDeeplink] and calls the existing NavGraph.
 *
 * See plans/ai-pro-phase2-spec.md §6.3 (repository) and §8 (fallback
 * matrix).
 */
@Singleton
class CoachInsightRepository @Inject constructor(
    private val entitlement: EntitlementSource,
    private val digestBuilder: DigestBuilder,
    private val client: PesaTrackAiClient,
    private val cache: CoachInsightCache,
) {

    /**
     * Fetch (or return the cached) Coach Insight for today. Never
     * throws — every failure mode collapses into a nullable return so
     * the caller renders unchanged.
     *
     * @param nowMs the "now" instant used for both period resolution
     *   (via [DigestBuilder.buildForCurrentPeriod]) and local-date
     *   derivation for the client cache. Injectable so tests can lock
     *   date rollover without touching the system clock.
     * @param zoneId the timezone used to convert [nowMs] to a
     *   [LocalDate] for the cache-slot key. Defaults to the device's
     *   default zone; tests use a fixed zone for determinism.
     */
    suspend fun getForToday(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): CoachInsight? {
        // Entitlement gate. Honest-numbers check — expired persisted state
        // resolves to false even if `isEntitled = true` is still on disk.
        if (!entitlement.isCurrentlyEntitled(nowMs)) return null

        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()

        // Cache hit — skip the network and OpenAI cost entirely.
        cache.getIfFreshForToday(today)?.let { return it }

        // Fresh fetch: build the digest, post it, rehydrate on success.
        val build = try {
            digestBuilder.buildForCurrentPeriod(nowMs)
        } catch (_: Throwable) {
            // Digest build failed (DAO error, etc.). Fall back rather
            // than crash the ViewModel.
            return cache.getYesterday(today)
        }

        val response = try {
            client.coachInsight(CoachInsightRequestDto(digest = build.digest))
        } catch (_: Throwable) {
            // Network / serialization / timeout — every wire-level
            // failure lands here.
            return cache.getYesterday(today)
        }

        if (!response.isSuccessful) {
            return cache.getYesterday(today)
        }

        val body = response.body()
        val fresh = body?.insight
        if (body == null || body.fallback || fresh == null) {
            return cache.getYesterday(today)
        }

        val hydrated = fresh.withRehydratedRecipients(build.rehydrationMap)
        cache.putForToday(today, hydrated)
        return hydrated
    }
}
