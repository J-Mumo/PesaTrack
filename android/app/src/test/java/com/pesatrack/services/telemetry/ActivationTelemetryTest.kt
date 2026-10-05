package com.pesatrack.services.telemetry

import com.pesatrack.data.local.preferences.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class ActivationTelemetryTest {

    private val day = TimeUnit.DAYS.toMillis(1)

    private fun choices(
        granted: Boolean = false, denied: Boolean = false, skipped: Boolean = false,
        importChosen: Boolean = false, importSkipped: Boolean = false, installTs: Long = 0L
    ) = AppPreferences.OnboardingChoices(granted, denied, skipped, importChosen, importSkipped, installTs)

    @Test
    fun `snapshot prefers granted over later skip`() {
        val p = ActivationTelemetry.snapshotParams(
            choices(granted = true, skipped = true, importChosen = true, installTs = 1_000L),
            1_000L + 2 * day
        )
        assertEquals("granted", p[TelemetryEvents.PARAM_SMS])
        assertEquals("chosen", p[TelemetryEvents.PARAM_IMPORT])
        assertEquals("1-3", p[TelemetryEvents.PARAM_DAYS_SINCE_INSTALL])
    }

    @Test
    fun `snapshot reports denied and unknown`() {
        val p = ActivationTelemetry.snapshotParams(choices(denied = true), 5 * day)
        assertEquals("denied", p[TelemetryEvents.PARAM_SMS])
        assertEquals("unknown", p[TelemetryEvents.PARAM_IMPORT])
        assertEquals("0", p[TelemetryEvents.PARAM_DAYS_SINCE_INSTALL])
    }

    @Test
    fun `snapshot params contain only allow-listed enum strings`() {
        val p = ActivationTelemetry.snapshotParams(choices(skipped = true, installTs = 1L), 40 * day)
        assertEquals(3, p.size)
        p.values.forEach { assert(it is String && (it as String).length <= 10) }
        assertEquals("30+", p[TelemetryEvents.PARAM_DAYS_SINCE_INSTALL])
    }

    @Test
    fun `data source mapping`() {
        assertEquals("sms", ActivationTelemetry.dataSourceFor(TelemetryEvents.SOURCE_SMS))
        assertEquals("statement", ActivationTelemetry.dataSourceFor(TelemetryEvents.SOURCE_STATEMENT))
        assertEquals("statement", ActivationTelemetry.dataSourceFor(TelemetryEvents.SOURCE_EXCEL))
        assertNull(ActivationTelemetry.dataSourceFor("other"))
    }

    @Test
    fun `buckets`() {
        assertEquals("0", TelemetryEvents.scanBucket(0))
        assertEquals("1-10", TelemetryEvents.scanBucket(10))
        assertEquals("11-50", TelemetryEvents.scanBucket(11))
        assertEquals("51-200", TelemetryEvents.scanBucket(200))
        assertEquals("200+", TelemetryEvents.scanBucket(201))
        assertEquals("4-7", TelemetryEvents.daysSinceInstallBucket(7))
        assertEquals("8-30", TelemetryEvents.daysSinceInstallBucket(30))
        assertEquals("sms_permission", TelemetryEvents.onboardingStep(2))
        assertEquals("import", TelemetryEvents.onboardingStep(9))
    }
}
