package com.pesatrack.utils.parsers

import android.util.Log
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.domain.models.IncomeTransaction
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.utils.SmsParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parser for M-PESA PDF statements downloaded from the Safaricom M-PESA app.
 *
 * Handles password-protected PDFs, extracts text, and parses transactions
 * using regex patterns matched against the statement's tabular format.
 *
 * Statement columns: Receipt No. | Completion Time | Details | Transaction Status | Paid In | Withdrawn | Balance
 *
 * Supports 13+ transaction types including:
 * - Customer Transfer (Send Money)
 * - Pay Bill / Pay Bill Online
 * - Merchant Payment / Merchant Payment Online (Buy Goods)
 * - Airtime Purchase / Customer Bundle Purchase / Recharge
 * - Customer Withdrawal At Agent
 * - Customer Payment to Small Business
 * - Card Pay Bill Online (M-PESA GlobalPay)
 * - M-Shwari Deposit / Withdraw
 * - Transaction charges (linked to parent via Receipt No.)
 *
 * Income transactions (Salary Payment, Funds received, etc.) and Reversals are skipped.
 *
 * @see <a href="../plans/mpesa-statement-parser-spec.md">Full specification</a>
 */
object MpesaStatementParser {

    private const val TAG = "MpesaStatementParser"

    /** Date format used in M-PESA statements: "2026-04-29 12:20:13" */
    private val STATEMENT_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // ── Row boundary detection ──────────────────────────────────────────────
    // Receipt No. is 10 alphanumeric chars (like "TDC0JO1R87") followed by date.
    // We use a lookbehind for newline or start-of-string, plus allow optional leading whitespace,
    // since PDFBox may indent lines from the PDF's left margin.
    // NOTE: No trailing \s+ — PDFBox may not have a space between the date and Details,
    // so we only capture receipt+date and trim the rest in code.
    private val ROW_BOUNDARY = Regex("""(?:^|\n)\s*([A-Z0-9]{10,12})\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})""")

    // ── Characters that PDFBox may produce instead of standard asterisk (*) ──
    // PDFBox can extract masked phone numbers with various Unicode characters
    // instead of the standard asterisk (U+002A). We normalize them all to *.
    private val ASTERISK_CHARS = charArrayOf(
        '*',      // U+002A standard asterisk
        '\u2022', // • bullet
        '\u2217', // ∗ asterisk operator
        '\u2731', // ✱ heavy asterisk
        '\u066D', // ٭ arabic five-pointed star
        '\u2023', // ‣ triangular bullet
        '\u25CF', // ● black circle
        '\u2219', // ∙ bullet operator
        '\u00B7', // · middle dot
        '\u2024', // one dot leader
        '\u2027', // hyphenation point
    )

    // Masked phone pattern: allows 4-6 masking chars (PDFBox may extract varying counts)
    // After normalizing asterisks to *, this matches standard masked numbers
    private const val MASKED_PHONE = """\d{2,4}\*{4,8}\d{2,4}"""

    // ── Income / skip patterns (detected from Details field) ────────────────
    private val INCOME_PATTERNS = listOf(
        Regex("""Salary Payment from""", RegexOption.IGNORE_CASE),
        Regex("""Funds received from""", RegexOption.IGNORE_CASE),
        Regex("""M-Shwari Withdraw""", RegexOption.IGNORE_CASE),
        Regex("""Offnet B2C Transfer""", RegexOption.IGNORE_CASE),
        Regex("""Business Payment from""", RegexOption.IGNORE_CASE),
    )

    private val REVERSAL_PATTERN = Regex("""Reversal|Pay Utility Reversal""", RegexOption.IGNORE_CASE)

