package com.pesatrack.services.telemetry

/**
 * No-op [TelemetryClient] used for debug builds and as a fallback when
 * Firebase configuration is missing. Guarantees the app can never leak
 * telemetry through it.
 */
class NoOpTelemetryClient : TelemetryClient {
    override fun setEnabled(enabled: Boolean) = Unit
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
}
