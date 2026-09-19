package com.pesatrack.services.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip + schema stability for [ProState] persistence.
 *
 * The stored JSON blob is the durable contract between older installed
 * builds and any future in-place upgrade — if these tests fail after a
 * change, either the schema needs a `_v2` migration in
 * `AppPreferences.KEY_PRO_STATE`, or the change should be reverted.
 *
 * See plans/ai-pro-phase1-spec.md §3.2.
 */
class ProStateJsonTest {

    @Test
    fun `default state round-trips through serialize + parse`() {
        val original = ProState.DEFAULT
        val json = ProStateJson.serialize(original)
        val restored = ProStateJson.parse(json)

        assertEquals(original, restored)
    }

    @Test
    fun `fully-populated monthly entitlement round-trips`() {
        val original = ProState(
            isEntitled = true,
            tier = ProProduct.MONTHLY,
            purchaseToken = "GPA.1234-5678-9012-34567",
            expiresAtEpochMs = 1_800_000_000_000L,
            lastVerifiedAtEpochMs = 1_780_000_000_000L,
            autoRenewing = true,
        )

        val json = ProStateJson.serialize(original)
        val restored = ProStateJson.parse(json)

        assertEquals(original, restored)
    }

    @Test
    fun `annual tier is preserved`() {
        val original = ProState.DEFAULT.copy(
            isEntitled = true,
            tier = ProProduct.ANNUAL,
            purchaseToken = "GPA.annual",
            expiresAtEpochMs = 1_900_000_000_000L,
        )
        val restored = ProStateJson.parse(ProStateJson.serialize(original))
        assertEquals(ProProduct.ANNUAL, restored?.tier)
    }

    @Test
    fun `tier is persisted as short name (monthly, annual) not enum identifier`() {
        // The wire format is a deliberate decoupling from Kotlin enum
        // identifiers — this test locks in the on-disk name so a rename
        // of the enum constant cannot silently break older installs.
        val monthlyJson = ProStateJson.serialize(
            ProState.DEFAULT.copy(tier = ProProduct.MONTHLY)
        )
        val annualJson = ProStateJson.serialize(
            ProState.DEFAULT.copy(tier = ProProduct.ANNUAL)
        )

        assertTrue(
            "MONTHLY should serialize with the 'monthly' short name, got: $monthlyJson",
            monthlyJson.contains("\"monthly\"")
        )
        assertTrue(
            "ANNUAL should serialize with the 'annual' short name, got: $annualJson",
            annualJson.contains("\"annual\"")
        )
        // Enum identifiers must NOT appear in the wire format — that would
        // silently couple the schema to the Kotlin source.
        assertFalse(monthlyJson.contains("MONTHLY"))
        assertFalse(annualJson.contains("ANNUAL"))
    }

    @Test
    fun `parse of malformed JSON returns null`() {
        assertNull(ProStateJson.parse("not-json-at-all"))
        assertNull(ProStateJson.parse("{ isEntitled: true }")) // unquoted keys
        assertNull(ProStateJson.parse(""))
    }

    @Test
    fun `parse of JSON with unknown tier returns null (caller falls back to DEFAULT)`() {
        val json = """{"isEntitled":true,"tier":"platinum","autoRenewing":false}"""
        // Moshi rejects unknown enum values by default — parse returns null,
        // which AppPreferences.getProState maps to ProState.DEFAULT so a
        // corrupted-or-future-tier blob never wedges the app on cold start.
        assertNull(ProStateJson.parse(json))
    }

    @Test
    fun `parse of JSON missing optional fields uses data-class defaults`() {
        // A minimal JSON blob (e.g. from a hypothetical earlier schema that
        // only stored isEntitled + tier) must still parse; missing fields
        // fall back to the data-class defaults, not throw.
        val json = """{"isEntitled":false}"""
        val restored = ProStateJson.parse(json)

        assertNotNull(restored)
        assertEquals(false, restored?.isEntitled)
        assertNull(restored?.tier)
        assertNull(restored?.purchaseToken)
        assertNull(restored?.expiresAtEpochMs)
        assertNull(restored?.lastVerifiedAtEpochMs)
        assertEquals(false, restored?.autoRenewing)
    }

    @Test
    fun `productId strings match the backend KNOWN_PRODUCT_IDS set`() {
        // Backend backend/src/services/playBilling.js defines
        //   KNOWN_PRODUCT_IDS = new Set(['pesatrack_pro_monthly', 'pesatrack_pro_annual']);
        // If these strings ever drift between client and backend, /billing/verify
        // will 400 with 'unknown_product' every time. Lock them here.
        assertEquals("pesatrack_pro_monthly", ProProduct.MONTHLY.productId)
        assertEquals("pesatrack_pro_annual", ProProduct.ANNUAL.productId)
    }

    @Test
    fun `fromProductId is the exact inverse of productId`() {
        assertEquals(ProProduct.MONTHLY, ProProduct.fromProductId("pesatrack_pro_monthly"))
        assertEquals(ProProduct.ANNUAL, ProProduct.fromProductId("pesatrack_pro_annual"))
        assertNull(ProProduct.fromProductId("pesatrack_pro_lifetime"))
        assertNull(ProProduct.fromProductId(null))
        assertNull(ProProduct.fromProductId(""))
    }
}
