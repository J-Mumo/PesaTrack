package com.pesatrack.services.ai

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of Ask Your Money's per-turn HTTP lifecycle on the
 * Android side.
 *
 * Public surface: one [ask] method that takes the user's question + the
 * prior in-memory history and returns a `Flow<AskStreamEvent>`. The
 * ViewModel collects and mutates its Compose state; the repository owns
 * digest construction, entitlement gating, and SSE plumbing but is
 * otherwise stateless.
 *
 * ## Design constraints (from plans/ai-pro-phase3-spec.md §7.3, §8.3)
 *
 *  - **No cache.** Chat turns are non-idempotent — the user's next
 *    question depends on this one's answer. Caching would strand
 *    replies on stale digests and defeat the purpose of Ask.
 *  - **Fresh digest per turn.** The user may have received a new SMS
 *    between turns; the digest builder re-reads Room every call.
 *  - **No persistence.** Nothing here writes to Room or DataStore.
 *    History is held in the ViewModel's `SnapshotStateList` and
 *    disappears on process death — that's the whole privacy story.
 *  - **Never throws.** Every failure mode terminates the returned Flow
 *    with [AskStreamEvent.Fallback]. The ViewModel decides how to
 *    render the fallback; it never sees an exception cross the wire.
 *  - **Entitlement short-circuit.** A non-Pro caller gets a Flow that
 *    emits `Fallback("not_entitled")` and completes — no HTTP call at
 *    all. In practice the FAB routes free users to the upsell screen
 *    before we ever get here, but defence in depth.
 *
 * ## History trim
 *
 * The ViewModel is expected to trim its ring buffer to 10 turns before
 * calling [ask]. We re-trim here as a safety net, matching the same
 * `slice(-10)` the backend Zod schema does — see spec §8.2 "history
 * capped at 10 turns server-side — extras trimmed silently".
 */
@Singleton
class AskYourMoneyRepository @Inject constructor(
    private val entitlement: EntitlementSource,
    private val digestBuilder: DigestBuilder,
    private val client: AskYourMoneyClient,
) {

    /**
     * Build a digest, open the SSE stream for one turn, and return the
     * event flow. See [AskStreamEvent] for the emission contract.
     *
     * @param question the user's typed question (must be non-blank —
     *   the ViewModel enforces this on the composer's send button, but
     *   we defensively short-circuit to fallback if empty)
     * @param history prior turns in chronological order (oldest first).
     *   Client-trimmed to the last 10 before the wire hits.
     * @param nowMs "now" used for digest period resolution. Injected
     *   for tests.
     */
    suspend fun ask(
        question: String,
        history: List<AskTurn>,
        nowMs: Long = System.currentTimeMillis(),
    ): Flow<AskStreamEvent> {
        Log.i(TAG, "ask(question.len=${question.length}, history.size=${history.size})")

        if (question.isBlank()) {
            Log.w(TAG, "ask: question is blank — synthetic fallback(reason=empty_question)")
            return flowOf(AskStreamEvent.Fallback(REASON_EMPTY_QUESTION))
        }

        val entitled = entitlement.isCurrentlyEntitled(nowMs)
        if (!entitled) {
            Log.w(TAG, "ask: entitlement.isCurrentlyEntitled=false — synthetic fallback(reason=not_entitled)")
            return flowOf(AskStreamEvent.Fallback(REASON_NOT_ENTITLED))
        }

        val build = try {
            digestBuilder.buildForCurrentPeriod(nowMs)
        } catch (t: Throwable) {
            // Digest build failed (DAO error, etc.). Don't crash the
            // ViewModel — synthesize a fallback event that terminates
            // the flow cleanly.
            Log.e(
                TAG,
                "ask: digestBuilder threw ${t.javaClass.simpleName}: ${t.message}",
                t,
            )
            return flowOf(AskStreamEvent.Fallback(REASON_DIGEST_ERROR))
        }
        Log.i(
            TAG,
            "ask: digest built OK — period=${build.digest.period}, recipients=${build.digest.topRecipientsThisPeriod.size}",
        )

        val trimmedHistory = if (history.size > MAX_HISTORY) history.takeLast(MAX_HISTORY) else history
        val request = AskRequestDto(
            digest = build.digest,
            history = trimmedHistory,
            question = question,
        )

        // Delegate the actual SSE plumbing to the client. Every wire /
        // parse / server failure lands as a Fallback event, so we can
        // return the raw flow without wrapping catch here.
        return client.stream(request)
            .onStart { Log.i(TAG, "ask: SSE stream opening") }
    }

    companion object {
        private const val TAG = "AskYourMoneyRepo"
        private const val MAX_HISTORY = 10

        /**
         * Synthetic reason codes emitted by this repository (never
         * originating from the server). Kept in sync with the client
         * telemetry PARAM_REASON enum in
         * plans/ai-pro-phase3-spec.md §11.
         */
        const val REASON_EMPTY_QUESTION = "empty_question"
        const val REASON_NOT_ENTITLED = "not_entitled"
        const val REASON_DIGEST_ERROR = "digest_error"
    }
}
