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
 * NCBA Bank SMS Parser — implements [SmsParserStrategy].
 *
 * Parses NCBA Bank confirmation SMS messages for M-PESA transactions
 * initiated through the NCBA banking app.
 *
 * NCBA sends **paired SMS** for each transaction:
 * 1. Generic debit notification: "Your account 763****018 has been debited..." → SKIPPED
 * 2. Detailed confirmation: "Dear NAME, your MPESA transfer..." → PARSED
 *
 * Only the detailed confirmation is parsed (has recipient info + M-PESA ref).
 * The generic debit is skipped (duplicate, less info).
 *
 * Supported SMS types:
 * - Send Money: "MPESA transfer of KES. 20000.00 to Mary Nduta Kungu (254790518661)"
 * - Buy Goods (Till): "Mpesa Till transfer of KES 3660 to 8933372 THE FIG AND OLIVE"
 * - Pay Bill: "Mpesa Paybill transfer of KES 1000 to AFRICAN INLAND CHURCH 87 account..."
 *
 * Skipped (not expenses):
 * - Self-transfer: "MPESA transfer of KES. 15000.00 has been processed" (no recipient = bank→M-PESA)
 * - Generic debits: "Your account ... has been debited"
 * - Credits: "has been credited"
 *
 * Deduplication: NCBA M-PESA transactions share the same M-PESA ref as direct
 * M-PESA SMS. The transactionId uniqueness constraint handles duplicates automatically.
 */
class NcbaBankParser : SmsParserStrategy {

    override val displayName: String = "NCBA Bank"

    override val senderIds: List<String> = listOf("NCBA_BANK")

    override val expenseSource: ExpenseSource = ExpenseSource.SMS_BANK

    // ==================== Regex Patterns ====================

    // --- Skip patterns ---

    // Generic debit notification (skip — duplicate, less info)
    private val genericDebitPattern = Pattern.compile(
        "Your account.*has been debited", Pattern.CASE_INSENSITIVE
    )

    // Credit notification (skip — not an expense)
    private val creditPattern = Pattern.compile(
        "has been credited", Pattern.CASE_INSENSITIVE
    )

    // --- Expense patterns (ordered most specific → least specific) ---

