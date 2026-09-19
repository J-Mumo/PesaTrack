package com.pesatrack.services.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure decision function
 * [PurchaseOutcome.Companion.fromPlayResponseCode]. This is the single
 * place where Google Play `BillingResponseCode`s get bucketed into the
 * user-visible outcome variants, and the mapping is what Slice A6's
 * telemetry event `pro_purchase_failed.reason` will read — a drift here
 * silently breaks the failure dashboard. Locking exhaustively.
 *
 * Every arm of `PlayBillingResponseCodes` is exercised at least once so
 * accidental "else" fallthrough after an SDK constant addition is
 * caught.
 */
class PurchaseOutcomeMappingTest {

    // ─── UserCancelled ──────────────────────────────────────────────────

    @Test
    fun `USER_CANCELED maps to UserCancelled regardless of phase`() {
        assertEquals(
            PurchaseOutcome.UserCancelled,
            PurchaseOutcome.fromPlayResponseCode(
                PlayBillingResponseCodes.USER_CANCELED, phase = "update"
            )
        )
        assertEquals(
            PurchaseOutcome.UserCancelled,
            PurchaseOutcome.fromPlayResponseCode(
                PlayBillingResponseCodes.USER_CANCELED, phase = "launch"
            )
        )
    }

    // ─── NetworkError bucket ────────────────────────────────────────────

    @Test
    fun `SERVICE_UNAVAILABLE maps to NetworkError`() {
        val outcome = PurchaseOutcome.fromPlayResponseCode(
            PlayBillingResponseCodes.SERVICE_UNAVAILABLE, phase = "connect"
        )
        assertTrue("expected NetworkError got $outcome", outcome is PurchaseOutcome.NetworkError)
        val cause = (outcome as PurchaseOutcome.NetworkError).cause
        assertTrue(cause is BillingNetworkException)
        assertEquals(PlayBillingResponseCodes.SERVICE_UNAVAILABLE, (cause as BillingNetworkException).playResponseCode)
        assertEquals("connect", cause.phase)
    }

    @Test
    fun `SERVICE_DISCONNECTED maps to NetworkError`() {
        val outcome = PurchaseOutcome.fromPlayResponseCode(
            PlayBillingResponseCodes.SERVICE_DISCONNECTED, phase = "update"
        )
        assertTrue(outcome is PurchaseOutcome.NetworkError)
    }

    @Test
    fun `SERVICE_TIMEOUT maps to NetworkError`() {
        val outcome = PurchaseOutcome.fromPlayResponseCode(
            PlayBillingResponseCodes.SERVICE_TIMEOUT, phase = "launch"
        )
        assertTrue(outcome is PurchaseOutcome.NetworkError)
    }

    @Test
    fun `NETWORK_ERROR maps to NetworkError`() {
        val outcome = PurchaseOutcome.fromPlayResponseCode(
            PlayBillingResponseCodes.NETWORK_ERROR, phase = "update"
        )
        assertTrue(outcome is PurchaseOutcome.NetworkError)
    }

    // ─── BillingFailed bucket (everything else non-OK, non-cancel) ──────

    @Test
    fun `BILLING_UNAVAILABLE maps to BillingFailed`() {
        val outcome = PurchaseOutcome.fromPlayResponseCode(
            PlayBillingResponseCodes.BILLING_UNAVAILABLE, phase = "connect"
        )
        assertTrue(outcome is PurchaseOutcome.BillingFailed)
        val failed = outcome as PurchaseOutcome.BillingFailed
        assertEquals(PlayBillingResponseCodes.BILLING_UNAVAILABLE, failed.playResponseCode)
        assertEquals("connect", failed.phase)
    }

    @Test
    fun `ITEM_UNAVAILABLE maps to BillingFailed`() {
        assertTrue(
            PurchaseOutcome.fromPlayResponseCode(
                PlayBillingResponseCodes.ITEM_UNAVAILABLE, phase = "launch"
            ) is PurchaseOutcome.BillingFailed
        )
    }

    @Test
    fun `DEVELOPER_ERROR maps to BillingFailed`() {
        assertTrue(
            PurchaseOutcome.fromPlayResponseCode(
                PlayBillingResponseCodes.DEVELOPER_ERROR, phase = "launch"
            ) is PurchaseOutcome.BillingFailed
        )
    }

