package com.pesatrack.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.IncomeRepository
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.IncomeTransaction
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.utils.SmsParser
import com.pesatrack.utils.parsers.ParsedSms
import com.pesatrack.utils.parsers.SmsParserRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for importing historical SMS from the device inbox.
 *
 * Supports multi-source import:
 * - M-PESA SMS (always imported)
 * - Bank SMS (NCBA, etc.) imported only if enabled in AppPreferences
 *
 * Reads SMS via ContentResolver, parses using [SmsParserRegistry],
 * deduplicates against existing DB records, and applies auto-categorization rules.
 *
 * Usage flow:
 * 1. User selects date range on ImportScreen
 * 2. ImportViewModel calls importHistoricalSms()
 * 3. Progress reported via callback
 * 4. Result shows how many were imported vs auto-categorized
 */
@Singleton
class SmsImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val recipientMappingRepository: RecipientMappingRepository,
    private val appPreferences: AppPreferences
) {

    companion object {
        private const val TAG = "SmsImportService"

        /** SMS content provider URI for inbox */
        private val SMS_INBOX_URI: Uri = Uri.parse("content://sms/inbox")

        /** M-PESA sender address (always included) */
        private const val MPESA_SENDER = "MPESA"

        /** Batch size for database inserts */
        private const val BATCH_SIZE = 50
    }

    /**
     * Result of a historical SMS import operation
     */
    data class ImportResult(
        /** Total SMS found in inbox across all sources */
        val totalSmsFound: Int = 0,
        /** SMS that were successfully parsed as expense transactions */
        val totalParsed: Int = 0,
        /** Expenses already in DB (skipped duplicates) */
        val duplicatesSkipped: Int = 0,
        /** New expenses inserted into DB */
        val newExpensesImported: Int = 0,
        /** Expenses auto-categorized by deterministic rules */
        val autoCategorizedByRules: Int = 0,
        /** Expenses auto-categorized by recipient mapping */
        val autoCategorizedByMapping: Int = 0,
        /** Expenses that need manual categorization */
        val needsManualCategorization: Int = 0,
        /** Transaction costs saved as separate expenses */
        val transactionCostsSaved: Int = 0,
        /** New income transactions imported (Phase 2) */
        val newIncomesImported: Int = 0,
        /** Income SMS already in DB (skipped duplicates, Phase 2) */
        val incomeDuplicatesSkipped: Int = 0,
        /** Number of sources that were imported from */
        val sourcesImported: Int = 0,
        /** Errors encountered during import */
        val errors: Int = 0
    )

    /**
     * Import historical SMS from the device inbox (all enabled sources).
     *
     * @param fromTimestamp Start of date range (null = all history)
     * @param toTimestamp End of date range (null = now)
     * @param onProgress Callback for progress updates (current, total)
     * @return ImportResult with statistics
     */
    suspend fun importHistoricalSms(
        fromTimestamp: Long? = null,
        toTimestamp: Long? = null,
        onProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> }
    ): ImportResult {
        Log.d(TAG, "Starting historical SMS import (from=${fromTimestamp}, to=${toTimestamp})")

        // Build list of sender IDs to query
        val senderIds = getActiveSenderIds()
        Log.d(TAG, "Active sender IDs: $senderIds")

        // 1. Read SMS from inbox for all active senders
        val smsList = readSmsFromInbox(senderIds, fromTimestamp, toTimestamp)
        Log.d(TAG, "Found ${smsList.size} SMS in inbox from ${senderIds.size} sources")

        if (smsList.isEmpty()) {
            return ImportResult(totalSmsFound = 0, sourcesImported = senderIds.size)
        }

        // 2. Load confident mappings for auto-categorization (≥80% confidence only)
        val confidentMappings = recipientMappingRepository.getConfidentMappingsAsMap()
        Log.d(TAG, "Loaded ${confidentMappings.size} confident recipient mappings")

        // 3. Parse and process SMS
        var totalParsed = 0
        var duplicatesSkipped = 0
        var newExpensesImported = 0
        var autoCategorizedByRules = 0
        var autoCategorizedByMapping = 0
        var needsManualCategorization = 0
        var transactionCostsSaved = 0
        var newIncomesImported = 0
        var incomeDuplicatesSkipped = 0
        var errors = 0

        val expenseBatch = mutableListOf<Expense>()

        for ((index, sms) in smsList.withIndex()) {
            try {
                // Report progress
                onProgress(index + 1, smsList.size)

                // Parse SMS using the registry (dispatches to correct parser by sender)
                // Pass sms.date so parsers use the actual SMS received date as timestamp
                val parsed = SmsParserRegistry.parseSms(sms.sender, sms.body, sms.date)
                when (parsed) {
                    ParsedSms.NotARelevantMessage -> continue
                    is ParsedSms.IncomeResult -> {
                        val rowId = incomeRepository.insertIfNew(parsed.income.copy(rawSms = sms.body))
                        if (rowId != null) newIncomesImported++ else incomeDuplicatesSkipped++
                        totalParsed++
                        continue
                    }
                    is ParsedSms.ExpenseResult -> Unit
                }
                totalParsed++

                // Create main expense with rawSms
                var mainExpense = parsed.expense.copy(rawSms = sms.body)

                // Check duplicate
                val txId = mainExpense.transactionId
                if (txId != null && expenseRepository.transactionExists(txId)) {
                    duplicatesSkipped++
                    continue
                }

                // Apply auto-categorization (only high-confidence mappings)
                mainExpense = applyCategorization(
                    mainExpense,
                    confidentMappings
                )

                // Track categorization stats
                when {
                    mainExpense.isCategorized && isRuleBasedCategory(mainExpense) -> {
                        autoCategorizedByRules++
                    }
                    mainExpense.isCategorized -> {
                        autoCategorizedByMapping++
                    }
                    else -> {
                        needsManualCategorization++
                    }
                }

                expenseBatch.add(mainExpense)

                // Handle transaction cost
                val costExpense = parsed.transactionCost?.copy(rawSms = sms.body)
                if (costExpense != null) {
                    expenseBatch.add(costExpense)
                    transactionCostsSaved++
                }

                // Flush batch when full
                if (expenseBatch.size >= BATCH_SIZE) {
                    val inserted = flushBatch(expenseBatch)
                    newExpensesImported += inserted
                    expenseBatch.clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS at index $index: ${e.message}", e)
                errors++
            }
        }

        // Flush remaining
        if (expenseBatch.isNotEmpty()) {
            val inserted = flushBatch(expenseBatch)
            newExpensesImported += inserted
            expenseBatch.clear()
        }

        val result = ImportResult(
            totalSmsFound = smsList.size,
            totalParsed = totalParsed,
            duplicatesSkipped = duplicatesSkipped,
            newExpensesImported = newExpensesImported,
            autoCategorizedByRules = autoCategorizedByRules,
            autoCategorizedByMapping = autoCategorizedByMapping,
            needsManualCategorization = needsManualCategorization,
            transactionCostsSaved = transactionCostsSaved,
            newIncomesImported = newIncomesImported,
            incomeDuplicatesSkipped = incomeDuplicatesSkipped,
            sourcesImported = senderIds.size,
            errors = errors
        )

        // Track import milestone and counter (fire-and-forget)
        if (newExpensesImported > 0) {
            appPreferences.recordFirstImportCompleted()
            appPreferences.incrementImportsCount()
            // Distinct from live-SMS parses (KEY_COUNT_SMS_PARSED, incremented by
            // SmsReceiver only). Feedback triage needs to know how many SMS came
            // from historical inbox pulls vs live receives.
            appPreferences.incrementSmsImportedCount(newExpensesImported + newIncomesImported)
        }

        Log.d(TAG, "Import complete: $result")
        return result
    }

    /**
     * Get the list of active sender IDs to import from.
     *
     * M-PESA is always included. Bank senders are included only
     * if bank tracking is enabled AND the specific bank is toggled on.
     */
    private suspend fun getActiveSenderIds(): List<String> {
        val senders = mutableListOf(MPESA_SENDER)

        // Add enabled bank sender IDs
        val enabledBanks = appPreferences.getEnabledBanksSnapshot()
        if (enabledBanks.isNotEmpty()) {
            val bankSenderIds = SmsParserRegistry.getEnabledSenderIds(enabledBanks)
            senders.addAll(bankSenderIds)
        }

        return senders.distinct()
    }

    /**
     * Read SMS from the device inbox for multiple senders via ContentResolver.
     *
     * @param senderIds List of sender addresses to query (e.g., "MPESA", "NCBA_BANK")
     * @param fromTimestamp Optional start date filter
     * @param toTimestamp Optional end date filter
     * @return List of SMS messages with body, date, and sender
     */
    private fun readSmsFromInbox(
        senderIds: List<String>,
        fromTimestamp: Long?,
        toTimestamp: Long?
    ): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()

        for (senderId in senderIds) {
            val senderMessages = readSmsForSender(senderId, fromTimestamp, toTimestamp)
            messages.addAll(senderMessages)
        }

        // Sort by date (oldest first for chronological import)
        messages.sortBy { it.date }

        Log.d(TAG, "Read ${messages.size} SMS from ${senderIds.size} senders: " +
                senderIds.joinToString(", ") { "$it(${messages.count { msg -> msg.sender == it }})" })

        return messages
    }

    /**
     * Read SMS from a single sender.
     */
    private fun readSmsForSender(
        senderId: String,
        fromTimestamp: Long?,
        toTimestamp: Long?
    ): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()

        // Build selection query
        val selectionParts = mutableListOf("address = ?")
        val selectionArgs = mutableListOf(senderId)

        if (fromTimestamp != null) {
            selectionParts.add("date >= ?")
            selectionArgs.add(fromTimestamp.toString())
        }
        if (toTimestamp != null) {
            selectionParts.add("date <= ?")
            selectionArgs.add(toTimestamp.toString())
        }

        val selection = selectionParts.joinToString(" AND ")

        try {
            context.contentResolver.query(
                SMS_INBOX_URI,
                arrayOf("body", "date", "address"),
                selection,
                selectionArgs.toTypedArray(),
                "date ASC" // oldest first for chronological import
            )?.use { cursor ->
                val bodyIndex = cursor.getColumnIndexOrThrow("body")
                val dateIndex = cursor.getColumnIndexOrThrow("date")
                val addressIndex = cursor.getColumnIndexOrThrow("address")

                while (cursor.moveToNext()) {
                    val body = cursor.getString(bodyIndex) ?: continue
                    val date = cursor.getLong(dateIndex)
                    val address = cursor.getString(addressIndex) ?: senderId

                    // Check if any parser can handle this SMS
                    if (SmsParserRegistry.canHandleAny(address, body)) {
                        messages.add(SmsMessage(body = body, date = date, sender = address))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SMS read permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS inbox for sender $senderId", e)
        }

        return messages
    }

    /**
     * Apply categorization rules to an expense:
     * 1. Deterministic rules (Airtime → category 202)
     * 2. Recipient mapping (only if ≥80% confidence)
     */
    private suspend fun applyCategorization(
        expense: Expense,
        confidentMappings: Map<String, Long>
    ): Expense {
        // Already categorized (e.g., transaction costs)
        if (expense.isCategorized) return expense

        // 1. Deterministic rules
        val ruleCategory = getDeterministicCategory(expense)
        if (ruleCategory != null) {
            return expense.copy(
                categoryId = ruleCategory,
                isCategorized = true
            )
        }

        // 2. Confident recipient mapping lookup (≥80% confidence only)
        //    Paybill payments look up a composite (paybill, account) key so aggregator
        //    paybills (e.g. NCBA Loop 247247) don't misfire across unrelated merchants.
        if (expense.paymentType == PaymentType.PAY_BILL) {
            val composite = RecipientMappingRepository.composePaybillKey(
                expense.recipientName, expense.recipient
            )
            val paybillCategory = composite?.let { confidentMappings[it] }
            if (paybillCategory != null) {
                return expense.copy(
                    categoryId = paybillCategory,
                    isCategorized = true
                )
            }
            // Fall through — no other lookup for paybills.
            return expense
        }

        val recipientKey = RecipientMappingRepository.normalizeRecipientKey(
            expense.recipientName ?: expense.recipient
        )
        val mappedCategory = confidentMappings[recipientKey]
        if (mappedCategory != null) {
            return expense.copy(
                categoryId = mappedCategory,
                isCategorized = true
            )
        }

        // Also try just the recipient (phone number / till)
        if (expense.recipientName != null) {
            val altKey = RecipientMappingRepository.normalizeRecipientKey(expense.recipient)
            val altCategory = confidentMappings[altKey]
            if (altCategory != null) {
                return expense.copy(
                    categoryId = altCategory,
                    isCategorized = true
                )
            }
        }

        // No categorization found — will appear in batch categorize screen
        return expense
    }

    /**
     * Get category from deterministic rules (always the same, no user input needed).
     *
     * Returns category ID or null if no rule applies.
     */
    private fun getDeterministicCategory(expense: Expense): Long? {
        return when (expense.paymentType) {
            // Airtime is always Airtime (category 202)
            PaymentType.AIRTIME -> 202L
            // Transaction costs are handled by SmsParser already, but just in case
            PaymentType.TRANSACTION_COST -> SmsParser.MPESA_TRANSACTION_COST_CATEGORY_ID
            // Other types need user categorization
            else -> null
        }
    }

    /**
     * Check if the expense was categorized by a deterministic rule
     * (vs by recipient mapping)
     */
    private fun isRuleBasedCategory(expense: Expense): Boolean {
        return getDeterministicCategory(expense) != null
    }

    /**
     * Flush a batch of expenses to the database.
     * Returns count of successfully inserted expenses.
     */
    private suspend fun flushBatch(expenses: List<Expense>): Int {
        return try {
            val results = expenseRepository.saveExpenses(expenses)
            // Count non-negative results (successful inserts)
            results.count { it > 0 }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing batch of ${expenses.size} expenses", e)
            0
        }
    }

    /**
     * Internal SMS message representation
     */
    private data class SmsMessage(
        val body: String,
        val date: Long,
        val sender: String = MPESA_SENDER
    )
}
