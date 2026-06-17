package com.pesatrack.data.repository

import com.pesatrack.domain.models.EffectiveIncomeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Table-driven tests for the income reconciliation rules.
 *
 * Source: `plans/income-tracking-plan.md` §6.4.
 *
 * | Detected | Manual override        | value | source                  |
 * |----------|------------------------|-------|-------------------------|
 * | 0/null   | null                   | null  | NONE                    |
 * | 0/null   | X                      | X     | MANUAL_OVERRIDE         |
 * | D > 0    | null                   | D     | DETECTED                |
 * | D > 0    | X within ±10% of D     | D     | DETECTED                |
 * | D > 0    | X > D × 1.10           | X     | DETECTED_BELOW_OVERRIDE |
 * | D > 0    | X < D × 0.90           | D     | DETECTED                |
 */
class IncomeRepositoryReconcileTest {

    @Test
    fun `no detected, no override -- NONE`() {
        val r = IncomeRepository.reconcile(detected = 0.0, manual = null)
        assertNull(r.value)
        assertEquals(EffectiveIncomeSource.NONE, r.source)
        assertEquals(0.0, r.detectedAmount, 0.001)
        assertNull(r.manualAmount)
    }

    @Test
    fun `negative detected treated as zero, no override -- NONE`() {
        val r = IncomeRepository.reconcile(detected = -50.0, manual = null)
        assertNull(r.value)
        assertEquals(EffectiveIncomeSource.NONE, r.source)
    }

    @Test
    fun `no detected, override set -- MANUAL_OVERRIDE`() {
        val r = IncomeRepository.reconcile(detected = 0.0, manual = 100000.0)
        assertEquals(100000.0, r.value!!, 0.001)
        assertEquals(EffectiveIncomeSource.MANUAL_OVERRIDE, r.source)
        assertEquals(0.0, r.detectedAmount, 0.001)
        assertEquals(100000.0, r.manualAmount!!, 0.001)
    }

    @Test
    fun `detected present, no override -- DETECTED`() {
        val r = IncomeRepository.reconcile(detected = 85000.0, manual = null)
        assertEquals(85000.0, r.value!!, 0.001)
        assertEquals(EffectiveIncomeSource.DETECTED, r.source)
        assertEquals(85000.0, r.detectedAmount, 0.001)
        assertNull(r.manualAmount)
    }

    @Test
    fun `detected and override match within 10 percent -- DETECTED`() {
        // D = 100k, X = 105k → diff 5%, within band, prefer detected.
        val r = IncomeRepository.reconcile(detected = 100000.0, manual = 105000.0)
        assertEquals(100000.0, r.value!!, 0.001)
        assertEquals(EffectiveIncomeSource.DETECTED, r.source)
    }

    @Test
    fun `override exactly 10 percent above detected -- DETECTED (boundary inclusive)`() {
        // X / D = 1.10 exactly; rule says "X > D × 1.10" so equality stays DETECTED.
        val r = IncomeRepository.reconcile(detected = 100000.0, manual = 110000.0)
        assertEquals(100000.0, r.value!!, 0.001)
        assertEquals(EffectiveIncomeSource.DETECTED, r.source)
    }

    @Test
    fun `override more than 10 percent above detected -- DETECTED_BELOW_OVERRIDE`() {
        val r = IncomeRepository.reconcile(detected = 100000.0, manual = 120000.0)
        assertEquals(120000.0, r.value!!, 0.001)
        assertEquals(EffectiveIncomeSource.DETECTED_BELOW_OVERRIDE, r.source)
        assertEquals(100000.0, r.detectedAmount, 0.001)
        assertEquals(120000.0, r.manualAmount!!, 0.001)
    }

    @Test
    fun `detected exceeds override -- DETECTED`() {
        // Detected income larger than user's set baseline → trust detection.
        val r = IncomeRepository.reconcile(detected = 150000.0, manual = 100000.0)
        assertEquals(150000.0, r.value!!, 0.001)
        assertEquals(EffectiveIncomeSource.DETECTED, r.source)
    }

    @Test
    fun `override much lower than detected -- DETECTED`() {
        val r = IncomeRepository.reconcile(detected = 200000.0, manual = 50000.0)
        assertEquals(200000.0, r.value!!, 0.001)
        assertEquals(EffectiveIncomeSource.DETECTED, r.source)
    }
}
