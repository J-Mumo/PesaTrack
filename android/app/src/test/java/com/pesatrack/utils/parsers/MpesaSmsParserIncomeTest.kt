package com.pesatrack.utils.parsers

import com.pesatrack.domain.models.IncomeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regex coverage for income detection in [MpesaSmsParser] (Income tracking Phase 2).
 *
 * Each test feeds a representative SMS body and asserts the parser returns
 * a [ParsedSms.IncomeResult] with the expected source/amount/sender — see
 * `plans/income-tracking-plan.md` §5.2.
 */
class MpesaSmsParserIncomeTest {

    private val parser = MpesaSmsParser()
    private val smsTimestamp = 1_700_000_000_000L

    @Test
    fun `salary payment is detected as SALARY`() {
        val body =
            "TGH1ABCDEF Confirmed. You have received Ksh50,000.00 Salary Payment from " +
                "ACME LTD 0700000000 on 1/12/23 at 9:00 AM New M-PESA balance is Ksh51,234.00"
        val result = parser.parseSms(body, smsTimestamp)
        assertTrue("Expected IncomeResult, got $result", result is ParsedSms.IncomeResult)
        val income = (result as ParsedSms.IncomeResult).income
        assertEquals(IncomeSource.SALARY, income.source)
        assertEquals(50_000.0, income.amount, 0.001)
        assertNotNull(income.sender)
        assertTrue("Sender should mention ACME, was '${income.sender}'", income.sender!!.contains("ACME"))
        assertEquals("MPESA", income.parserSource)
        assertEquals("TGH1ABCDEF", income.transactionId)
        assertTrue(income.isCategorized)
    }

    @Test
    fun `business payment is detected as BUSINESS`() {
        val body =
            "ABC123XYZW Confirmed. You have received Ksh4,500.00 Business Payment from " +
                "JANE DOE 0711111111 on 12/3/24 at 1:00 PM"
        val result = parser.parseSms(body, smsTimestamp)
        val income = (result as ParsedSms.IncomeResult).income
        assertEquals(IncomeSource.BUSINESS, income.source)
        assertEquals(4_500.0, income.amount, 0.001)
    }

    @Test
    fun `peer receive is detected as UNCATEGORIZED with sender`() {
        val body =
            "PQR987SDFG Confirmed. You have received Ksh1,200.00 from JOHN DOE 254712345678 " +
                "on 5/6/24 at 11:11 AM"
        val result = parser.parseSms(body, smsTimestamp)
        val income = (result as ParsedSms.IncomeResult).income
        assertEquals(IncomeSource.UNCATEGORIZED, income.source)
        assertEquals(1_200.0, income.amount, 0.001)
        assertTrue("Sender should contain name, was '${income.sender}'", income.sender!!.contains("JOHN"))
    }

    @Test
    fun `mshwari withdrawal is detected as TRANSFER_IN`() {
        val body =
            "TXY555ABCD Confirmed. Ksh3,000.00 transferred from M-Shwari to M-PESA " +
                "on 1/1/24 at 8:00 AM"
        val result = parser.parseSms(body, smsTimestamp)
        val income = (result as ParsedSms.IncomeResult).income
        assertEquals(IncomeSource.TRANSFER_IN, income.source)
        assertEquals(3_000.0, income.amount, 0.001)
    }

    @Test
    fun `deposit at agent is detected as TRANSFER_IN`() {
        val body =
            "DEP123ABCD Confirmed. You have deposited Ksh5,000.00 to your M-PESA account " +
                "on 1/1/24 at 8:00 AM"
        val result = parser.parseSms(body, smsTimestamp)
        val income = (result as ParsedSms.IncomeResult).income
        assertEquals(IncomeSource.TRANSFER_IN, income.source)
        assertEquals(5_000.0, income.amount, 0.001)
    }

    @Test
    fun `reversal SMS is ignored as NotARelevantMessage`() {
        val body =
            "TGH1ABCDEF Confirmed. Transaction TGH1ABCDEF has been reversed. " +
                "Ksh1,000.00 has been deposited back to your M-PESA account"
        val result = parser.parseSms(body, smsTimestamp)
        assertEquals(ParsedSms.NotARelevantMessage, result)
    }

    @Test
    fun `random non-mpesa text returns NotARelevantMessage`() {
        val result = parser.parseSms("Have a great day!", smsTimestamp)
        assertEquals(ParsedSms.NotARelevantMessage, result)
    }

    @Test
    fun `expense send is still parsed as ExpenseResult`() {
        val body =
            "TGH1ABCDEF Confirmed. Ksh1,500.00 sent to JANE DOE 0711111111 on 1/12/23 " +
                "at 9:00 AM. New M-PESA balance is Ksh3,000.00. Transaction cost, Ksh23.00"
        val result = parser.parseSms(body, smsTimestamp)
        assertTrue("Expected ExpenseResult, got $result", result is ParsedSms.ExpenseResult)
        val exp = (result as ParsedSms.ExpenseResult).expense
        assertEquals(1_500.0, exp.amount, 0.001)
        assertNotNull(result.transactionCost)
        assertEquals(23.0, result.transactionCost!!.amount, 0.001)
    }
}
