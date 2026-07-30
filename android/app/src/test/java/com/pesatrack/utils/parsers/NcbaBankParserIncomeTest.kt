package com.pesatrack.utils.parsers

import com.pesatrack.domain.models.IncomeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regex coverage for income detection in [NcbaBankParser] (Income tracking Phase 2 §5.2).
 */
class NcbaBankParserIncomeTest {

    private val parser = NcbaBankParser()
    private val smsTimestamp = 1_700_000_000_000L

    @Test
    fun `credit with ref is detected as UNCATEGORIZED income`() {
        val body =
            "Your account 763****018 has been credited with KES 25,000.00 from " +
                "ACME COMPANY LTD Ref: ABC123XYZ on 1/12/2024 13:45"
        val result = parser.parseSms(body, smsTimestamp)
        assertTrue("Expected IncomeResult, got $result", result is ParsedSms.IncomeResult)
        val income = (result as ParsedSms.IncomeResult).income
        assertEquals(IncomeSource.UNCATEGORIZED, income.source)
        assertEquals(25_000.0, income.amount, 0.001)
        assertNotNull(income.sender)
        assertEquals("NCBA", income.parserSource)
        assertEquals("ABC123XYZ", income.transactionId)
    }

    @Test
    fun `credit without ref uses fallback transactionId`() {
        val body = "Your account 763****018 has been credited with KES 5,000.00 from JANE DOE."
        val result = parser.parseSms(body, smsTimestamp)
        val income = (result as ParsedSms.IncomeResult).income
        assertEquals(5_000.0, income.amount, 0.001)
        assertTrue(
            "Fallback id should be deterministic, was '${income.transactionId}'",
            income.transactionId.startsWith("NCBA-")
        )
    }

    @Test
    fun `credit without amount is NotARelevantMessage`() {
        val body = "Your account 763****018 has been credited from JANE DOE."
        val result = parser.parseSms(body, smsTimestamp)
        assertEquals(ParsedSms.NotARelevantMessage, result)
    }

    @Test
    fun `real NCBA credit SMS with sender and date-before-from is detected as income`() {
        val body =
            "Your account 763****018 has been credited with KES 524,498.10 on 23/07/2026 at 15:25 " +
                "from MICROSOFT RESEARCH & DEVELOPMENT KE 16000. Ref: FT26204ZG5R2. " +
                "For queries, call 0711056444 / 0732156444 or WhatsApp: 0717804444."
        val result = parser.parseSms(body, smsTimestamp)
        assertTrue("Expected IncomeResult, got $result", result is ParsedSms.IncomeResult)
        val income = (result as ParsedSms.IncomeResult).income
        assertEquals(524_498.10, income.amount, 0.001)
        assertEquals("FT26204ZG5R2", income.transactionId)
        assertNotNull(income.sender)
        assertTrue(
            "Sender should contain MICROSOFT, was '${income.sender}'",
            income.sender!!.contains("MICROSOFT", ignoreCase = true)
        )
    }

    @Test
    fun `real NCBA credit SMS without from clause is detected as income`() {
        val body =
            "Your account 763****018 has been credited with KES 3,125.00 on 20/07/2026 at 11:11 " +
                "for . Ref: FT26201MC1KB. For queries, call 0711056444 / 0732156444 or WhatsApp: 0717804444."
        val result = parser.parseSms(body, smsTimestamp)
        assertTrue("Expected IncomeResult, got $result", result is ParsedSms.IncomeResult)
        val income = (result as ParsedSms.IncomeResult).income
        assertEquals(3_125.0, income.amount, 0.001)
        assertEquals("FT26201MC1KB", income.transactionId)
    }

    @Test
    fun `detailed till transfer SMS still parses as ExpenseResult`() {
        // Generic "Your account ... has been debited" SMS are intentionally skipped;
        // the paired detailed "Mpesa Till transfer ..." SMS is the one that becomes an expense.
        val body =
            "Mpesa Till transfer of KES 1737.00 to JAZA MUTHIGA BANK REF. FTX26115UARQT " +
                "MPESA REF. UDPSGBHAML was successful."
        val result = parser.parseSms(body, smsTimestamp)
        assertTrue("Expected ExpenseResult, got $result", result is ParsedSms.ExpenseResult)
    }
}
