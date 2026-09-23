package com.pesatrack.services.ai

/**
 * Assigns opaque, request-scoped ids (`r1`, `r2`, ...) to recipient
 * aggregates before they are packaged into a [DataDigest] for the AI
 * backend, and provides the rehydration map that lets the client swap
 * those ids back for real names in the model's response — **on device,
 * never over the wire**.
 *
 * ---
 *
 * ## Why sequential ids, not hashes
 *
 * The rehydration ids are deliberately **not** derived from the merchant
 * name (no hash, no truncated fingerprint, no rolling counter tied to a
 * device identifier). Two consecutive digests for the same user can
 * reassign the same merchant a different id, and two users with the
 * same top merchant will use the same id `r1` — that's the point. The
 * server never sees anything that could correlate merchants across
 * users, sessions, or requests.
 *
 * Ordering is deterministic within a single call so that unit tests can
 * lock the assignment; it is **not** deterministic across calls with
 * different inputs.
 *
 * ## Ordering rules
 *
 * 1. Higher spend first (descending [RecipientAggregate.spent]).
 * 2. Ties broken by descending [RecipientAggregate.count].
 * 3. Further ties broken by ascending [RecipientAggregate.key] (case-
 *    insensitive) — stable, purely for determinism in tests.
 *
 * The caller is expected to have already collapsed duplicate keys and
 * capped the input to whatever the digest allows (the plan asks for
 * top 8 recipients).
 *
 * ---
 *
 * ## Rehydration
 *
 * [rehydrate] replaces occurrences of the pattern `rN` (integer) in an
 * arbitrary string with the corresponding real name from the map, using
 * a word-boundary regex. That means `r1` and `r10` are both replaced
 * independently — replacing `r1` with `"Java House"` will **not** mangle
 * `r10`. IDs not present in the map are left untouched so the caller
 * can decide whether to strip or flag them.
 *
 * See `plans/ai-pro-plan.md §7` for the privacy contract this class enforces
 * and `plans/ai-pro-phase2-spec.md §6.2` for how the rehydration hook is
 * consumed by the Coach Insight repository.
 */
class RecipientAnonymizer {

    /**
     * Aggregate figures for a single recipient, keyed by [key]
     * (COALESCE(recipientName, recipient) from the expenses table).
     *
     * The [displayName] field is what the client will later render to
     * the user; it never crosses the wire. The [key] and [displayName]
     * can be identical (they often are for merchants) or diverge (a
     * phone number with a saved contact name).
     */
    data class RecipientAggregate(
        /** Stable local key (COALESCE(recipientName, recipient)). Never sent. */
        val key: String,
        /** User-facing name for on-device rendering. Never sent. */
        val displayName: String,
        /** Whole KES spent this period. */
        val spent: Int,
        /** Transaction count this period. */
        val count: Int,
        /** Most-likely category id (from learned mappings). Nullable. */
        val categoryId: Long?,
        /** Three-month rolling monthly average, whole KES. */
        val threeMoAvg: Int,
    )

    /**
     * The result of anonymising a batch of recipient aggregates.
     *
     *  - [recipients] is safe to send: opaque `rN` ids, no names.
     *  - [rehydrationMap] MUST stay on device. It maps `rN → displayName`.
     */
    data class AnonymizedBatch(
        val recipients: List<DigestRecipient>,
        val rehydrationMap: Map<String, String>,
    ) {
        /** Convenience: apply [RecipientAnonymizer.rehydrate] using this batch's map. */
        fun rehydrate(source: String): String =
            RecipientAnonymizer.rehydrate(source, rehydrationMap)
    }

    /**
     * Assign `r1..rN` to [aggregates] and return the pair of wire-safe
     * digest rows and the on-device rehydration map.
     *
     * @param aggregates raw per-recipient rows. Order does **not**
     *   matter; this method sorts internally by [RecipientAggregate.spent]
     *   desc.
     */
    fun anonymize(aggregates: List<RecipientAggregate>): AnonymizedBatch {
        if (aggregates.isEmpty()) return AnonymizedBatch(emptyList(), emptyMap())

        val ordered = aggregates
            .sortedWith(
                compareByDescending<RecipientAggregate> { it.spent }
                    .thenByDescending { it.count }
                    .thenBy { it.key.lowercase() }
            )

        val recipients = ArrayList<DigestRecipient>(ordered.size)
        val map = LinkedHashMap<String, String>(ordered.size)

        for ((index, agg) in ordered.withIndex()) {
            val id = "r${index + 1}"
            recipients += DigestRecipient(
                id = id,
                spent = agg.spent,
                count = agg.count,
                categoryId = agg.categoryId,
                threeMoAvg = agg.threeMoAvg,
            )
            map[id] = agg.displayName
        }
        return AnonymizedBatch(recipients, map)
    }

    companion object {
        /**
         * Word-boundary regex that matches a bare `rN` token where `N`
         * is one or more digits. Kept as a compiled `Regex` so hot paths
         * don't re-compile.
         */
        private val R_ID_PATTERN = Regex("""\br(\d+)\b""")

        /**
         * Replace every `rN` occurrence in [source] with `map["rN"]`.
         * Unknown ids are left untouched so callers can decide what to
         * do about them (strip, log, fall back). Uses word boundaries so
         * `r1` and `r10` don't collide.
         */
        fun rehydrate(source: String, map: Map<String, String>): String {
            if (map.isEmpty()) return source
            return R_ID_PATTERN.replace(source) { match ->
                val token = match.value
                map[token] ?: token
            }
        }
    }
}
