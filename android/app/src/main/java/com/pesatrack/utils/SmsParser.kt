package com.pesatrack.utils

import android.util.Log
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

/**
 * M-PESA SMS Parser
 *
 * Parses M-PESA confirmation SMS messages to extract transaction details.
 * Only parses EXPENSE transactions — Receive Money, Deposit, and Reversal are ignored.
 *
 * Also extracts "Transaction cost, Ksh53.00" and creates a separate auto-categorized
 * expense under "Mpesa Transaction Cost" (category ID 811).
 *
 * Actual SMS formats (from real M-PESA messages):
 *
 * 1. Send Money:
 *    "UCB048VVN7 Confirmed. Ksh2,800.00 sent to DIBON SEWE 0722636142 on 11/3/26 at 10:31 AM.
 *     New M-PESA balance is Ksh4,241.33. Transaction cost, Ksh53.00. ..."
 *
 * 2. Pay Bill:
 *    "UCB048VRG3 Confirmed. Ksh100.00 sent to NABO CAPITAL LTD C2B for account PG5QWT on 11/3/26
 *     at 10:37 AM New M-PESA balance is Ksh4,141.33. Transaction cost, Ksh0.00. ..."
 *
 * 3. Buy Goods (Till):
 *    "UCA048UL5Q Confirmed. Ksh1,000.00 paid to sarah k ltd. on 10/3/26 at 8:03 PM.
 *     New M-PESA balance is Ksh28,139.33. Transaction cost, Ksh0.00. ..."
 *
 * 4. M-PESA Card (Global):
 *    "UCB048W0AD Confirmed. Ksh247,481.57 sent to M-PESA CARD for account HU HBS ONLINE
 *     617-496-6355 US on 11/3/26 at 11:47 AM New M-PESA balance is Ksh8,406.76. ..."
 *
 * 5. Withdraw from Agent:
 *    "ABC123XYZ Confirmed.You have withdrawn Ksh1,000.00 from 123456 - AGENT NAME on 15/1/24
 *     at 4:00 PM. New M-PESA balance is Ksh1,500.00. Transaction cost, Ksh28.00. ..."
 *
 * 6. Buy Airtime (self):
 *    "ABC123XYZ confirmed. You bought Ksh100.00 of airtime on 15/1/24 at 5:00 PM. ..."
 *
 * 7. Buy Airtime (other):
 *    "ABC123XYZ confirmed. You bought Ksh50.00 of airtime for 0798765432 on 15/1/24 at 5:30 PM. ..."
 *
 * 8. Fuliza Send:
 *    "ABC123XYZ Confirmed. Ksh500.00 Fuliza M-PESA amount sent to John Doe 0712345678 on ..."
 *
 * NOT parsed (not expenses):
 *    - Receive Money: "You have received Ksh1,000.00 from ..."
 *    - Deposit: "You have deposited Ksh2,000.00 ..."
 *    - Reversal: "Transaction ABC987ZYX has been reversed ..."
 */
object SmsParser {

    private const val TAG = "SmsParser"

    /**
     * Category ID for "Mpesa Transaction Cost" in the default categories.
     * This is defined in DefaultCategories (Financial > Mpesa Transaction Cost).
     */
    const val MPESA_TRANSACTION_COST_CATEGORY_ID = 811L

    // M-PESA sender IDs
    private val MPESA_SENDERS = listOf("MPESA", "M-PESA", "Safaricom")

    // ==================== Regex Patterns ====================

    // Transaction ID: 10-character alphanumeric code at the start
    private val TRANSACTION_ID_PATTERN = Pattern.compile("^([A-Z0-9]{10})")

    // Amount: Ksh followed by digits with optional commas and decimal
    private val AMOUNT_PATTERN = Pattern.compile("Ksh([\\d,]+(?:\\.\\d{2})?)")

    // Transaction cost: "Transaction cost, Ksh53.00"
    private val TRANSACTION_COST_PATTERN = Pattern.compile(
        "Transaction cost,?\\s*Ksh([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE
    )

    // Date/Time: "on 15/1/24 at 12:34 PM" or "on 11/3/26 at 10:31 AM"
    private val DATE_PATTERN = Pattern.compile("on (\\d{1,2}/\\d{1,2}/\\d{2,4}) at (\\d{1,2}:\\d{2} [AP]M)")

    // --- Non-expense patterns (detect & skip) ---

