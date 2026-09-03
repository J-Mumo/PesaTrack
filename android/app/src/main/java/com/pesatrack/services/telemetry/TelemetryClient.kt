package com.pesatrack.services.telemetry

/**
 * Vendor-agnostic telemetry client.
 *
 * All event emission in the app goes through this interface — never call
 * Firebase (or any future analytics SDK) directly. This keeps the vendor
 * swap surface small and gives us a single choke point where the consent
 * flag is enforced.
 *
 * Implementations must:
 *  - Check the user's opt-in state before doing any network work
 *  - Silently no-op when telemetry is disabled (never throw)
 *  - Never accept or transmit financial data, SMS content, or PII
 *
 * Phase 1 emits only [TelemetryEvents]. See plans/product-principles.md.
 */
interface TelemetryClient {

    /**
     * Reflect the user's opt-in choice. Called on app start (to apply the
     * persisted flag) and whenever the user toggles the Settings switch.
     *
     * When [enabled] is `false`, implementations should:
     *  - Immediately stop collection
     *  - Discard any queued but unsent events
     *  - Reset per-user identifiers where the SDK exposes an API for it
     */
    fun setEnabled(enabled: Boolean)

    /**
     * Emit an allow-listed event. [name] must be a value from [TelemetryEvents].
     * [params] must contain only allow-listed keys and primitive values (no
     * user-entered strings, no amounts, no counterparty text).
     */
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
}
