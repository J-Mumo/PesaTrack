package com.pesatrack.services.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Firebase-backed [TelemetryClient].
 *
 * Collection is default-disabled at the manifest level
 * ([AndroidManifest.xml] `firebase_analytics_collection_enabled=false`).
 * We flip it on only after the user opts in — either through the consent
 * sheet or via Settings — and back off immediately if they revoke.
 *
 * When disabled, [logEvent] short-circuits before touching Firebase.
 */
class FirebaseTelemetryClient(context: Context) : TelemetryClient {

    private val analytics: FirebaseAnalytics = Firebase.analytics
    @Volatile private var enabled: Boolean = false

    init {
        // Belt-and-braces: force collection off at startup regardless of any
        // cached SDK state. setEnabled(true) will re-enable it if the user
        // has already opted in.
        analytics.setAnalyticsCollectionEnabled(false)
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        analytics.setAnalyticsCollectionEnabled(enabled)
        if (!enabled) {
            // Reset the Firebase App Instance ID so the previous session's
            // pseudonymous identifier is no longer usable after opt-out.
            runCatching { analytics.resetAnalyticsData() }
        }
    }

    override fun logEvent(name: String, params: Map<String, Any>) {
        if (!enabled) return
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putLong(key, value.toLong())
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Float -> putDouble(key, value.toDouble())
                    is Boolean -> putLong(key, if (value) 1L else 0L)
                    // Anything else is silently dropped — we do NOT want to
                    // stringify arbitrary values that could smuggle in PII.
                }
            }
        }
        analytics.logEvent(name, bundle)
    }
}