    // Receive Money: "You have received Ksh..."
    private val RECEIVE_PATTERN = Pattern.compile(
        "You have received", Pattern.CASE_INSENSITIVE
    )

    // Deposit: "You have deposited Ksh..."
    private val DEPOSIT_PATTERN = Pattern.compile(
        "You have deposited", Pattern.CASE_INSENSITIVE
    )

    // Reversal: "has been reversed"
    private val REVERSAL_PATTERN = Pattern.compile(
        "has been reversed", Pattern.CASE_INSENSITIVE
    )

    // --- Expense transaction patterns (ordered most specific → least specific) ---

    // Withdraw from Agent: "You have withdrawn Ksh1,000.00 from 123456 - AGENT NAME on"
    private val WITHDRAW_PATTERN = Pattern.compile(
        "withdrawn Ksh[\\d,]+(?:\\.\\d{2})? from (\\d+)\\s*-\\s*(.+?)\\s+on",
        Pattern.CASE_INSENSITIVE
    )

    // Buy Airtime for other: "You bought Ksh50.00 of airtime for 0798765432"
    private val AIRTIME_OTHER_PATTERN = Pattern.compile(
        "bought Ksh[\\d,]+(?:\\.\\d{2})? of airtime for (\\d{10,12})",
        Pattern.CASE_INSENSITIVE
    )

    // Buy Airtime for self: "You bought Ksh100.00 of airtime on"
    private val AIRTIME_SELF_PATTERN = Pattern.compile(
        "bought Ksh[\\d,]+(?:\\.\\d{2})? of airtime on", Pattern.CASE_INSENSITIVE
    )

    // M-PESA Card: "sent to M-PESA CARD for account HU HBS ONLINE 617-496-6355 US on"
    private val MPESA_CARD_PATTERN = Pattern.compile(
        "sent to M-PESA CARD for account (.+?) on", Pattern.CASE_INSENSITIVE
    )

    // Pay Bill: "sent to NABO CAPITAL LTD C2B for account PG5QWT on"
    // Must be checked AFTER M-PESA Card (which also has "for account")
    private val PAY_BILL_PATTERN = Pattern.compile(
        "sent to (.+?) for account (.+?) on", Pattern.CASE_INSENSITIVE
    )

    // Fuliza Send: "Fuliza M-PESA amount sent to John Doe 0712345678 on"
    private val FULIZA_SEND_PATTERN = Pattern.compile(
        "Fuliza.*?sent to (.+?)\\s+(\\d{10,12})\\s+on", Pattern.CASE_INSENSITIVE
    )

    // Send Money: "sent to DIBON SEWE 0722636142 on"
    // Must be checked AFTER Pay Bill and Fuliza (which also use "sent to")
    private val SEND_MONEY_PATTERN = Pattern.compile(
        "sent to (.+?)\\s+(\\d{10,12})\\s+on", Pattern.CASE_INSENSITIVE
    )

    // Buy Goods (Till): "paid to sarah k ltd. on"
    // Note: Buy Goods uses "paid to" with a period before "on"
    private val BUY_GOODS_PATTERN = Pattern.compile(
        "paid to (.+?)\\.\\s*on", Pattern.CASE_INSENSITIVE
    )

    // ==================== Public API ====================

    /**
     * Result of parsing an M-PESA SMS.
     *
     * @property expense The main transaction expense
     * @property transactionCost Optional separate expense for the M-PESA transaction cost.
     *   Auto-categorized under "Mpesa Transaction Cost" (category 811).
     *   Only present when the SMS contains "Transaction cost, KshXX.XX" with amount > 0.
     */
    data class ParsedTransaction(
        val expense: Expense,
        val transactionCost: Expense?
    )

    /**
     * Check if SMS is from M-PESA
     */
    fun isMpesaSms(sender: String?): Boolean {
        if (sender == null) return false
        return MPESA_SENDERS.any { sender.contains(it, ignoreCase = true) }
    }

