package com.pesatrack.utils.parsers

import android.util.Log
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.utils.SmsParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

/**
 * M-PESA SMS Parser — implements [SmsParserStrategy].
 *
 * Parses M-PESA confirmation SMS messages to extract transaction details.
 * Only parses EXPENSE transactions — Receive Money, Deposit, and Reversal are ignored.
 *
 * Also extracts "Transaction cost, Ksh53.00" and creates a separate auto-categorized
 * expense under "Mpesa Transaction Cost" (category ID 606).
 *
 * Supported SMS formats:
 * 1. Send Money: "sent to NAME PHONE on"
 * 2. Pay Bill: "sent to COMPANY for account ACCT on"
 * 3. Buy Goods (Till): "paid to SHOP. on"
 * 4. M-PESA Card: "sent to M-PESA CARD for account"
 * 5. Withdraw from Agent: "withdrawn ... from AGENT"
 * 6. Buy Airtime (self): "bought ... of airtime on"
 * 7. Buy Airtime (other): "bought ... of airtime for PHONE"
 * 8. Fuliza Send: "Fuliza M-PESA amount sent to"
 */
class MpesaSmsParser : SmsParserStrategy {

    override val displayName: String = "M-PESA"

    override val senderIds: List<String> = listOf("MPESA", "M-PESA", "Safaricom")

    override val expenseSource: ExpenseSource = ExpenseSource.SMS_PARSED

    // ==================== Regex Patterns ====================

    // Transaction ID: 10-character alphanumeric code at the start
    private val transactionIdPattern = Pattern.compile("^([A-Z0-9]{10})")

    // Amount: Ksh followed by digits with optional commas and decimal
    private val amountPattern = Pattern.compile("Ksh([\\d,]+(?:\\.\\d{2})?)")

    // Transaction cost: "Transaction cost, Ksh53.00"
    private val transactionCostPattern = Pattern.compile(
        "Transaction cost,?\\s*Ksh([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE
    )

    // Date/Time: "on 15/1/24 at 12:34 PM" or "on 11/3/26 at 10:31 AM"
    private val datePattern = Pattern.compile("on (\\d{1,2}/\\d{1,2}/\\d{2,4}) at (\\d{1,2}:\\d{2} [AP]M)")

    // --- Non-expense patterns (detect & skip) ---
    private val receivePattern = Pattern.compile("You have received", Pattern.CASE_INSENSITIVE)
    private val depositPattern = Pattern.compile("You have deposited", Pattern.CASE_INSENSITIVE)
    private val reversalPattern = Pattern.compile("has been reversed", Pattern.CASE_INSENSITIVE)

    // --- Expense transaction patterns (ordered most specific → least specific) ---
    private val withdrawPattern = Pattern.compile(
        "withdrawn Ksh[\\d,]+(?:\\.\\d{2})? from (\\d+)\\s*-\\s*(.+?)\\s+on",
        Pattern.CASE_INSENSITIVE
    )
    private val airtimeOtherPattern = Pattern.compile(
        "bought Ksh[\\d,]+(?:\\.\\d{2})? of airtime for (\\d{10,12})",
        Pattern.CASE_INSENSITIVE
    )
    private val airtimeSelfPattern = Pattern.compile(
        "bought Ksh[\\d,]+(?:\\.\\d{2})? of airtime on", Pattern.CASE_INSENSITIVE
    )
    private val mpesaCardPattern = Pattern.compile(
        "sent to M-PESA CARD for account (.+?) on", Pattern.CASE_INSENSITIVE
    )
    private val payBillPattern = Pattern.compile(
        "sent to (.+?) for account (.+?) on", Pattern.CASE_INSENSITIVE
    )
    private val fulizaSendPattern = Pattern.compile(
        "Fuliza.*?sent to (.+?)\\s+(\\d{10,12})\\s+on", Pattern.CASE_INSENSITIVE
    )
    private val sendMoneyPattern = Pattern.compile(
        "sent to (.+?)\\s+(\\d{10,12})\\s+on", Pattern.CASE_INSENSITIVE
    )
    private val buyGoodsPattern = Pattern.compile(
        "paid to (.+?)\\.\\s*on", Pattern.CASE_INSENSITIVE
    )

    // ==================== SmsParserStrategy Implementation ====================

    override fun canHandle(sender: String, body: String): Boolean {
        // Check sender
        val isMpesa = senderIds.any { sender.contains(it, ignoreCase = true) }
        if (!isMpesa) return false

        // Must contain "Confirmed" and "Ksh"
        if (!body.contains("Confirmed", ignoreCase = true)) return false
        if (!body.contains("Ksh", ignoreCase = true)) return false

        // Match any transaction keyword
        return body.contains("sent to", ignoreCase = true) ||
                body.contains("paid to", ignoreCase = true) ||
                body.contains("withdrawn", ignoreCase = true) ||
                body.contains("of airtime", ignoreCase = true) ||
                body.contains("Fuliza", ignoreCase = true) ||
                body.contains("bought", ignoreCase = true)
    }

