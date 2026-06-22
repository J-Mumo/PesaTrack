package com.pesatrack.utils.parsers

import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.utils.SmsParser

/**
 * Strategy interface for SMS parsers.
 *
 * Each implementation handles a specific SMS source (M-PESA, NCBA Bank, etc.)
 * and knows how to parse transaction details from that source's SMS format.
 *
 * Implementations are registered in [SmsParserRegistry] which dispatches
 * incoming SMS to the appropriate parser based on sender ID.
 */
interface SmsParserStrategy {

    /** Human-readable name (e.g., "M-PESA", "NCBA Bank") */
    val displayName: String

    /**
     * SMS sender IDs this parser handles.
     * Matched case-insensitively against the originating address.
     * Examples: "MPESA", "NCBA_BANK"
     */
    val senderIds: List<String>

    /** ExpenseSource to tag parsed expenses with */
    val expenseSource: ExpenseSource

    /**
     * Check if this parser can handle the given SMS.
     *
     * @param sender The SMS originating address (e.g., "MPESA", "NCBA_BANK")
     * @param body The SMS message body
     * @return true if this parser should attempt to parse the message
     */
    fun canHandle(sender: String, body: String): Boolean

    /**
     * Parse the SMS body into a [ParsedSms] result.
     *
     * Returns:
     * - [ParsedSms.ExpenseResult] for outgoing-money SMS (send, pay bill, withdraw, ...).
     * - [ParsedSms.IncomeResult] for incoming-money SMS (salary, receive, bank credit, ...).
     * - [ParsedSms.NotARelevantMessage] for SMS the parser recognises but cannot or
     *   should not turn into a row (reversals, balance reports, unrecognised formats).
     *
     * @param body The SMS message body
     * @param smsDate The SMS received timestamp from the device inbox (millis since epoch).
     *                Used as the transaction timestamp when the parser cannot extract a date
     *                from the SMS body itself. Defaults to current time if not provided.
     */
    fun parseSms(body: String, smsDate: Long = System.currentTimeMillis()): ParsedSms

    /**
     * Legacy expense-only entry point retained for backward compatibility with
     * [SmsParser.parseSms]. New callers should use [parseSms] directly so income
     * results are not silently dropped.
     */
    @Deprecated(
        "Use parseSms(...) which returns ParsedSms",
        ReplaceWith("(parseSms(body, smsDate) as? ParsedSms.ExpenseResult)?.let { SmsParser.ParsedTransaction(it.expense, it.transactionCost, it.isCardApprovalUpdate) }")
    )
    fun parse(body: String, smsDate: Long = System.currentTimeMillis()): SmsParser.ParsedTransaction? {
        val result = parseSms(body, smsDate)
        return (result as? ParsedSms.ExpenseResult)?.let {
            SmsParser.ParsedTransaction(it.expense, it.transactionCost, it.isCardApprovalUpdate)
        }
    }
}