    /**
     * Check if SMS is a transaction confirmation (any type).
     * Broadened check — detailed classification happens in parseSms().
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
     * Parse M-PESA SMS into a ParsedTransaction containing the main expense
     * and an optional transaction cost expense.
     *
     * Returns null for:
     * - Non-transaction SMS
     * - Receive Money (income, not expense)
     * - Deposit (not expense)
     * - Reversal (not expense)
     * - Unrecognized formats
     *
     * @param message The SMS message body
     * @return ParsedTransaction if parsing successful, null otherwise
     */
    fun parseSms(message: String): ParsedTransaction? {
        // Quick pre-check
        if (!message.contains("Confirmed", ignoreCase = true)) {
            return null
        }

        // Skip non-expense transactions
        if (RECEIVE_PATTERN.matcher(message).find()) {
            Log.d(TAG, "Skipping receive money SMS (not an expense)")
            return null
        }
        if (DEPOSIT_PATTERN.matcher(message).find()) {
            Log.d(TAG, "Skipping deposit SMS (not an expense)")
            return null
        }
        if (REVERSAL_PATTERN.matcher(message).find()) {
            Log.d(TAG, "Skipping reversal SMS (not an expense)")
            return null
        }

        // Must pass transaction check
        if (!isTransactionSms(message)) {
            Log.d(TAG, "Not a transaction SMS")
            return null
        }

        try {
            // Extract transaction ID
            val transactionId = extractTransactionId(message)
            if (transactionId == null) {
                Log.w(TAG, "Could not extract transaction ID from: ${message.take(60)}...")
                return null
            }

            // Extract amount
            val amount = extractAmount(message)
            if (amount == null) {
                Log.w(TAG, "Could not extract amount from: ${message.take(60)}...")
                return null
            }

            // Classify transaction and extract recipient info
            val txInfo = classifyTransaction(message)
            if (txInfo == null) {
                Log.w(TAG, "Could not classify transaction: ${message.take(80)}...")
                return null
            }

            // Extract timestamp
            val timestamp = extractTimestamp(message) ?: System.currentTimeMillis()

            // Build main expense
            val mainExpense = Expense(
                transactionId = transactionId,
                amount = amount,
                recipient = txInfo.recipient,
                recipientName = txInfo.recipientName,
                paymentType = txInfo.paymentType,
                source = ExpenseSource.SMS_PARSED,
                timestamp = timestamp,
                isCategorized = false
            )

            // Extract transaction cost (if present and > 0)
            val transactionCostExpense = extractTransactionCost(message)?.let { cost ->
                if (cost > 0.0) {
                    Expense(
                        // Use a derived transaction ID so it's unique but linked
                        transactionId = "${transactionId}_COST",
                        amount = cost,
                        recipient = "Safaricom",
                        recipientName = "M-PESA Transaction Cost",
                        paymentType = PaymentType.TRANSACTION_COST,
                        source = ExpenseSource.SMS_PARSED,
                        timestamp = timestamp,
                        // Auto-categorize under "Mpesa Transaction Cost" (category 811)
                        categoryId = MPESA_TRANSACTION_COST_CATEGORY_ID,
                        isCategorized = true
                    )
                } else null
            }

            return ParsedTransaction(
                expense = mainExpense,
                transactionCost = transactionCostExpense
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SMS: ${e.message}", e)
            return null
        }
    }

    // ==================== Extraction Helpers ====================

    /**
     * Extract transaction ID (10 alphanumeric chars at start of message)
     */
    private fun extractTransactionId(message: String): String? {
        val matcher = TRANSACTION_ID_PATTERN.matcher(message.trim())
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Extract the first amount (Ksh...) from the message
     */
    private fun extractAmount(message: String): Double? {
        val matcher = AMOUNT_PATTERN.matcher(message)
        return if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            amountStr?.toDoubleOrNull()
        } else null
    }

    /**
     * Extract transaction cost from "Transaction cost, Ksh53.00"
     */
    private fun extractTransactionCost(message: String): Double? {
        val matcher = TRANSACTION_COST_PATTERN.matcher(message)
        return if (matcher.find()) {
            val costStr = matcher.group(1)?.replace(",", "")
            costStr?.toDoubleOrNull()
        } else null
    }

    /**
     * Classify the transaction type and extract recipient details.
     *
     * Pattern matching order matters — more specific patterns must be
     * checked first to avoid false matches.
     *
     * Order:
     * 1. Withdraw (unique "withdrawn ... from")
     * 2. Airtime other (unique "airtime for PHONE")
     * 3. Airtime self (unique "airtime on")
     * 4. M-PESA Card (specific "sent to M-PESA CARD for account")
     * 5. Pay Bill ("sent to NAME for account") — before Send Money
     * 6. Fuliza Send ("Fuliza...sent to NAME PHONE") — before Send Money
     * 7. Send Money ("sent to NAME PHONE") — generic "sent to"
     * 8. Buy Goods ("paid to NAME. on") — only "paid to"
     */
    private fun classifyTransaction(message: String): TransactionInfo? {

        // 1. Withdraw from Agent
        WITHDRAW_PATTERN.matcher(message).let { m ->
            if (m.find()) {
                val agentNumber = m.group(1)?.trim()
                val agentName = m.group(2)?.trim()
                return TransactionInfo(
                    paymentType = PaymentType.WITHDRAW,
                    recipient = agentNumber ?: "",
                    recipientName = agentName
                )
            }
        }

        // 2. Buy Airtime for other
        AIRTIME_OTHER_PATTERN.matcher(message).let { m ->
            if (m.find()) {
                val phone = m.group(1)?.trim()
                return TransactionInfo(
                    paymentType = PaymentType.AIRTIME,
                    recipient = phone ?: "Other",
                    recipientName = "Airtime for $phone"
                )
            }
        }

        // 3. Buy Airtime for self
        AIRTIME_SELF_PATTERN.matcher(message).let { m ->
            if (m.find()) {
                return TransactionInfo(
                    paymentType = PaymentType.AIRTIME,
                    recipient = "Self",
                    recipientName = "Airtime (Self)"
                )
            }
        }

        // 4. M-PESA Card (must be before PayBill since both have "for account")
        MPESA_CARD_PATTERN.matcher(message).let { m ->
            if (m.find()) {
                val accountDetails = m.group(1)?.trim()
                return TransactionInfo(
                    paymentType = PaymentType.MPESA_CARD,
                    recipient = accountDetails ?: "M-PESA Card",
                    recipientName = accountDetails
                )
            }
        }

        // 5. Pay Bill (must be before Send Money since both use "sent to")
        PAY_BILL_PATTERN.matcher(message).let { m ->
            if (m.find()) {
                val businessName = m.group(1)?.trim()
                val accountNumber = m.group(2)?.trim()
                return TransactionInfo(
                    paymentType = PaymentType.PAY_BILL,
                    recipient = accountNumber ?: businessName ?: "",
                    recipientName = businessName
                )
            }
        }

        // 6. Fuliza Send Money
        FULIZA_SEND_PATTERN.matcher(message).let { m ->
            if (m.find()) {
                val recipientName = m.group(1)?.trim()
                val phoneNumber = m.group(2)?.trim()
                return TransactionInfo(
                    paymentType = PaymentType.SEND_MONEY,
                    recipient = phoneNumber ?: "",
                    recipientName = recipientName?.let { "$it (Fuliza)" }
                )
            }
        }

        // 7. Send Money (generic "sent to NAME PHONE")
        SEND_MONEY_PATTERN.matcher(message).let { m ->
            if (m.find()) {
                val recipientName = m.group(1)?.trim()
                val phoneNumber = m.group(2)?.trim()
                return TransactionInfo(
                    paymentType = PaymentType.SEND_MONEY,
                    recipient = phoneNumber ?: "",
                    recipientName = recipientName
                )
            }
        }

        // 8. Buy Goods (Till) — "paid to NAME. on"
        BUY_GOODS_PATTERN.matcher(message).let { m ->
            if (m.find()) {
                val recipientName = m.group(1)?.trim()
                return TransactionInfo(
                    paymentType = PaymentType.BUY_GOODS,
                    recipient = recipientName ?: "",
                    recipientName = recipientName
                )
            }
        }

        return null
    }

    /**
     * Extract timestamp from "on dd/M/yy at h:mm AM/PM"
     */
    private fun extractTimestamp(message: String): Long? {
        val matcher = DATE_PATTERN.matcher(message)
        if (matcher.find()) {
            val dateStr = matcher.group(1)
            val timeStr = matcher.group(2)

            return try {
                val combinedDateTime = "$dateStr $timeStr"

                // Try different date formats M-PESA uses
                val formats = listOf(
                    SimpleDateFormat("d/M/yy h:mm a", Locale.US),
                    SimpleDateFormat("d/M/yyyy h:mm a", Locale.US),
                    SimpleDateFormat("dd/MM/yy h:mm a", Locale.US),
                    SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.US)
                )

                for (format in formats) {
                    try {
                        return format.parse(combinedDateTime)?.time
                    } catch (_: Exception) {
                        continue
                    }
                }

                System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }

        return null
    }

    // ==================== Internal Data Class ====================

    /**
     * Intermediate result of transaction classification
     */
    private data class TransactionInfo(
        val paymentType: PaymentType,
        val recipient: String,
        val recipientName: String?
    )
}