    override fun parse(body: String, smsDate: Long): SmsParser.ParsedTransaction? {
        // Quick pre-check
        if (!body.contains("Confirmed", ignoreCase = true)) {
            return null
        }

        // Skip non-expense transactions
        if (receivePattern.matcher(body).find()) {
            Log.d(TAG, "Skipping receive money SMS (not an expense)")
            return null
        }
        if (depositPattern.matcher(body).find()) {
            Log.d(TAG, "Skipping deposit SMS (not an expense)")
            return null
        }
        if (reversalPattern.matcher(body).find()) {
            Log.d(TAG, "Skipping reversal SMS (not an expense)")
            return null
        }

        try {
            // Extract transaction ID
            val transactionId = extractTransactionId(body)
            if (transactionId == null) {
                Log.w(TAG, "Could not extract transaction ID from: ${body.take(60)}...")
                return null
            }

            // Extract amount
            val amount = extractAmount(body)
            if (amount == null) {
                Log.w(TAG, "Could not extract amount from: ${body.take(60)}...")
                return null
            }

            // Classify transaction and extract recipient info
            val txInfo = classifyTransaction(body)
            if (txInfo == null) {
                Log.w(TAG, "Could not classify transaction: ${body.take(80)}...")
                return null
            }

            // Extract timestamp — prefer body-parsed date, fall back to SMS received date
            val timestamp = extractTimestamp(body) ?: smsDate

            // Build main expense
            val mainExpense = Expense(
                transactionId = transactionId,
                amount = amount,
                recipient = txInfo.recipient,
                recipientName = txInfo.recipientName,
                paymentType = txInfo.paymentType,
                source = expenseSource,
                timestamp = timestamp,
                isCategorized = false
            )

            // Extract transaction cost (if present and > 0)
            val transactionCostExpense = extractTransactionCost(body)?.let { cost ->
                if (cost > 0.0) {
                    Expense(
                        transactionId = "${transactionId}_COST",
                        amount = cost,
                        recipient = "Safaricom",
                        recipientName = "M-PESA Transaction Cost",
                        paymentType = PaymentType.TRANSACTION_COST,
                        source = expenseSource,
                        timestamp = timestamp,
                        categoryId = SmsParser.MPESA_TRANSACTION_COST_CATEGORY_ID,
                        isCategorized = true
                    )
                } else null
            }

            return SmsParser.ParsedTransaction(
                expense = mainExpense,
                transactionCost = transactionCostExpense
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SMS: ${e.message}", e)
            return null
        }
    }

    // ==================== Extraction Helpers ====================

    private fun extractTransactionId(message: String): String? {
        val matcher = transactionIdPattern.matcher(message.trim())
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractAmount(message: String): Double? {
        val matcher = amountPattern.matcher(message)
        return if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            amountStr?.toDoubleOrNull()
        } else null
    }

    private fun extractTransactionCost(message: String): Double? {
        val matcher = transactionCostPattern.matcher(message)
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
     */
    private fun classifyTransaction(message: String): TransactionInfo? {

        // 1. Withdraw from Agent
        withdrawPattern.matcher(message).let { m ->
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
        airtimeOtherPattern.matcher(message).let { m ->
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
        airtimeSelfPattern.matcher(message).let { m ->
            if (m.find()) {
                return TransactionInfo(
                    paymentType = PaymentType.AIRTIME,
                    recipient = "Self",
                    recipientName = "Airtime (Self)"
                )
            }
        }

        // 4. M-PESA Card (must be before PayBill since both have "for account")
        mpesaCardPattern.matcher(message).let { m ->
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
        payBillPattern.matcher(message).let { m ->
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
        fulizaSendPattern.matcher(message).let { m ->
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
        sendMoneyPattern.matcher(message).let { m ->
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
        buyGoodsPattern.matcher(message).let { m ->
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

    private fun extractTimestamp(message: String): Long? {
        val matcher = datePattern.matcher(message)
        if (matcher.find()) {
            val dateStr = matcher.group(1)
            val timeStr = matcher.group(2)

            return try {
                val combinedDateTime = "$dateStr $timeStr"

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

                null // Let caller use smsDate fallback
            } catch (_: Exception) {
                null // Let caller use smsDate fallback
            }
        }

        return null
    }

    // ==================== Internal Data Class ====================

    private data class TransactionInfo(
        val paymentType: PaymentType,
        val recipient: String,
        val recipientName: String?
    )

    companion object {
        private const val TAG = "MpesaSmsParser"
    }
}