    // Send Money with recipient: "MPESA transfer of KES. 20000.00 to Mary Nduta Kungu (254790518661)...MPESA ref number UCCOO8W1AW"
    private val sendMoneyWithRecipientPattern = Pattern.compile(
        "MPESA transfer of KES\\.?\\s*([\\d,]+(?:\\.\\d{2})?)\\s+to\\s+(.+?)\\s*\\((\\d+)\\).*?MPESA ref number\\s*([A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    // Self-transfer (no recipient): "MPESA transfer of KES. 15000.00 has been processed...MPESA ref number UCB048VYQ9"
    // This is a bank → own M-PESA wallet transfer. SKIP.
    private val selfTransferPattern = Pattern.compile(
        "MPESA transfer of KES\\.?\\s*[\\d,]+(?:\\.\\d{2})?.*?processed.*?MPESA ref number\\s*([A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    // Till payment: "Mpesa Till transfer of KES 3660 to 8933372 THE FIG AND OLIVE LIMITED 1 BANK REF. FTX26067ECFBF MPESA REF. UC8SG99R4R"
    private val tillPaymentPattern = Pattern.compile(
        "Mpesa Till transfer of KES\\s*([\\d,]+(?:\\.\\d{2})?)\\s+to\\s+(\\d+)\\s+(.+?)\\s+BANK REF\\.\\s*(\\S+)\\s+MPESA REF\\.\\s*([A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    // Paybill payment — two known NCBA formats:
    // Format A: "Mpesa Paybill transfer of KES 1000 to AFRICAN INLAND CHURCH KINOO 87 account number Offering BANK REF. ... MPESA REF. ..."
    //   → recipientName = "AFRICAN INLAND CHURCH KINOO", paybillNumber = "87", accountNumber = "Offering"
    // Format B: "Mpesa Paybill transfer of KES 3150 to Lipa na KCB account number 7575077 BANK REF. ... MPESA REF. UCMSG9YPUB was successful..."
    //   → recipientName = "Lipa na KCB", paybillNumber = N/A, accountNumber = "7575077"

    // Format A: business name followed by a standalone paybill number (digits) before "account"
    private val paybillPatternA = Pattern.compile(
        "Mpesa Paybill transfer of KES\\s*([\\d,]+(?:\\.\\d{2})?)\\s+to\\s+(.+?)\\s+(\\d+)\\s+account\\s+(?:number\\s+)?(.+?)\\s+BANK REF\\.\\s*(\\S+)\\s+MPESA REF\\.\\s*([A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    // Format B: business name directly followed by "account number" (no separate paybill number)
    private val paybillPatternB = Pattern.compile(
        "Mpesa Paybill transfer of KES\\s*([\\d,]+(?:\\.\\d{2})?)\\s+to\\s+(.+?)\\s+account\\s+(?:number\\s+)?(.+?)\\s+BANK REF\\.\\s*(\\S+)\\s+MPESA REF\\.\\s*([A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    // Fallback: extract M-PESA ref from any NCBA SMS
    private val mpesaRefPattern = Pattern.compile(
        "MPESA (?:ref number|REF\\.)\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE
    )

    // Fallback: extract bank ref
    private val bankRefPattern = Pattern.compile(
        "(?:BANK REF\\.|Ref:)\\s*(\\S+)", Pattern.CASE_INSENSITIVE
    )

    // Date pattern for NCBA debit SMS: "on 12/03/2026 at 08:43"
    private val datePattern = Pattern.compile(
        "on (\\d{1,2}/\\d{2}/\\d{4}) at (\\d{2}:\\d{2})"
    )

    // ==================== SmsParserStrategy Implementation ====================

    override fun canHandle(sender: String, body: String): Boolean {
        val isNcba = senderIds.any { sender.contains(it, ignoreCase = true) }
        if (!isNcba) return false

        // Must be a detailed confirmation (not just a generic debit)
        // Look for keywords that indicate it's a parseable NCBA transaction SMS
        return body.contains("MPESA transfer", ignoreCase = true) ||
                body.contains("Mpesa Till transfer", ignoreCase = true) ||
                body.contains("Mpesa Paybill transfer", ignoreCase = true)
    }

    override fun parse(body: String, smsDate: Long): SmsParser.ParsedTransaction? {
        // Skip generic debit notifications
        if (genericDebitPattern.matcher(body).find()) {
            Log.d(TAG, "Skipping NCBA generic debit notification")
            return null
        }

        // Skip credit notifications
        if (creditPattern.matcher(body).find()) {
            Log.d(TAG, "Skipping NCBA credit notification (not an expense)")
            return null
        }

        try {
            // Try each pattern in order of specificity

            // 1. Till payment (Buy Goods)
            tillPaymentPattern.matcher(body).let { m ->
                if (m.find()) {
                    val amount = parseAmount(m.group(1))
                    val tillNumber = m.group(2)?.trim() ?: ""
                    val recipientName = m.group(3)?.trim()
                    val bankRef = m.group(4)?.trim()
                    val mpesaRef = m.group(5)?.trim()

                    if (amount != null) {
                        return SmsParser.ParsedTransaction(
                            expense = Expense(
                                transactionId = mpesaRef ?: bankRef,
                                amount = amount,
                                recipient = tillNumber,
                                recipientName = recipientName,
                                paymentType = PaymentType.BUY_GOODS,
                                source = expenseSource,
                                timestamp = smsDate,
                                isCategorized = false
                            ),
                            transactionCost = null // NCBA doesn't report M-PESA costs
                        )
                    }
                }
            }

            // 2a. Paybill payment — Format A (with explicit paybill number before "account")
            paybillPatternA.matcher(body).let { m ->
                if (m.find()) {
                    val amount = parseAmount(m.group(1))
                    val recipientName = m.group(2)?.trim()
                    val paybillNumber = m.group(3)?.trim() ?: ""
                    val accountNumber = m.group(4)?.trim()
                    val bankRef = m.group(5)?.trim()
                    val mpesaRef = m.group(6)?.trim()

                    if (amount != null) {
                        return SmsParser.ParsedTransaction(
                            expense = Expense(
                                transactionId = mpesaRef ?: bankRef,
                                amount = amount,
                                recipient = accountNumber ?: paybillNumber,
                                recipientName = recipientName,
                                paymentType = PaymentType.PAY_BILL,
                                source = expenseSource,
                                notes = "Paybill: $paybillNumber, Account: $accountNumber",
                                timestamp = smsDate,
                                isCategorized = false
                            ),
                            transactionCost = null
                        )
                    }
                }
            }

            // 2b. Paybill payment — Format B (business name directly before "account number")
            paybillPatternB.matcher(body).let { m ->
                if (m.find()) {
                    val amount = parseAmount(m.group(1))
                    val recipientName = m.group(2)?.trim()
                    val accountNumber = m.group(3)?.trim()
                    val bankRef = m.group(4)?.trim()
                    val mpesaRef = m.group(5)?.trim()

                    if (amount != null) {
                        return SmsParser.ParsedTransaction(
                            expense = Expense(
                                transactionId = mpesaRef ?: bankRef,
                                amount = amount,
                                recipient = accountNumber ?: "",
                                recipientName = recipientName,
                                paymentType = PaymentType.PAY_BILL,
                                source = expenseSource,
                                notes = "Account: $accountNumber",
                                timestamp = smsDate,
                                isCategorized = false
                            ),
                            transactionCost = null
                        )
                    }
                }
            }

            // 3. Send Money with recipient (must check BEFORE self-transfer)
            sendMoneyWithRecipientPattern.matcher(body).let { m ->
                if (m.find()) {
                    val amount = parseAmount(m.group(1))
                    val recipientName = m.group(2)?.trim()
                    val phone = m.group(3)?.trim() ?: ""
                    val mpesaRef = m.group(4)?.trim()

                    if (amount != null) {
                        return SmsParser.ParsedTransaction(
                            expense = Expense(
                                transactionId = mpesaRef,
                                amount = amount,
                                recipient = phone,
                                recipientName = recipientName,
                                paymentType = PaymentType.SEND_MONEY,
                                source = expenseSource,
                                timestamp = smsDate,
                                isCategorized = false
                            ),
                            transactionCost = null
                        )
                    }
                }
            }

            // 4. Self-transfer (no recipient) — SKIP, not an expense
            if (selfTransferPattern.matcher(body).find()) {
                Log.d(TAG, "Skipping NCBA self-transfer (bank → own M-PESA)")
                return null
            }

            Log.d(TAG, "Unrecognized NCBA SMS format: ${body.take(80)}...")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing NCBA SMS: ${e.message}", e)
            return null
        }
    }

    // ==================== Helpers ====================

    /**
     * Parse amount string like "20000.00" or "3,660.00" or "3660" to Double
     */
    private fun parseAmount(amountStr: String?): Double? {
        if (amountStr == null) return null
        return amountStr.replace(",", "").toDoubleOrNull()
    }

    companion object {
        private const val TAG = "NcbaBankParser"
    }
}
