package com.pesatrack.utils

import com.pesatrack.domain.models.Expense
import com.pesatrack.utils.parsers.SmsParserRegistry

/**
 * M-PESA SMS Parser — backward-compatible facade.
 *
 * Delegates all parsing to [SmsParserRegistry] and individual strategy parsers.
 * Existing callers (SmsReceiver, SmsImportService) can continue to use this API
 * without changes.
 *
 * For new code, prefer using [SmsParserRegistry] directly — it supports
 * multi-source parsing (M-PESA, NCBA Bank, etc.).
 *
 * @see com.pesatrack.utils.parsers.SmsParserRegistry
 * @see com.pesatrack.utils.parsers.MpesaSmsParser
 * @see com.pesatrack.utils.parsers.NcbaBankParser
 */
object SmsParser {

    /**
     * Category ID for "Mpesa Transaction Cost" in the default categories.
     * This is defined in DefaultCategories (Financial > Mpesa Transaction Cost).
     */
    const val MPESA_TRANSACTION_COST_CATEGORY_ID = 606L

    // M-PESA sender IDs (kept for backward compat with isMpesaSms)
    private val MPESA_SENDERS = listOf("MPESA", "M-PESA", "Safaricom")

    /**
     * Result of parsing an SMS transaction.
     *
     * @property expense The main transaction expense
     * @property transactionCost Optional separate expense for the M-PESA transaction cost.
     *   Auto-categorized under "Mpesa Transaction Cost" (category 606).
     *   Only present when the SMS contains "Transaction cost, KshXX.XX" with amount > 0.
     */
    data class ParsedTransaction(
        val expense: Expense,
        val transactionCost: Expense?,
        /**
         * When true, this parsed result represents a card approval SMS that should
         * UPDATE an existing CARD_PAYMENT expense (adding merchant name) rather than
         * inserting a new record. The SmsReceiver should find a recent CARD_PAYMENT
         * within a 5-minute window and update its recipientName/notes.
         */
        val isCardApprovalUpdate: Boolean = false
    )

    /**
     * Check if SMS is from M-PESA.
     *
     * For multi-source checks (M-PESA + bank SMS), use
     * [SmsParserRegistry.canHandleAny] instead.
     */
    fun isMpesaSms(sender: String?): Boolean {
        if (sender == null) return false
        return MPESA_SENDERS.any { sender.contains(it, ignoreCase = true) }
    }

    /**
     * Check if SMS is a transaction confirmation (M-PESA only).
     *
     * For multi-source checks, use [SmsParserRegistry.canHandleAny] instead.
     */
    fun isTransactionSms(message: String): Boolean {
        // Must contain "Confirmed" (all M-PESA transaction SMS start with "TXID Confirmed.")
        if (!message.contains("Confirmed", ignoreCase = true)) return false

        // Must contain an amount
        if (!message.contains("Ksh", ignoreCase = true)) return false

        // Match any transaction keyword
        return message.contains("sent to", ignoreCase = true) ||
                message.contains("paid to", ignoreCase = true) ||
                message.contains("withdrawn", ignoreCase = true) ||
                message.contains("of airtime", ignoreCase = true) ||
                message.contains("Fuliza", ignoreCase = true) ||
                message.contains("bought", ignoreCase = true)
    }

    /**
     * Parse M-PESA SMS into a ParsedTransaction.
     *
     * Delegates to [SmsParserRegistry] using "MPESA" as the sender.
     * For multi-source parsing, use [SmsParserRegistry.parseTransaction] directly.
     *
     * @param message The SMS message body
     * @return ParsedTransaction if parsing successful, null otherwise
     */
    fun parseSms(message: String): ParsedTransaction? {
        return SmsParserRegistry.parseTransaction("MPESA", message)
    }
}
