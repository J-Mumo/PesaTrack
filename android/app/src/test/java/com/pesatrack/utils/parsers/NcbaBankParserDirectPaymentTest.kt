package com.pesatrack.utils.parsers

import com.pesatrack.domain.models.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NcbaBankParserDirectPaymentTest {
    private val parser = NcbaBankParser()

    @Test
    fun `direct Kenya Power confirmation is parsed as pay bill`() {
        val body = "Dear Customer, your Kenya Power Prepaid payment of KES 1500.00 " +
            "to Meter Number: 92106709873 was successful. Token number: 19277682111301081334. " +
            "Units: 59.7. Ref: FTX26278PAFWI. NCBA, Go for it."

        val result = parser.parseSms(body, 1_760_000_000_000L)
        assertTrue("Expected ExpenseResult, got $result", result is ParsedSms.ExpenseResult)
        val expense = (result as ParsedSms.ExpenseResult).expense
        assertEquals("FTX26278PAFWI", expense.transactionId)
        assertEquals(1500.0, expense.amount, 0.001)
        assertEquals("92106709873", expense.recipient)
        assertEquals("Kenya Power Prepaid", expense.recipientName)
        assertEquals(PaymentType.PAY_BILL, expense.paymentType)
        assertEquals("Meter: 92106709873", expense.notes)
        assertEquals(1_760_000_000_000L, expense.timestamp)
    }

    @Test
    fun `direct Kenya Power confirmation is handled by NCBA strategy`() {
        val body = "Dear Customer, your Kenya Power Prepaid payment of KES 1500.00 " +
            "to Meter Number: 92106709873 was successful. Ref: FTX26278PAFWI."
        assertTrue(parser.canHandle("NCBA_BANK", body))
    }

    @Test
    fun `generic debit remains skipped to avoid duplicate expense`() {
        val body = "Your account 763****018 has been debited with KES 1,500.00 " +
            "on 05/10/2026 at 10:09. Ref: FTX26278PAFWI."
        assertTrue(parser.parseSms(body) is ParsedSms.NotARelevantMessage)
    }

    @Test
    fun `card approval is parsed with merchant and card payment type`() {
        val body = "Joel, we have approved a transaction of KES  1,298.00 at " +
            "SIMBISA BRANDS SIGONA on your card no. ending *6283. If its not yours, " +
            "please call us on 0711056444/0732156444 urgently"

        val result = parser.parseSms(body, 1_760_000_000_000L)
        assertTrue(result is ParsedSms.ExpenseResult)
        val expense = (result as ParsedSms.ExpenseResult).expense
        assertEquals(1298.0, expense.amount, 0.001)
        assertEquals("SIMBISA BRANDS SIGONA", expense.recipientName)
        assertEquals("*6283", expense.recipient)
        assertEquals(PaymentType.CARD_PAYMENT, expense.paymentType)
    }

    @Test
    fun `failed pending quoted and malformed electricity messages are not expenses`() {
        val success = "Dear Customer, your Kenya Power Prepaid payment of KES 1500.00 to Meter Number: 92106709873 was successful. Ref: FTX26278PAFWI."
        for (body in listOf(success.replace("was successful", "failed"),
            success.replace("was successful", "is pending"), "Forwarded: $success",
            success.replace("FTX26278PAFWI", "FTX26278PAFWI-BAD"),
            success.replace("92106709873", "unknown"))) {
            assertTrue(body, parser.parseSms(body, 1_760_000_000_000L) is ParsedSms.NotARelevantMessage)
        }
        assertTrue(!parser.canHandle("FAKE_NCBA_BANK", success))
    }
}