    // ── Charge patterns ─────────────────────────────────────────────────────
    private val SEND_CHARGE = Regex("""Customer Transfer of Funds\s*Charge""", RegexOption.IGNORE_CASE)
    private val PAYBILL_CHARGE = Regex("""Pay Bill(?:\s+Online)?\s*Charge""", RegexOption.IGNORE_CASE)
    private val MERCHANT_CHARGE = Regex("""(?:Pay )?Merchant(?:\s+Payment)?(?:\s+Online)?\s*Charge""", RegexOption.IGNORE_CASE)
    private val WITHDRAWAL_CHARGE = Regex("""(?:Customer )?Withdrawal\s*Charge""", RegexOption.IGNORE_CASE)

    // ── Expense patterns ────────────────────────────────────────────────────

    // Send Money: "Customer Transfer to 2547******827 JONATHAN NGEI"
    // PDF may insert "-" between "to" and the phone number: "Customer Transfer to - 2547..."
    private val SEND_MONEY = Regex(
        """Customer Transfer to\s+-?\s*($MASKED_PHONE)\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    // Send to Small Business: "Customer Payment to Small Business to - 2547******103 SERAH BORO"
    private val SEND_SMALL_BUSINESS = Regex(
        """Customer Payment to Small\s*Business to\s*-?\s*($MASKED_PHONE)\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    // Pay Bill with dash: "Pay Bill Online to 888880 - KPLC PREPAID Acc. 92106709873"
    // Pay Bill without dash: "Pay Bill Online to 4034615 GLADYS TECHNOLOGIES LIMITED Acc. STREAMS OF"
    private val PAYBILL_WITH_DASH = Regex(
        """Pay Bill(?:\s+Online)?\s+to\s+(\d+)\s+-\s+(.+?)\s+Acc\.?\s*(.+)""",
        RegexOption.IGNORE_CASE
    )
    private val PAYBILL_NO_DASH = Regex(
        """Pay Bill(?:\s+Online)?\s+to\s+(\d+)\s+(.+?)\s+Acc\.?\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    // Merchant Payment with dash: "Merchant Payment Online to 905834 - FAIRMART SUPERMARKET-KIKUYU"
    // Merchant Payment without dash: "Merchant Payment to 7608807 FAIRMART SUPERMARKET LTD."
    private val MERCHANT_WITH_DASH = Regex(
        """Merchant Payment(?:\s+Online)?\s+to\s*(\d+)\s+-\s+(.+)""",
        RegexOption.IGNORE_CASE
    )
    private val MERCHANT_NO_DASH = Regex(
        """Merchant Payment(?:\s+Online)?\s+to\s+(\d+)\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    // Airtime Purchase (self)
    private val AIRTIME_SELF = Regex("""Airtime Purchase""", RegexOption.IGNORE_CASE)

    // Customer Bundle Purchase: "Customer Bundle Purchase to 826915Safaricom Offers by 2547******181 JOEL NGEI"
    // also: "Customer Bundle Purchase to 826915 Safaricom Offers by 2547******181 JOEL NGEI" (with space)
    private val BUNDLE_PURCHASE = Regex(
        """Customer Bundle Purchase to\s*(\d+)\s*(.+?)\s+by\s+($MASKED_PHONE)\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    // Recharge for Customer: "Recharge for Customer to 150501SAFARICOMHOME by 2547******181 JOEL NGEI"
    private val RECHARGE = Regex(
        """Recharge for Customer to\s*(\d+)\s*(.+?)\s+by\s+($MASKED_PHONE)\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    // Agent Withdrawal: "Customer Withdrawal At Agent Till 376065 - Maizma Connect..."
    private val AGENT_WITHDRAWAL = Regex(
        """Customer Withdrawal At Agent\s+Till\s+(\d+)\s+-\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    // M-PESA Card (GlobalPay): "Card Pay Bill Online to 903470 M-PESA GlobalPay Acc. HU HBS ONLINE..."
    private val MPESA_CARD = Regex(
        """Card Pay Bill(?:\s+Online)?\s+to\s+(\d+)\s+(.+?)\s+Acc\.?\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    // M-Shwari Deposit (savings outflow — withdrawn from M-PESA to M-Shwari)
    private val MSHWARI_DEPOSIT = Regex("""M-Shwari Deposit""", RegexOption.IGNORE_CASE)

    // ── Amount extraction ───────────────────────────────────────────────────
    // PDFBox extracts tabular PDFs as space-separated columns. The columns are:
    // Details | Transaction Status | Paid In | Withdrawn | Balance
    //
    // The text after "Completed" will have up to 3 decimal numbers:
    //   "Completed   50,000.00                   123,456.78"  (income: paid_in then balance)
    //   "Completed                8,000.00       123,456.78"  (expense: withdrawn then balance)
    //   "Completed   50,000.00   8,000.00       123,456.78"  (both: rare, but possible)
    //
    // We extract ALL decimal numbers after "Completed" and interpret by position and context.
    private val AMOUNT_NUMBER = Regex("""([\d,]+\.\d{2})""")

    // Balance at the end: last number in the row
    private val BALANCE_PATTERN = Regex("""([\d,]+\.\d{2})\s*$""")

    // ── Page noise to strip ─────────────────────────────────────────────────
    private val PAGE_FOOTER = Regex("""Disclaimer:.*?Page \d+ of \d+\s*[A-Z0-9]+""", RegexOption.DOT_MATCHES_ALL)
    private val HEADER_REPEAT = Regex("""Receipt No\.\s+Completion Time\s+Details\s+Transaction Status\s+Paid In\s+Withdrawn\s+Balance""")

    /**
     * A single parsed row from the M-PESA statement.
     */
    data class StatementRow(
        val receiptNo: String,
        val dateTime: String,
        val details: String,
        val amountWithdrawn: Double?,
        val amountPaidIn: Double?,
        val balance: Double?
    )

    /**
     * Result of parsing a statement row into a PesaTrack expense.
     */
    data class ParsedStatementTransaction(
        val expense: Expense,
        val isCharge: Boolean = false
    )

    /**
     * Header information from the statement.
     */
    data class StatementHeader(
        val customerName: String? = null,
        val mobileNumber: String? = null,
        val email: String? = null,
        val statementPeriod: String? = null,
        val requestDate: String? = null
    )

    /**
     * Full result of parsing a statement.
     */
    data class StatementParseResult(
        val header: StatementHeader,
        val transactions: List<ParsedStatementTransaction>,
        /** Income rows parsed into [IncomeTransaction]s (added in Phase 2). */
        val incomeTransactions: List<IncomeTransaction>,
        val totalRowsParsed: Int,
        val rowsSkippedIncome: Int,
        val rowsSkippedReversal: Int,
        val rowsUnparseable: Int
    )

    /**
     * Extract text from a potentially password-protected M-PESA PDF.
     *
     * @param inputStream The PDF file input stream
     * @param password The PDF password (sent by Safaricom via SMS). Null if unprotected.
     * @return The full extracted text, or null if the PDF could not be opened/decrypted
     */
    fun extractTextFromPdf(inputStream: InputStream, password: String? = null): String? {
        return try {
            val document = if (password != null) {
                PDDocument.load(inputStream, password)
            } else {
                PDDocument.load(inputStream)
            }

            val stripper = PDFTextStripper()
            stripper.sortByPosition = true // Important: sort by position for tabular data
            val text = stripper.getText(document)
            document.close()

            // Debug: log first 1000 chars to understand PDFBox output format
            Log.d(TAG, "PDF extracted text length: ${text.length}")
            Log.d(TAG, "PDF first 1000 chars:\n${text.take(1000)}")

            // Also log a sample from middle of document to see transaction rows
            if (text.length > 2000) {
                Log.d(TAG, "PDF chars 1000-2000:\n${text.substring(1000, minOf(2000, text.length))}")
            }

            text
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text from PDF: ${e.message}", e)
            null
        }
    }

    /**
     * Parse the full statement text into structured transactions.
     *
     * @param text The raw text extracted from the PDF
     * @return StatementParseResult with all parsed transactions and statistics
     */
    fun parseStatementText(text: String): StatementParseResult {
        // 1. Parse header
        val header = parseHeader(text)

        // 2. Clean text — remove page footers, repeated headers
        val cleaned = cleanText(text)

        // 3. Split into rows
        val rows = splitIntoRows(cleaned)
        Log.d(TAG, "Found ${rows.size} statement rows")

        // 4. Parse each row
        var skippedIncome = 0
        var skippedReversal = 0
        var unparseable = 0
        val transactions = mutableListOf<ParsedStatementTransaction>()
        val incomeTransactions = mutableListOf<IncomeTransaction>()

        for (row in rows) {
            val isPaidInRow = row.amountPaidIn != null && row.amountPaidIn > 0 &&
                    (row.amountWithdrawn == null || row.amountWithdrawn == 0.0)
            val matchesIncomePattern = INCOME_PATTERNS.any { it.containsMatchIn(row.details) }

            if (isPaidInRow || matchesIncomePattern) {
                skippedIncome++
                val income = parseIncomeRow(row)
                if (income != null) {
                    incomeTransactions.add(income)
                } else {
                    Log.w(TAG, "Could not parse income row: ${row.receiptNo} | paidIn=${row.amountPaidIn} | ${row.details.take(100)}")
                }
                continue
            }

            // Skip reversals
            if (REVERSAL_PATTERN.containsMatchIn(row.details)) {
                skippedReversal++
                continue
            }

            val parsed = parseRow(row)
            if (parsed != null) {
                transactions.add(parsed)
            } else {
                unparseable++
                Log.w(TAG, "Could not parse row: ${row.receiptNo} | withdrawn=${row.amountWithdrawn} paidIn=${row.amountPaidIn} | ${row.details.take(100)}")
            }
        }

        Log.d(TAG, "Parsed ${transactions.size} expense transactions, ${incomeTransactions.size} income transactions (income-rows=$skippedIncome, reversals=$skippedReversal, unparseable=$unparseable)")

        return StatementParseResult(
            header = header,
            transactions = transactions,
            incomeTransactions = incomeTransactions,
            totalRowsParsed = rows.size,
            rowsSkippedIncome = skippedIncome,
            rowsSkippedReversal = skippedReversal,
            rowsUnparseable = unparseable
        )
    }

    /**
     * Parse a single statement row into an [IncomeTransaction]. Returns null when
     * the row doesn't carry a positive paid-in amount or a parseable timestamp.
     */
    private fun parseIncomeRow(row: StatementRow): IncomeTransaction? {
        val amount = row.amountPaidIn ?: return null
        if (amount <= 0.0) return null

        val (source, sender) = classifyIncomeRow(row.details)

        return IncomeTransaction(
            transactionId = row.receiptNo,
            amount = amount,
            timestamp = parseTimestamp(row.dateTime),
            source = source,
            sender = sender,
            parserSource = "STATEMENT_IMPORT",
            isCategorized = source != IncomeSource.UNCATEGORIZED
        )
    }

    private fun classifyIncomeRow(details: String): Pair<IncomeSource, String?> {
        val normalized = details.trim()
        fun afterPrefix(prefix: String): String? {
            val idx = normalized.indexOf(prefix, ignoreCase = true)
            if (idx < 0) return null
            return normalized.substring(idx + prefix.length).trim().takeIf { it.isNotBlank() }
        }
        return when {
            normalized.contains("Salary Payment from", ignoreCase = true) ->
                IncomeSource.SALARY to afterPrefix("Salary Payment from")
            normalized.contains("Business Payment from", ignoreCase = true) ->
                IncomeSource.BUSINESS to afterPrefix("Business Payment from")
            normalized.contains("M-Shwari Withdraw", ignoreCase = true) ->
                IncomeSource.TRANSFER_IN to "M-Shwari"
            normalized.contains("Offnet B2C Transfer", ignoreCase = true) ->
                IncomeSource.UNCATEGORIZED to afterPrefix("Offnet B2C Transfer")
            normalized.contains("Funds received from", ignoreCase = true) ->
                IncomeSource.UNCATEGORIZED to afterPrefix("Funds received from")
            else -> IncomeSource.UNCATEGORIZED to null
        }
    }

    /**
     * Parse header information from the statement text.
     */
    private fun parseHeader(text: String): StatementHeader {
        val nameMatch = Regex("""Customer Name:\s*(.+)""").find(text)
        val phoneMatch = Regex("""Mobile Number:\s*(.+)""").find(text)
        val emailMatch = Regex("""Email Address:\s*(.+)""").find(text)
        val periodMatch = Regex("""Statement Period:\s*(.+)""").find(text)
        val dateMatch = Regex("""Request Date:\s*(.+)""").find(text)

        return StatementHeader(
            customerName = nameMatch?.groupValues?.get(1)?.trim(),
            mobileNumber = phoneMatch?.groupValues?.get(1)?.trim(),
            email = emailMatch?.groupValues?.get(1)?.trim(),
            statementPeriod = periodMatch?.groupValues?.get(1)?.trim(),
            requestDate = dateMatch?.groupValues?.get(1)?.trim()
        )
    }

    /**
     * Remove page footers, repeated column headers, and other noise from PDF text.
     */
    private fun cleanText(text: String): String {
        var cleaned = text

        // Remove disclaimer blocks + page numbers + verification codes
        cleaned = PAGE_FOOTER.replace(cleaned, "\n")

        // Remove repeated column headers
        cleaned = HEADER_REPEAT.replace(cleaned, "")

        // Remove "SUMMARY" and "DETAILED STATEMENT" section headers
        cleaned = cleaned.replace("DETAILED STATEMENT", "")
        cleaned = cleaned.replace("M-PESA STATEMENT", "")

        return cleaned
    }

    /**
     * Normalize non-standard asterisk/bullet/dot characters to standard asterisk (*).
     *
     * PDFBox may extract the masked phone number asterisks as various Unicode characters
     * depending on the PDF font encoding. For example:
     * - "2547••••••827" (U+2022 bullet)
     * - "2547∗∗∗∗∗∗827" (U+2217 asterisk operator)
     * - "2547······827" (U+00B7 middle dot)
     *
     * This function normalizes them all to standard '*' so regex patterns work consistently.
     */
    private fun normalizeDetails(details: String): String {
        val sb = StringBuilder(details.length)
        for (ch in details) {
            if (ch in ASTERISK_CHARS && ch != '*') {
                sb.append('*')
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Split cleaned text into individual transaction rows.
     *
     * Uses Receipt No. pattern as row boundaries since Details can span multiple lines.
     *
     * PDFBox extracts tables as space-separated text. The M-PESA statement columns are:
     * Receipt No. | Completion Time | Details | Transaction Status | Paid In | Withdrawn | Balance
     *
     * After extracting the receipt+date prefix, the remaining text contains the Details
     * followed by "Completed" and then up to 3 decimal numbers (paid_in, withdrawn, balance).
     * The exact format varies by PDFBox version and PDF layout. We handle multiple formats:
     *
     * Format A (space-separated columns):
     *   "Customer Transfer to ... Completed                 8,000.00    123,456.78"
     *
     * Format B (concatenated with no spaces around Completed):
     *   "Customer Transfer to ... Completed8,000.00 123,456.78"
     *
     * Format C (dash before withdrawn amount — original assumption):
     *   "Customer Transfer to ... Completed-8,000.00 123,456.78"
     */
    private fun splitIntoRows(text: String): List<StatementRow> {
        val matches = ROW_BOUNDARY.findAll(text).toList()
        if (matches.isEmpty()) {
            Log.w(TAG, "No row boundaries found! First 300 chars of cleaned text: ${text.take(300)}")
            return emptyList()
        }

        Log.d(TAG, "Found ${matches.size} row boundary matches")

        val rows = mutableListOf<StatementRow>()

        for (i in matches.indices) {
            val matchStart = matches[i].range.first
            val matchEnd = matches[i].range.last + 1 // end of the receipt+date match
            val nextRowStart = if (i + 1 < matches.size) matches[i + 1].range.first else text.length

            val receiptNo = matches[i].groupValues[1]
            val dateTime = matches[i].groupValues[2]

            // Everything after the receipt+date match, up to the next row boundary
            val afterDate = text.substring(matchEnd, nextRowStart).trim()

            // Log first few rows for debugging
            if (i < 5) {
                Log.d(TAG, "Row $i ($receiptNo): afterDate='${afterDate.take(200)}'")
            }

            // Find "Completed" to split details from amounts
            val completedIdx = afterDate.indexOf("Completed", ignoreCase = true)
            if (completedIdx < 0) {
                Log.w(TAG, "Row $receiptNo: no 'Completed' found, skipping")
                continue
            }

            // Details PART 1 = everything before "Completed"
            val detailsBefore = afterDate.substring(0, completedIdx).trim()

            // After "Completed" — extract all decimal numbers (amounts)
            val afterCompleted = afterDate.substring(completedIdx + "Completed".length)

            // Find all amount matches to determine their positions
            val amountMatches = AMOUNT_NUMBER.findAll(afterCompleted).toList()
            val amounts = amountMatches.map { it.groupValues[1].replace(",", "").toDouble() }

            // Details PART 2 = any text AFTER the last amount number in afterCompleted.
            // PDFBox often places continuation details (recipient name, account number) on
            // lines below the "Completed -Amount Balance" line. These continuation lines
            // appear after the last numeric amount in the row text.
            val detailsAfter = if (amountMatches.isNotEmpty()) {
                val lastAmountEnd = amountMatches.last().range.last + 1
                afterCompleted.substring(lastAmountEnd).trim()
            } else {
                ""
            }

            // Combine both parts of the details, collapsing whitespace
            val combinedDetails = "$detailsBefore $detailsAfter".replace(Regex("""\s+"""), " ").trim()
            // Normalize non-standard asterisks to standard *
            val normalizedDetails = normalizeDetails(combinedDetails)

            // Interpret amounts based on count and context:
            // The statement columns after Status are: Paid In | Withdrawn | Balance
            // - 3 amounts: paid_in, withdrawn, balance
            // - 2 amounts: either (paid_in, balance) or (withdrawn, balance)
            //   → We determine which by checking the Details for income patterns
            // - 1 amount: just the balance (no paid_in or withdrawn visible)
            // - 0 amounts: skip
            var paidIn: Double? = null
            var withdrawn: Double? = null
            var balance: Double? = null

            val looksLikeIncome = INCOME_PATTERNS.any { it.containsMatchIn(normalizedDetails) }

            when (amounts.size) {
                0 -> {
                    Log.w(TAG, "Row $receiptNo: no amounts found after 'Completed'")
                    // Still add the row — it might be parseable with zero amounts
                }
                1 -> {
                    // Just balance
                    balance = amounts[0]
                }
                2 -> {
                    if (looksLikeIncome) {
                        // paid_in + balance
                        paidIn = amounts[0]
                        balance = amounts[1]
                    } else {
                        // withdrawn + balance
                        withdrawn = amounts[0]
                        balance = amounts[1]
                    }
                }
                3 -> {
                    paidIn = amounts[0]
                    withdrawn = amounts[1]
                    balance = amounts[2]
                }
                else -> {
                    // More than 3 amounts — take last 3
                    paidIn = amounts[amounts.size - 3]
                    withdrawn = amounts[amounts.size - 2]
                    balance = amounts[amounts.size - 1]
                }
            }

            if (i < 5) {
                Log.d(TAG, "Row $receiptNo: details='${normalizedDetails.take(100)}', " +
                        "withdrawn=$withdrawn, paidIn=$paidIn, balance=$balance")
            }

            rows.add(
                StatementRow(
                    receiptNo = receiptNo,
                    dateTime = dateTime,
                    details = normalizedDetails,
                    amountWithdrawn = withdrawn,
                    amountPaidIn = paidIn,
                    balance = balance
                )
            )
        }

        return rows
    }

    /**
     * Parse a single statement row into a PesaTrack expense.
     */
    private fun parseRow(row: StatementRow): ParsedStatementTransaction? {
        val details = row.details
        val amount = row.amountWithdrawn
        if (amount == null) {
            Log.d(TAG, "Row ${row.receiptNo}: skipping — no withdrawn amount (paidIn=${row.amountPaidIn})")
            return null
        }
        if (amount <= 0) return null

        val timestamp = parseTimestamp(row.dateTime)

        // ── Check charge patterns first ─────────────────────────────────
        if (SEND_CHARGE.containsMatchIn(details)) {
            return createCharge(row.receiptNo, amount, timestamp, "Transfer charge")
        }
        if (PAYBILL_CHARGE.containsMatchIn(details)) {
            return createCharge(row.receiptNo, amount, timestamp, "Pay Bill charge")
        }
        if (MERCHANT_CHARGE.containsMatchIn(details)) {
            return createCharge(row.receiptNo, amount, timestamp, "Merchant charge")
        }
        if (WITHDRAWAL_CHARGE.containsMatchIn(details)) {
            return createCharge(row.receiptNo, amount, timestamp, "Withdrawal charge")
        }

        // ── Send Money ──────────────────────────────────────────────────
        SEND_MONEY.find(details)?.let { match ->
            val phone = match.groupValues[1]
            val name = match.groupValues[2].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.SEND_MONEY,
                recipient = phone,
                recipientName = name,
                notes = null
            )
        }

        // ── Send to Small Business ──────────────────────────────────────
        SEND_SMALL_BUSINESS.find(details)?.let { match ->
            val phone = match.groupValues[1]
            val name = match.groupValues[2].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.SEND_MONEY,
                recipient = phone,
                recipientName = name,
                notes = "Small Business"
            )
        }

        // ── M-PESA Card (GlobalPay) — check before regular Pay Bill ─────
        MPESA_CARD.find(details)?.let { match ->
            val paybill = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val account = match.groupValues[3].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.MPESA_CARD,
                recipient = paybill,
                recipientName = name,
                notes = "Account: $account"
            )
        }

        // ── Pay Bill (with dash) ────────────────────────────────────────
        PAYBILL_WITH_DASH.find(details)?.let { match ->
            val paybill = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val account = match.groupValues[3].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.PAY_BILL,
                recipient = paybill,
                recipientName = name,
                notes = "Account: $account"
            )
        }

        // ── Pay Bill (no dash) ──────────────────────────────────────────
        PAYBILL_NO_DASH.find(details)?.let { match ->
            val paybill = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val account = match.groupValues[3].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.PAY_BILL,
                recipient = paybill,
                recipientName = name,
                notes = "Account: $account"
            )
        }

        // ── Merchant Payment (with dash) ────────────────────────────────
        MERCHANT_WITH_DASH.find(details)?.let { match ->
            val till = match.groupValues[1]
            val name = match.groupValues[2].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.BUY_GOODS,
                recipient = till,
                recipientName = name,
                notes = null
            )
        }

        // ── Merchant Payment (no dash) ──────────────────────────────────
        MERCHANT_NO_DASH.find(details)?.let { match ->
            val till = match.groupValues[1]
            val name = match.groupValues[2].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.BUY_GOODS,
                recipient = till,
                recipientName = name,
                notes = null
            )
        }

        // ── Agent Withdrawal ────────────────────────────────────────────
        AGENT_WITHDRAWAL.find(details)?.let { match ->
            val agentTill = match.groupValues[1]
            val agentName = match.groupValues[2].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.WITHDRAW,
                recipient = agentTill,
                recipientName = agentName,
                notes = null
            )
        }

