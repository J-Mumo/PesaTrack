package com.pesatrack.services.pro

import com.squareup.moshi.Moshi

/**
 * JSON codec for [ProState] persisted under DataStore key
 * `pro_state_json_v1` (see `AppPreferences.KEY_PRO_STATE`).
 *
 * Kept out of `AppPreferences` so the encoding is trivially unit-testable
 * on the JVM with no Android or Hilt scaffolding, and so a future `_v2`
 * migration can live alongside the codec instead of leaking into the
 * preferences layer.
 *
 * The Moshi adapter uses Moshi Kotlin codegen (`@JsonClass(generateAdapter
 * = true)` on [ProState] + `moshi-kotlin-codegen` KSP dep in
 * `android/app/build.gradle.kts`) — no reflection, so this stays R8-safe
 * without any consumer ProGuard rules.
 *
 * Contract for [parse]:
 *   - Any parse failure (malformed JSON, unknown enum value in `tier`,
 *     wrong types) returns `null`. Callers should treat `null` as
 *     "corrupted; fall back to [ProState.DEFAULT]" so a bad blob never
 *     wedges the app on cold start. Corrupted-state telemetry is a
 *     Slice-A6 concern.
 *
 * See plans/ai-pro-phase1-spec.md §3.2.
 */
object ProStateJson {

    private val moshi: Moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(ProState::class.java)

    /** Serialize a [ProState] to its stable v1 JSON wire format. */
    fun serialize(state: ProState): String = adapter.toJson(state)

    /**
     * Parse a v1 JSON string into a [ProState]. Returns `null` if the
     * string is malformed or references an unknown [ProProduct]; callers
     * should substitute [ProState.DEFAULT] in that case.
     */
    fun parse(json: String): ProState? =
        runCatching { adapter.fromJson(json) }.getOrNull()
}
