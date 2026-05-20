package com.pesatrack.utils.parsers

import android.util.Log
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.utils.SmsParser
import java.util.regex.Pattern

/**
 * NCBA Bank SMS Parser — implements [SmsParserStrategy].
 *
 * Parses NCBA Bank confirmation SMS messages for M-PESA transactions
 * initiated through the NCBA banking app.
 *
 * NCBA sends **paired SMS** for each transaction:
 * 1. Generic debit notification: "Your account 763****018 has been debited..." → SKIPPED
 * 2. Detailed confirmation: "Dear NAME, your MPESA transfer..." or "Mpesa Till/Paybill transfer..." → PARSED
 *
 * Only the detailed confirmation is parsed (has recipient info + M-PESA ref).
 * The generic debit is skipped (duplicate, less info).
 *
 * Supported SMS types:
 *
 * **Send Money:**
 * - "MPESA transfer of KES. 20000.00 to Mary Nduta Kungu (254790518661)...MPESA ref number UCCOO8W1AW"
 *
 * **Buy Goods (Till):**
 * - Format A (with till number): "Mpesa Till transfer of KES 3660 to 8933372 THE FIG AND OLIVE LIMITED 1 BANK REF. FTX26067ECFBF MPESA REF. UC8SG99R4R"
 * - Format B (name only):        "Mpesa Till transfer of KES 1737.00 to JAZA MUTHIGA BANK REF. FTX26115UARQT MPESA REF. UDPSGBHAML was successful..."
 *
 * **Pay Bill:**
 * - Format A (paybill + account): "Mpesa Paybill transfer of KES 1000 to AFRICAN INLAND CHURCH KINOO 87 account number Offering BANK REF. ... MPESA REF. ..."
 * - Format B (account only):      "Mpesa Paybill transfer of KES 3150 to Lipa na KCB account number 7575077 BANK REF. ... MPESA REF. UCMSG9YPUB was successful..."
 * - Format C (name only):         "Mpesa Paybill transfer of KES 50000.00 to BACK TO THE ROOT OF WORSHIP MINISTRY BANK REF. FTX26115UALPI MPESA REF. UDPSGBH4Z2 was successful..."
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

    // Credit notification (skip — not an expense)
    private val creditPattern = Pattern.compile(
        "has been credited", Pattern.CASE_INSENSITIVE
    )

    // Generic debit notification (skip — ALL generic debits are skipped)
    // For card payments, the card approval SMS triggers inbox lookup to get KES amount
    private val genericDebitPattern = Pattern.compile(
        "Your account.*has been debited", Pattern.CASE_INSENSITIVE
    )

    // --- Card payment patterns ---

    // Card approval: "Joel, we have approved a transaction of USD 11.60 at OPENAI on your card no. ending *3462"
    private val cardApprovalPattern = Pattern.compile(
        "approved a transaction of ([A-Z]{3})\\s+([\\d,]+(?:\\.\\d{1,2})?)\\s+at\\s+(.+?)\\s+on your card no\\.\\s*ending\\s*\\*(\\d+)",
        Pattern.CASE_INSENSITIVE
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

    // Till payment Format A (with till number): "Mpesa Till transfer of KES 3660 to 8933372 THE FIG AND OLIVE LIMITED 1 BANK REF. FTX26067ECFBF MPESA REF. UC8SG99R4R"
    private val tillPaymentPatternA = Pattern.compile(
        "Mpesa Till transfer of KES\\s*([\\d,]+(?:\\.\\d{2})?)\\s+to\\s+(\\d+)\\s+(.+?)\\s+BANK REF\\.\\s*(\\S+)\\s+MPESA REF\\.\\s*([A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    // Till payment Format B (name only, no till number): "Mpesa Till transfer of KES 1737.00 to JAZA MUTHIGA BANK REF. FTX26115UARQT MPESA REF. UDPSGBHAML was successful..."
    private val tillPaymentPatternB = Pattern.compile(
        "Mpesa Till transfer of KES\\s*([\\d,]+(?:\\.\\d{2})?)\\s+to\\s+(.+?)\\s+BANK REF\\.\\s*(\\S+)\\s+MPESA REF\\.\\s*([A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    // Paybill payment — three known NCBA formats:
    // Format A: "Mpesa Paybill transfer of KES 1000 to AFRICAN INLAND CHURCH KINOO 87 account number Offering BANK REF. ... MPESA REF. ..."
    //   → recipientName = "AFRICAN INLAND CHURCH KINOO", paybillNumber = "87", accountNumber = "Offering"
    // Format B: "Mpesa Paybill transfer of KES 3150 to Lipa na KCB account number 7575077 BANK REF. ... MPESA REF. UCMSG9YPUB was successful..."
    //   → recipientName = "Lipa na KCB", paybillNumber = N/A, accountNumber = "7575077"
    // Format C: "Mpesa Paybill transfer of KES 50000.00 to BACK TO THE ROOT OF WORSHIP MINISTRY BANK REF. FTX26115UALPI MPESA REF. UDPSGBH4Z2 was successful..."
    //   → recipientName = "BACK TO THE ROOT OF WORSHIP MINISTRY", no paybill/account

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

    // Format C: business name directly followed by BANK REF (no account keyword at all)
    private val paybillPatternC = Pattern.compile(
        "Mpesa Paybill transfer of KES\\s*([\\d,]+(?:\\.\\d{2})?)\\s+to\\s+(.+?)\\s+BANK REF\\.\\s*(\\S+)\\s+MPESA REF\\.\\s*([A-Z0-9]+)",
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

    // ==================== SmsParserStrategy Implementation ====================

    override fun canHandle(sender: String, body: String): Boolean {
        val isNcba = senderIds.any { sender.contains(it, ignoreCase = true) }
        if (!isNcba) return false

        // Parseable NCBA transaction types:
        // 1. M-PESA transfers (Send/Till/Paybill)
        // 2. Card approval alerts
        // 3. Generic debits (handled to explicitly skip them)
        return body.contains("MPESA transfer", ignoreCase = true) ||
                body.contains("Mpesa Till transfer", ignoreCase = true) ||
                body.contains("Mpesa Paybill transfer", ignoreCase = true) ||
                body.contains("approved a transaction of", ignoreCase = true) ||
                body.contains("has been debited", ignoreCase = true)
    }

    override fun parse(body: String, smsDate: Long): SmsParser.ParsedTransaction? {
        // Skip credit notifications
        if (creditPattern.matcher(body).find()) {
            Log.d(TAG, "Skipping NCBA credit notification (not an expense)")
            return null
        }

        try {
            // 0. Skip ALL generic debit notifications.
            // For card payments, the card approval SMS triggers an inbox lookup
            // in SmsReceiver to find the paired debit and extract the KES amount.
            if (genericDebitPattern.matcher(body).find()) {
                Log.d(TAG, "Skipping NCBA generic debit notification")
                return null
            }

            // 1. Card approval: "approved a transaction of USD 11.60 at OPENAI on your card no. ending *3462"
            cardApprovalPattern.matcher(body).let { m ->
                if (m.find()) {
                    val currency = m.group(1)?.trim() ?: "KES"
                    val amount = parseAmount(m.group(2))
                    val merchant = m.group(3)?.trim() ?: "Unknown Merchant"
                    val cardLast4 = m.group(4)?.trim() ?: ""

                    Log.d(TAG, "Parsed NCBA card approval: $currency $amount at $merchant (card *$cardLast4)")

                    return SmsParser.ParsedTransaction(
                        expense = Expense(
                            transactionId = null, // No ref in card approval SMS
                            amount = amount ?: 0.0, // Foreign currency amount as fallback
                            recipient = "*$cardLast4",
                            recipientName = merchant,
                            paymentType = PaymentType.CARD_PAYMENT,
                            source = expenseSource,
                            notes = "$currency ${m.group(2)?.trim()} at $merchant (Card *$cardLast4)",
                            timestamp = smsDate,
                            isCategorized = false
                        ),
                        transactionCost = null,
                        isCardApprovalUpdate = true
                    )
                }
            }

            // Try each M-PESA pattern in order of specificity (most specific first)

            // 1a. Till payment Format A (with till number — more specific, try first)
            tillPaymentPatternA.matcher(body).let { m ->
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

            // 1b. Till payment Format B (name only, no till number)
            tillPaymentPatternB.matcher(body).let { m ->
                if (m.find()) {
                    val amount = parseAmount(m.group(1))
                    val recipientName = m.group(2)?.trim()
                    val bankRef = m.group(3)?.trim()
                    val mpesaRef = m.group(4)?.trim()

                    if (amount != null) {
                        return SmsParser.ParsedTransaction(
                            expense = Expense(
                                transactionId = mpesaRef ?: bankRef,
                                amount = amount,
                                recipient = recipientName ?: "",
                                recipientName = recipientName,
                                paymentType = PaymentType.BUY_GOODS,
                                source = expenseSource,
                                timestamp = smsDate,
                                isCategorized = false
                            ),
                            transactionCost = null
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

            // 2c. Paybill payment — Format C (name only, no account keyword at all)
            paybillPatternC.matcher(body).let { m ->
                if (m.find()) {
                    val amount = parseAmount(m.group(1))
                    val recipientName = m.group(2)?.trim()
                    val bankRef = m.group(3)?.trim()
                    val mpesaRef = m.group(4)?.trim()

                    if (amount != null) {
                        return SmsParser.ParsedTransaction(
                            expense = Expense(
                                transactionId = mpesaRef ?: bankRef,
                                amount = amount,
                                recipient = recipientName ?: "",
                                recipientName = recipientName,
                                paymentType = PaymentType.PAY_BILL,
                                source = expenseSource,
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