        // ── Airtime Purchase (self) ─────────────────────────────────────
        if (AIRTIME_SELF.containsMatchIn(details)) {
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.AIRTIME,
                recipient = "Self",
                recipientName = "Airtime",
                notes = null
            )
        }

        // ── Customer Bundle Purchase ────────────────────────────────────
        BUNDLE_PURCHASE.find(details)?.let { match ->
            val serviceId = match.groupValues[1]
            val serviceName = match.groupValues[2].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.AIRTIME,
                recipient = serviceId,
                recipientName = "Data Bundle - $serviceName".trim(),
                notes = null
            )
        }

        // ── Recharge for Customer ───────────────────────────────────────
        RECHARGE.find(details)?.let { match ->
            val serviceId = match.groupValues[1]
            val serviceName = match.groupValues[2].trim()
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.PAY_BILL,
                recipient = serviceId,
                recipientName = "Recharge - $serviceName".trim(),
                notes = null
            )
        }

        // ── M-Shwari Deposit (savings outflow) ─────────────────────────
        if (MSHWARI_DEPOSIT.containsMatchIn(details)) {
            return createExpense(
                receiptNo = row.receiptNo,
                amount = amount,
                timestamp = timestamp,
                paymentType = PaymentType.PAY_BILL,
                recipient = "M-Shwari",
                recipientName = "M-Shwari Deposit",
                notes = "Savings"
            )
        }

        // ── Unrecognized ────────────────────────────────────────────────
        // Log details with hex codes for non-ASCII characters to diagnose pattern mismatches
        val hexDump = details.take(100).map { ch ->
            if (ch.code > 127) "\\u${ch.code.toString(16).padStart(4, '0')}" else ch.toString()
        }.joinToString("")
        Log.w(TAG, "Unrecognized transaction: receipt=${row.receiptNo} withdrawn=$amount details='${details.take(120)}'")
        Log.w(TAG, "  hex-details='$hexDump'")
        return null
    }

    private fun createExpense(
        receiptNo: String,
        amount: Double,
        timestamp: Long,
        paymentType: PaymentType,
        recipient: String,
        recipientName: String?,
        notes: String?
    ): ParsedStatementTransaction {
        return ParsedStatementTransaction(
            expense = Expense(
                transactionId = receiptNo,
                amount = amount,
                recipient = recipient,
                recipientName = recipientName,
                paymentType = paymentType,
                source = ExpenseSource.MPESA_STATEMENT,
                notes = notes,
                timestamp = timestamp
            ),
            isCharge = false
        )
    }

    private fun createCharge(
        receiptNo: String,
        amount: Double,
        timestamp: Long,
        description: String
    ): ParsedStatementTransaction {
        return ParsedStatementTransaction(
            expense = Expense(
                transactionId = "${receiptNo}_charge",
                amount = amount,
                recipient = "M-PESA",
                recipientName = description,
                categoryId = SmsParser.MPESA_TRANSACTION_COST_CATEGORY_ID,
                paymentType = PaymentType.TRANSACTION_COST,
                source = ExpenseSource.MPESA_STATEMENT,
                notes = description,
                timestamp = timestamp,
                isCategorized = true
            ),
            isCharge = true
        )
    }

    private fun parseTimestamp(dateTimeStr: String): Long {
        return try {
            STATEMENT_DATE_FORMAT.parse(dateTimeStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse date: $dateTimeStr", e)
            System.currentTimeMillis()
        }
    }
}
