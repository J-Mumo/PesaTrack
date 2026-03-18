package com.pesatrack.services

import android.util.Log
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.utils.excel.ExcelCategoryMapper
import com.pesatrack.utils.excel.ExcelParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that orchestrates Excel expense import.
 *
 * Flow:
 * 1. Parse Excel files via [ExcelParser]
 * 2. Map category labels via [ExcelCategoryMapper]
 * 3. Determine SMS-covered date range from DB
 * 4. For each Excel row within range:
 *    a. Try to match an uncategorized SMS expense (amount ± 1 KES, date ± 1 day)
 *    b. If matched → apply category + save recipient→category mapping
 *    c. If no match and no existing expense at that amount+date → import as standalone
 * 5. Report results
 */
@Singleton
class ExcelImportService @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val recipientMappingRepository: RecipientMappingRepository
) {
    companion object {
        private const val TAG = "ExcelImportService"

        /** Amount tolerance for matching (KES) */
        private const val AMOUNT_TOLERANCE = 1.0

        /** Date tolerance: ±1 day in milliseconds */
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Result of an Excel import operation.
     */
    data class ExcelImportResult(
        /** Total rows parsed from all Excel files */
        val totalExcelRows: Int = 0,
        /** Excel rows matched to uncategorized SMS expenses (category applied) */
        val rowsMatchedToSms: Int = 0,
        /** Excel rows imported as new standalone expenses */
        val rowsImportedAsStandalone: Int = 0,
        /** Excel rows skipped because date is outside SMS coverage */
        val rowsSkippedOutOfRange: Int = 0,
        /** Excel rows skipped because a matching expense already exists (categorized) */
        val rowsSkippedAlreadyExists: Int = 0,
        /** Excel rows with unknown category labels (imported uncategorized) */
        val rowsWithUnknownCategory: Int = 0,
        /** Recipient→category mappings learned from matches */
        val recipientMappingsLearned: Int = 0,
        /** Parse errors across all files */
        val parseErrors: Int = 0,
        /** Number of files processed */
        val filesProcessed: Int = 0,
        /** Total sheets processed */
        val sheetsProcessed: Int = 0
    )

    /**
     * Represents a single Excel file to import.
     */
    data class ExcelFileInput(
        val inputStream: InputStream,
        val fileName: String
    )

    /**
     * Import one or more Excel files.
     *
     * @param files List of Excel file inputs (supports multi-file)
     * @param onProgress Callback: (currentRow, totalRows, phase)
     * @return ExcelImportResult with statistics
     */
    suspend fun importExcelFiles(
        files: List<ExcelFileInput>,
        onProgress: suspend (current: Int, total: Int, phase: String) -> Unit = { _, _, _ -> }
    ): ExcelImportResult {
        Log.d(TAG, "Starting Excel import of ${files.size} file(s)")

        // Phase 1: Parse all files
        onProgress(0, 0, "Parsing Excel files...")
        val allRows = mutableListOf<ExcelParser.ExcelExpenseRow>()
        var totalSheetsProcessed = 0
        var totalParseErrors = 0

        for (file in files) {
            Log.d(TAG, "Parsing file: ${file.fileName}")
            val result = ExcelParser.parse(file.inputStream, file.fileName)
            allRows.addAll(result.rows)
            totalSheetsProcessed += result.sheetsProcessed
            totalParseErrors += result.parseErrors
            Log.d(TAG, "Parsed ${result.rows.size} rows from ${file.fileName}")
        }

        if (allRows.isEmpty()) {
            Log.w(TAG, "No valid rows found in Excel files")
            return ExcelImportResult(
                parseErrors = totalParseErrors,
                filesProcessed = files.size,
                sheetsProcessed = totalSheetsProcessed
            )
        }

        Log.d(TAG, "Total parsed rows: ${allRows.size}")

        // Phase 2: Get SMS date coverage
        onProgress(0, allRows.size, "Checking SMS date range...")
        val dateRange = expenseRepository.getSmsCoveredDateRange()
        val minSmsDate = dateRange?.minTimestamp
        val maxSmsDate = dateRange?.maxTimestamp

        if (minSmsDate == null || maxSmsDate == null) {
            Log.w(TAG, "No SMS expenses found — importing all Excel rows as standalone")
        } else {
            val minDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(minSmsDate))
            val maxDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(maxSmsDate))
            Log.d(TAG, "SMS date range: $minDateStr to $maxDateStr")
        }

        // Phase 3: Process each row
        var rowsMatchedToSms = 0
        var rowsImportedAsStandalone = 0
        var rowsSkippedOutOfRange = 0
        var rowsSkippedAlreadyExists = 0
        var rowsWithUnknownCategory = 0
        var recipientMappingsLearned = 0

        // Track already-matched expense IDs to avoid double-matching
        val matchedExpenseIds = mutableSetOf<Long>()

        for ((index, excelRow) in allRows.withIndex()) {
            if (index % 50 == 0) {
                onProgress(index, allRows.size, "Matching expenses...")
            }

            // Map Excel category label to PesaTrack category ID
            val categoryId = ExcelCategoryMapper.getCategoryId(excelRow.categoryLabel)
            if (categoryId == null) {
                rowsWithUnknownCategory++
            }

            // Check if within SMS date range
            val withinSmsRange = if (minSmsDate != null && maxSmsDate != null) {
                excelRow.date in minSmsDate..maxSmsDate
            } else {
                false // No SMS data — can't determine range
            }

            if (!withinSmsRange && minSmsDate != null) {
                rowsSkippedOutOfRange++
                continue
            }

            // Calculate date window: ±1 day from Excel date
            val dayStart = excelRow.date - DAY_MS
            val dayEnd = excelRow.date + DAY_MS

            // Try to find a matching uncategorized SMS expense
            val matchedExpense = expenseRepository.findMatchByAmountAndDate(
                amount = excelRow.amount,
                tolerance = AMOUNT_TOLERANCE,
                dayStartMs = dayStart,
                dayEndMs = dayEnd
            )

            if (matchedExpense != null && matchedExpense.id !in matchedExpenseIds) {
                // Match found — apply category
                if (categoryId != null) {
                    expenseRepository.updateCategory(matchedExpense.id, categoryId)

                    // Save recipient→category mapping for future auto-categorization
                    val recipientKey = RecipientMappingRepository.normalizeRecipientKey(
                        matchedExpense.recipientName ?: matchedExpense.recipient
                    )
                    if (recipientKey.isNotBlank()) {
                        try {
                            recipientMappingRepository.saveMapping(
                                recipientKey = recipientKey,
                                categoryId = categoryId,
                                displayName = matchedExpense.recipientName
                                    ?: matchedExpense.recipient
                            )
                            recipientMappingsLearned++
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to save mapping for $recipientKey: ${e.message}")
                        }
                    }
                }

                matchedExpenseIds.add(matchedExpense.id)
                rowsMatchedToSms++
            } else {
                // No match — check if expense already exists at this amount+date
                val alreadyExists = expenseRepository.expenseExistsAtAmountAndDate(
                    amount = excelRow.amount,
                    tolerance = AMOUNT_TOLERANCE,
                    dayStartMs = dayStart,
                    dayEndMs = dayEnd
                )

                if (alreadyExists) {
                    rowsSkippedAlreadyExists++
                } else if (withinSmsRange || minSmsDate == null) {
                    // Import as standalone expense
                    val syntheticTxId = buildSyntheticTransactionId(excelRow)

                    // Check if this synthetic ID already exists (re-import safety)
                    if (!expenseRepository.transactionExists(syntheticTxId)) {
                        val expense = Expense(
                            transactionId = syntheticTxId,
                            amount = excelRow.amount,
                            recipient = excelRow.categoryLabel.trim(),
                            recipientName = excelRow.categoryLabel.trim(),
                            categoryId = categoryId,
                            paymentType = PaymentType.SEND_MONEY,
                            source = ExpenseSource.EXCEL_IMPORT,
                            notes = "Imported from Excel: ${excelRow.fileName} / ${excelRow.sheetName}",
                            timestamp = excelRow.date,
                            isCategorized = categoryId != null
                        )

                        try {
                            expenseRepository.saveExpense(expense)
                            rowsImportedAsStandalone++
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to import standalone expense: ${e.message}")
                            totalParseErrors++
                        }
                    } else {
                        rowsSkippedAlreadyExists++
                    }
                }
            }
        }

        onProgress(allRows.size, allRows.size, "Complete")

        val result = ExcelImportResult(
            totalExcelRows = allRows.size,
            rowsMatchedToSms = rowsMatchedToSms,
            rowsImportedAsStandalone = rowsImportedAsStandalone,
            rowsSkippedOutOfRange = rowsSkippedOutOfRange,
            rowsSkippedAlreadyExists = rowsSkippedAlreadyExists,
            rowsWithUnknownCategory = rowsWithUnknownCategory,
            recipientMappingsLearned = recipientMappingsLearned,
            parseErrors = totalParseErrors,
            filesProcessed = files.size,
            sheetsProcessed = totalSheetsProcessed
        )

        Log.d(TAG, "Excel import complete: $result")
        return result
    }

    /**
     * Build a synthetic transaction ID for standalone Excel imports.
     * Format: EXCEL_{sanitizedFileName}_{date}_{label}_{amount}
     * This ensures re-imports don't create duplicates.
     */
    private fun buildSyntheticTransactionId(row: ExcelParser.ExcelExpenseRow): String {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(row.date))
        val sanitizedFile = row.fileName
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .take(20) // Limit filename portion
        val sanitizedLabel = row.categoryLabel.trim()
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .take(20)
        val amountStr = String.format(Locale.US, "%.2f", row.amount).replace(".", "")
        return "EXCEL_${sanitizedFile}_${dateStr}_${sanitizedLabel}_$amountStr"
    }
}
