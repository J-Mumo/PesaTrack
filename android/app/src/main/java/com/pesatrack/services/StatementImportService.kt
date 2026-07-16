package com.pesatrack.services

import android.util.Log
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.IncomeRepository
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.utils.parsers.MpesaStatementParser
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that orchestrates the M-PESA PDF statement import process.
 *
 * Flow:
 * 1. Extract text from password-protected PDF via [MpesaStatementParser]
 * 2. Parse all transactions from the extracted text
 * 3. Deduplicate against existing DB records (by transactionId/receiptNo)
 * 4. Auto-categorize using recipient mappings and [CategorizationService]
 * 5. Save to database
 * 6. Report results
 *
 * @see MpesaStatementParser
 */
@Singleton
class StatementImportService @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val recipientMappingRepository: RecipientMappingRepository,
    private val categorizationService: CategorizationService
) {
    companion object {
        private const val TAG = "StatementImportService"
    }

    /**
     * Result of a statement import operation.
     */
    data class StatementImportResult(
        /** Total transaction rows found in the statement */
        val totalRows: Int = 0,
        /** Expenses successfully imported */
        val imported: Int = 0,
        /** Expenses auto-categorized via recipient mapping or rules engine */
        val autoCategorized: Int = 0,
        /** Transaction charges imported (auto-categorized under 606) */
        val chargesImported: Int = 0,
        /** Rows skipped because they are income (received money, salary, etc.) */
        val skippedIncome: Int = 0,
        /** New income transactions imported from the statement (Phase 2) */
        val incomeImported: Int = 0,
        /** Income rows skipped because transactionId already exists in DB (Phase 2) */
        val incomeDuplicates: Int = 0,
        /** Rows skipped because they are reversals */
        val skippedReversal: Int = 0,
        /** Rows skipped because transactionId already exists in DB */
        val skippedDuplicate: Int = 0,
        /** Rows that could not be parsed (unrecognized transaction type) */
        val unparseable: Int = 0,
        /** Statement period (from header) */
        val statementPeriod: String? = null,
        /** Customer name (from header) */
        val customerName: String? = null,
        /** Any error message */
        val error: String? = null
    )

    /**
     * Import a single M-PESA statement PDF.
     *
     * @param inputStream The PDF file input stream
     * @param password The PDF password (sent by Safaricom via SMS). Null to try without password.
     * @param onProgress Callback for progress updates (current, total, phase)
     * @return StatementImportResult with import statistics
     */
    suspend fun importStatement(
        inputStream: InputStream,
        password: String?,
        onProgress: ((current: Int, total: Int, phase: String) -> Unit)? = null
    ): StatementImportResult {
        // Phase 1: Extract text from PDF
        onProgress?.invoke(0, 0, "Opening PDF...")

        val text = MpesaStatementParser.extractTextFromPdf(inputStream, password)
        if (text == null) {
            return StatementImportResult(
                error = if (password != null) {
                    "Could not open PDF. Check that the password is correct (sent by Safaricom via SMS)."
                } else {
                    "Could not open PDF. The file may be password-protected."
                }
            )
        }

        // Phase 2: Parse transactions
        onProgress?.invoke(0, 0, "Parsing transactions...")

        val parseResult = MpesaStatementParser.parseStatementText(text)
        val transactions = parseResult.transactions

        if (transactions.isEmpty()) {
            return StatementImportResult(
                totalRows = parseResult.totalRowsParsed,
                skippedIncome = parseResult.rowsSkippedIncome,
                skippedReversal = parseResult.rowsSkippedReversal,
                unparseable = parseResult.rowsUnparseable,
                statementPeriod = parseResult.header.statementPeriod,
                customerName = parseResult.header.customerName,
                error = "No expense transactions found in the statement."
            )
        }

        // Phase 3: Deduplicate + auto-categorize + save
        onProgress?.invoke(0, transactions.size, "Importing expenses...")

        var imported = 0
        var autoCategorized = 0
        var chargesImported = 0
        var skippedDuplicate = 0

        // Track receipt numbers whose parent transaction was skipped (duplicate or already from SMS)
        // so we can also skip their associated charges
        val skippedReceiptNos = mutableSetOf<String>()

        for ((index, parsed) in transactions.withIndex()) {
            onProgress?.invoke(index + 1, transactions.size, "Importing expenses...")

            val expense = parsed.expense

            // For charges: check if parent transaction was skipped as duplicate
            // Charges have transactionId like "UDK041CZLM_charge" — parent is "UDK041CZLM"
            if (parsed.isCharge && expense.transactionId != null) {
                val parentReceiptNo = expense.transactionId.removeSuffix("_charge")
                if (skippedReceiptNos.contains(parentReceiptNo)) {
                    skippedDuplicate++
                    continue
                }
                // Also check if the parent exists in DB (was imported from SMS previously)
                if (expenseRepository.transactionExists(parentReceiptNo)) {
                    skippedDuplicate++
                    skippedReceiptNos.add(parentReceiptNo) // cache for future lookups
                    continue
                }
            }

            // Check if already exists (by transactionId)
            if (expense.transactionId != null) {
                val exists = expenseRepository.transactionExists(expense.transactionId)
                if (exists) {
                    skippedDuplicate++
                    // Track the receipt number so associated charges are also skipped
                    val baseReceiptNo = expense.transactionId.removeSuffix("_charge")
                    skippedReceiptNos.add(baseReceiptNo)
                    Log.d(TAG, "Skipping duplicate: ${expense.transactionId} (${expense.recipientName ?: expense.recipient})")
                    continue
                }
            }

            // Auto-categorize (unless it's already a charge — those are pre-categorized)
            val categorizedExpense = if (parsed.isCharge) {
                chargesImported++
                expense
            } else {
                val categoryResult = autoCategorize(expense)
                if (categoryResult.categoryId != null) {
                    autoCategorized++
                }
                categoryResult
            }

            // Save to database
            try {
                expenseRepository.saveExpense(categorizedExpense)
                imported++
                Log.d(TAG, "Imported: ${expense.transactionId} | ${expense.amount} | ${expense.recipientName ?: expense.recipient} | charge=${parsed.isCharge}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save expense ${expense.transactionId}: ${e.message}", e)
            }
        }

        Log.i(TAG, "Statement import complete: imported=$imported, autoCat=$autoCategorized, " +
                "charges=$chargesImported, duplicates=$skippedDuplicate")

        // Phase 4: Import income transactions
        var incomeImported = 0
        var incomeDuplicates = 0
        for (income in parseResult.incomeTransactions) {
            try {
                val rowId = incomeRepository.insertIfNew(income.copy(rawSms = "PDF statement row"))
                if (rowId != null) {
                    incomeImported++
                    Log.d(TAG, "Imported income: ${income.transactionId} | KES ${income.amount} | source=${income.source.name}")
                } else {
                    incomeDuplicates++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save income ${income.transactionId}: ${e.message}", e)
            }
        }
        if (parseResult.incomeTransactions.isNotEmpty()) {
            Log.i(TAG, "Income import: imported=$incomeImported, duplicates=$incomeDuplicates")
        }

        return StatementImportResult(
            totalRows = parseResult.totalRowsParsed,
            imported = imported,
            autoCategorized = autoCategorized,
            chargesImported = chargesImported,
            skippedIncome = parseResult.rowsSkippedIncome,
            incomeImported = incomeImported,
            incomeDuplicates = incomeDuplicates,
            skippedReversal = parseResult.rowsSkippedReversal,
            skippedDuplicate = skippedDuplicate,
            unparseable = parseResult.rowsUnparseable,
            statementPeriod = parseResult.header.statementPeriod,
            customerName = parseResult.header.customerName
        )
    }

    /**
     * Auto-categorize an expense using:
     * 1. Recipient mapping (learned from previous categorizations)
     * 2. CategorizationService (user rules + built-in keyword rules engine)
     */
    private suspend fun autoCategorize(expense: Expense): Expense {
        val recipientKey = expense.recipientName?.trim()?.lowercase()
            ?: expense.recipient.trim().lowercase()

        // 1. Try recipient mapping first
        //    Paybill payments use a composite (paybill, account) key so aggregator
        //    paybills (e.g. NCBA Loop 247247) don't misfire across unrelated merchants.
        if (expense.paymentType == PaymentType.PAY_BILL) {
            val paybillCategory = recipientMappingRepository.getCategoryForPaybill(
                paybillName = expense.recipientName,
                account = expense.recipient
            )
            if (paybillCategory != null) {
                return expense.copy(
                    categoryId = paybillCategory,
                    isCategorized = true
                )
            }
            // Skip name/account-only fallbacks for paybills — see comment above.
        } else {
            val mappedCategory = recipientMappingRepository.getCategoryForRecipient(recipientKey)
            if (mappedCategory != null) {
                return expense.copy(
                    categoryId = mappedCategory,
                    isCategorized = true
                )
            }

            // Also try the raw recipient (phone/till/paybill number)
            val recipientNumericKey = expense.recipient.trim().lowercase()
            if (recipientNumericKey != recipientKey) {
                val numericMapped = recipientMappingRepository.getCategoryForRecipient(recipientNumericKey)
                if (numericMapped != null) {
                    return expense.copy(
                        categoryId = numericMapped,
                        isCategorized = true
                    )
                }
            }
        }

        // 2. Try rules engine (user rules + keyword-based)
        val displayName = expense.recipientName ?: expense.recipient
        val recipientInfo = RecipientInfo(
            recipientKey = recipientKey,
            displayName = displayName,
            paymentType = expense.paymentType.name,
            totalAmount = expense.amount,
            transactionCount = 1
        )
        val result = categorizationService.suggestCategories(listOf(recipientInfo))
        val suggestion = result.suggestions[recipientKey]
        if (suggestion != null) {
            return expense.copy(
                categoryId = suggestion.categoryId,
                isCategorized = true
            )
        }

        // 3. Special case: M-Shwari → Investment & Savings > Savings (1801)
        if (expense.recipientName?.contains("M-Shwari", ignoreCase = true) == true) {
            return expense.copy(
                categoryId = 1801L, // Savings
                isCategorized = true
            )
        }

        // No match — leave uncategorized
        return expense
    }
}
