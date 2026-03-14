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
     * Parse the SMS body into a [SmsParser.ParsedTransaction].
     *
     * Returns null if:
     * - The SMS is not a parseable expense transaction
     * - The SMS is a non-expense (e.g., receive money, deposit, self-transfer)
     * - The format is unrecognized
     *
     * @param body The SMS message body
     * @return ParsedTransaction if parsing successful, null otherwise
     */
    fun parse(body: String): SmsParser.ParsedTransaction?
}
