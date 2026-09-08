package com.pesatrack.utils.parsers

import com.pesatrack.domain.models.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for Pochi la Biashara ("Business Pouch") SMS in
 * [MpesaSmsParser]. Pochi SMS omit both the recipient phone number and the
 * "for account" clause, so they previously fell through Send Money / Pay Bill
 * / Buy Goods classification and were silently dropped.
 */
class MpesaSmsParserPochiTest {

    private val parser = MpesaSmsParser()
    private val smsTimestamp = 1_700_000_000_000L

    @Test
    fun `pochi la biashara SMS is parsed as BUY_GOODS`() {
        val body =
            "UI5044ZHEB Confirmed. Ksh800.00 sent to MARGARET  GICHIRA on 5/9/26 at 2:13 PM. " +
                "New M-PESA balance is Ksh2,931.83. Transaction cost, Ksh13.00. " +
                "Amount you can transact within the day is 498,600.00. See all your balances now https://saf.cx/kWQpy"

        val result = parser.parseSms(body, smsTimestamp)
        assertTrue("Expected ExpenseResult, got $result", result is ParsedSms.ExpenseResult)
        val expense = (result as ParsedSms.ExpenseResult).expense

        assertEquals("UI5044ZHEB", expense.transactionId)
        assertEquals(800.0, expense.amount, 0.001)
        assertEquals(PaymentType.BUY_GOODS, expense.paymentType)
        // Double-space in the source name should be normalised to a single space
        assertEquals("MARGARET GICHIRA", expense.recipientName)
        assertEquals("MARGARET GICHIRA", expense.recipient)

        // Transaction cost of Ksh13.00 must still be attached as a separate expense
        val cost = result.transactionCost
        assertNotNull("Transaction cost expense should be extracted", cost)
        assertEquals(13.0, cost!!.amount, 0.001)
        assertEquals(PaymentType.TRANSACTION_COST, cost.paymentType)
    }

    @Test
    fun `send money with phone still wins over pochi pattern`() {
        val body =
            "ABC123XYZW Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 5/9/26 at 2:13 PM. " +
                "New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh7.00."

        val result = parser.parseSms(body, smsTimestamp)
        val expense = (result as ParsedSms.ExpenseResult).expense
        assertEquals(PaymentType.SEND_MONEY, expense.paymentType)
        assertEquals("0712345678", expense.recipient)
        assertEquals("JOHN DOE", expense.recipientName)
    }

    @Test
    fun `pay bill with account clause still wins over pochi pattern`() {
        val body =
            "PB1234ABCD Confirmed. Ksh2,000.00 sent to KPLC PREPAID for account 12345678 " +
                "on 5/9/26 at 2:13 PM. New M-PESA balance is Ksh500.00. Transaction cost, Ksh0.00."

        val result = parser.parseSms(body, smsTimestamp)
        val expense = (result as ParsedSms.ExpenseResult).expense
        assertEquals(PaymentType.PAY_BILL, expense.paymentType)
        assertEquals("12345678", expense.recipient)
        assertEquals("KPLC PREPAID", expense.recipientName)
    }

    @Test
    fun `pochi with zero transaction cost has no cost expense`() {
        val body =
            "PC0000ABCD Confirmed. Ksh50.00 sent to SMALL SHOP on 5/9/26 at 2:13 PM. " +
                "New M-PESA balance is Ksh100.00. Transaction cost, Ksh0.00."

        val result = parser.parseSms(body, smsTimestamp)
        val expenseResult = result as ParsedSms.ExpenseResult
        assertEquals(PaymentType.BUY_GOODS, expenseResult.expense.paymentType)
        assertEquals("SMALL SHOP", expenseResult.expense.recipientName)
        assertNull(expenseResult.transactionCost)
    }
}
