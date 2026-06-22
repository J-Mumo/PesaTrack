package com.pesatrack.utils.parsers

import android.util.Log
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.domain.models.IncomeTransaction
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

    // --- Income patterns (detect & emit as IncomeResult) ---
    // Tightest patterns first so e.g. "Salary Payment from" wins over the generic "You have received".
    private val salaryPattern = Pattern.compile(
        "Salary Payment from\\s+(.+?)\\s+(?:on|via|\\.|New)", Pattern.CASE_INSENSITIVE
    )
    private val businessIncomePattern = Pattern.compile(
        "Business Payment from\\s+(.+?)\\s+(?:on|via|\\.|New)", Pattern.CASE_INSENSITIVE
    )
    private val fundsReceivedPattern = Pattern.compile(
        "Funds received from\\s+(.+?)\\s+(?:on|via|\\.|New)", Pattern.CASE_INSENSITIVE
    )
    // Peer receive: "You have received Ksh1,000.00 from JOHN DOE 254712345678 on ..."
    private val receiveFromPersonPattern = Pattern.compile(
        "You have received Ksh[\\d,]+(?:\\.\\d{2})?\\s+from\\s+(.+?)(?:\\s+(\\d{10,12}))?\\s+on",
        Pattern.CASE_INSENSITIVE
    )
    private val receiveGenericPattern = Pattern.compile("You have received", Pattern.CASE_INSENSITIVE)
    // M-Shwari -> M-PESA self transfer
    private val mshwariWithdrawPattern = Pattern.compile(
        "(?:M-?Shwari\\s+Withdraw|transferred from M-?Shwari to M-?PESA)", Pattern.CASE_INSENSITIVE
    )
    // Agent deposit (self top-up)
    private val depositPattern = Pattern.compile(
        "You have deposited Ksh[\\d,]+(?:\\.\\d{2})?(?:\\s+to your M-?PESA account)?(?:\\s+at\\s+(\\d+)\\s*-?\\s*(.+?))?\\s+on",
        Pattern.CASE_INSENSITIVE
    )
    // Offnet B2C — often a payroll from a non-M-PESA source
    private val offnetB2cPattern = Pattern.compile(
        "Offnet B2C Transfer", Pattern.CASE_INSENSITIVE
    )

    // Reversals — still skipped at the parser level (see plan §5.2). The
    // "reversal-as-exclude" rule belongs to the receiver and needs the
    // original txn id, which the SMS doesn't always carry.
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

        // Match any expense OR income keyword
        return body.contains("sent to", ignoreCase = true) ||
                body.contains("paid to", ignoreCase = true) ||
                body.contains("withdrawn", ignoreCase = true) ||
                body.contains("of airtime", ignoreCase = true) ||
                body.contains("Fuliza", ignoreCase = true) ||
                body.contains("bought", ignoreCase = true) ||
                body.contains("You have received", ignoreCase = true) ||
                body.contains("You have deposited", ignoreCase = true) ||
                body.contains("Salary Payment from", ignoreCase = true) ||
                body.contains("Business Payment from", ignoreCase = true) ||
                body.contains("Funds received from", ignoreCase = true) ||
                body.contains("M-Shwari", ignoreCase = true) ||
                body.contains("Offnet B2C", ignoreCase = true)
    }

    override fun parseSms(body: String, smsDate: Long): ParsedSms {
        // Quick pre-check
        if (!body.contains("Confirmed", ignoreCase = true)) {
            return ParsedSms.NotARelevantMessage
        }

        // Reversals — defer to follow-on (need original tx id for exclude-not-income rule).
        if (reversalPattern.matcher(body).find()) {
            Log.d(TAG, "Skipping reversal SMS (not yet handled)")
            return ParsedSms.NotARelevantMessage
        }

        // Income detection runs before expense classification.
        val income = tryParseIncome(body, smsDate)
        if (income != null) return ParsedSms.IncomeResult(income)

        return tryParseExpense(body, smsDate) ?: ParsedSms.NotARelevantMessage
    }

    private fun tryParseExpense(body: String, smsDate: Long): ParsedSms.ExpenseResult? {

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

            return ParsedSms.ExpenseResult(
                expense = mainExpense,
                transactionCost = transactionCostExpense
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SMS: ${e.message}", e)
            return null
        }
    }

    // ==================== Income Detection ====================

    /**
     * Try to parse [body] as an income (incoming-money) SMS. Returns null when
     * the body is not income-shaped — caller then attempts expense parsing.
     */
    private fun tryParseIncome(body: String, smsDate: Long): IncomeTransaction? {
        val source: IncomeSource
        val sender: String?

        val salaryMatcher = salaryPattern.matcher(body)
        val businessMatcher = businessIncomePattern.matcher(body)
        val fundsMatcher = fundsReceivedPattern.matcher(body)
        val depositMatcher = depositPattern.matcher(body)
        val personMatcher = receiveFromPersonPattern.matcher(body)

        when {
            salaryMatcher.find() -> {
                source = IncomeSource.SALARY
                sender = salaryMatcher.group(1)?.trim()
            }
            businessMatcher.find() -> {
                source = IncomeSource.BUSINESS
                sender = businessMatcher.group(1)?.trim()
            }
            mshwariWithdrawPattern.matcher(body).find() -> {
                source = IncomeSource.TRANSFER_IN
                sender = "M-Shwari"
            }
            depositMatcher.find() -> {
                source = IncomeSource.TRANSFER_IN
                sender = depositMatcher.group(2)?.trim() ?: "Agent deposit"
            }
            fundsMatcher.find() -> {
                source = IncomeSource.UNCATEGORIZED
                sender = fundsMatcher.group(1)?.trim()
            }
            offnetB2cPattern.matcher(body).find() -> {
                source = IncomeSource.UNCATEGORIZED
                sender = null
            }
            personMatcher.find() -> {
                source = IncomeSource.UNCATEGORIZED
                val name = personMatcher.group(1)?.trim()
                val phone = personMatcher.group(2)?.trim()
                sender = listOfNotNull(name, phone)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() }
            }
            receiveGenericPattern.matcher(body).find() -> {
                source = IncomeSource.UNCATEGORIZED
                sender = null
            }
            else -> return null
        }

        val transactionId = extractTransactionId(body) ?: return null
        val amount = extractAmount(body) ?: return null
        val timestamp = extractTimestamp(body) ?: smsDate

        Log.d(
            TAG,
            "Parsed income: Ksh$amount source=${source.name} sender=$sender txid=$transactionId"
        )
        return IncomeTransaction(
            transactionId = transactionId,
            amount = amount,
            timestamp = timestamp,
            source = source,
            sender = sender,
            parserSource = "MPESA",
            isCategorized = source != IncomeSource.UNCATEGORIZED
        )
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
