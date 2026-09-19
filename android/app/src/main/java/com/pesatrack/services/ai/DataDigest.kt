package com.pesatrack.services.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Privacy-safe monthly financial digest that the client sends to
 * `pesatrack-api.jmumo.com` when requesting AI features.
 *
 * **Slice A3 (this file) intentionally ships a minimal stub.** Only
 * [period] is defined — the backend `/ai/echo` handler currently only
 * requires `period` (min 4, max 20 chars) and treats every other field as
 * `passthrough` for Phase-1 wiring tests. The full schema (top recipients,
 * category summaries, income/spend totals, etc.) lands with
 * `DataDigestBuilder` in a later slice; extending this data class then is
 * append-only from the wire-format's perspective (Moshi ignores unknown
 * fields on both sides).
 *
 * **Privacy invariants (locked in for the full schema when it lands):**
 *  - No raw recipient names, phone numbers, or account strings. Recipients
 *    are anonymised at build time into ephemeral request-scoped ids `r1..rN`
 *    (see plans/ai-pro-phase1-spec.md §5.2 — the id→name map is a local
 *    val inside the calling ViewModel, never persisted, never sent).
 *  - No SMS bodies. No transaction descriptions. No merchant tokens.
 *  - Amounts as whole KES (Kenya doesn't use fractional shillings in
 *    practice) — declared as `Int` when they arrive.
 *
 * **JSON contract:**
 *  - Field names snake_case matching the backend Zod schema.
 *  - Nullable fields emit `null` rather than being omitted — schema
 *    evolution stays safer that way.
 *
 * See plans/ai-pro-phase1-spec.md §5.
 */
@JsonClass(generateAdapter = true)
data class DataDigest(
    /**
     * The reporting period this digest covers, formatted `YYYY-MM`
     * (e.g. `"2026-09"`). Backend validates `min(4).max(20)`.
     */
    @Json(name = "period")
    val period: String,
)