    @Test
    fun `ERROR maps to BillingFailed`() {
        assertTrue(
            PurchaseOutcome.fromPlayResponseCode(
                PlayBillingResponseCodes.ERROR, phase = "update"
            ) is PurchaseOutcome.BillingFailed
        )
    }

    @Test
    fun `ITEM_ALREADY_OWNED maps to BillingFailed with phase preserved`() {
        // Callers use this bucket to prompt the user toward the "Restore purchase"
        // flow — verified by phase inspection in the UI layer.
        val outcome = PurchaseOutcome.fromPlayResponseCode(
            PlayBillingResponseCodes.ITEM_ALREADY_OWNED, phase = "launch"
        )
        assertTrue(outcome is PurchaseOutcome.BillingFailed)
        assertEquals("launch", (outcome as PurchaseOutcome.BillingFailed).phase)
    }

    @Test
    fun `ITEM_NOT_OWNED maps to BillingFailed`() {
        assertTrue(
            PurchaseOutcome.fromPlayResponseCode(
                PlayBillingResponseCodes.ITEM_NOT_OWNED, phase = "acknowledge"
            ) is PurchaseOutcome.BillingFailed
        )
    }

    @Test
    fun `FEATURE_NOT_SUPPORTED maps to BillingFailed`() {
        assertTrue(
            PurchaseOutcome.fromPlayResponseCode(
                PlayBillingResponseCodes.FEATURE_NOT_SUPPORTED, phase = "connect"
            ) is PurchaseOutcome.BillingFailed
        )
    }

    // ─── Defensive: OK reaching the mapping is a developer error ────────

    @Test
    fun `OK reaching the mapping is bucketed as BillingFailed with unexpected_ok suffix`() {
        // OK should never reach the mapping — the caller is supposed to
        // handle the happy path directly. If it does slip through, we
        // want a distinguishable bucket in telemetry to catch the bug.
        val outcome = PurchaseOutcome.fromPlayResponseCode(
            PlayBillingResponseCodes.OK, phase = "update"
        )
        assertTrue(outcome is PurchaseOutcome.BillingFailed)
        val failed = outcome as PurchaseOutcome.BillingFailed
        assertEquals(PlayBillingResponseCodes.OK, failed.playResponseCode)
        assertEquals("update:unexpected_ok", failed.phase)
    }

    // ─── PlayBillingResponseCodes numeric values lock ───────────────────

    @Test
    fun `PlayBillingResponseCodes numeric values match the Play SDK`() {
        // These are the durable Play BillingResponseCode integers. Locking
        // them here so a copy-paste rename or a Play SDK renumbering
        // surfaces immediately. Values from
        // https://developer.android.com/reference/com/android/billingclient/api/BillingClient.BillingResponseCode
        assertEquals(0, PlayBillingResponseCodes.OK)
        assertEquals(1, PlayBillingResponseCodes.USER_CANCELED)
        assertEquals(2, PlayBillingResponseCodes.SERVICE_UNAVAILABLE)
        assertEquals(3, PlayBillingResponseCodes.BILLING_UNAVAILABLE)
        assertEquals(4, PlayBillingResponseCodes.ITEM_UNAVAILABLE)
        assertEquals(5, PlayBillingResponseCodes.DEVELOPER_ERROR)
        assertEquals(6, PlayBillingResponseCodes.ERROR)
        assertEquals(7, PlayBillingResponseCodes.ITEM_ALREADY_OWNED)
        assertEquals(8, PlayBillingResponseCodes.ITEM_NOT_OWNED)
        assertEquals(12, PlayBillingResponseCodes.NETWORK_ERROR)
        assertEquals(-1, PlayBillingResponseCodes.SERVICE_DISCONNECTED)
        assertEquals(-2, PlayBillingResponseCodes.FEATURE_NOT_SUPPORTED)
        assertEquals(-3, PlayBillingResponseCodes.SERVICE_TIMEOUT)
    }

    // ─── BillingNetworkException carries diagnostic info ────────────────

    @Test
    fun `BillingNetworkException message includes code and phase`() {
        val ex = BillingNetworkException(playResponseCode = 12, phase = "launch")
        val msg = ex.message
        assertNotNull(msg)
        assertTrue("expected 'code=12', got: $msg", msg!!.contains("code=12"))
        assertTrue("expected 'phase=launch', got: $msg", msg.contains("phase=launch"))
    }
}
