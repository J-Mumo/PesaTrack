package com.pesatrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.IncomeRepository
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.domain.models.IncomeTransaction
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.services.telemetry.TelemetryClient
import com.pesatrack.services.telemetry.TelemetryEvents
import com.pesatrack.utils.SmsParser
import com.pesatrack.utils.parsers.ParsedSms
import com.pesatrack.utils.parsers.NcbaPairedSmsResolver
import com.pesatrack.utils.parsers.SmsParserRegistry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver for incoming SMS messages.
 *
 * Listens for M-PESA and bank confirmation SMS and automatically
 * parses them into expense records. Also extracts transaction
 * costs and saves them as separate auto-categorized expenses.
 *
 * Multi-source support:
 * - M-PESA SMS are always processed
 * - Bank SMS (NCBA, etc.) are processed only if enabled in AppPreferences
 *
 * Enhanced with recipient-based auto-categorization:
 * if the recipient has been categorized before, the new expense
 * is automatically assigned the same category.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var incomeRepository: IncomeRepository

    @Inject
    lateinit var recipientMappingRepository: RecipientMappingRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var budgetService: BudgetService

    @Inject
    lateinit var expenseDao: ExpenseDao

    @Inject
    lateinit var categorizationService: CategorizationService

    @Inject
    lateinit var telemetryClient: TelemetryClient

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }


        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        // SMS may be split across multiple parts — concatenate them by sender
        // Also track the SMS timestamp per sender
        val smsByAddress = mutableMapOf<String, StringBuilder>()
        val smsTimestamps = mutableMapOf<String, Long>()
        for (message in messages) {
            val sender = message.displayOriginatingAddress ?: continue
            val body = message.messageBody ?: continue
            smsByAddress.getOrPut(sender) { StringBuilder() }.append(body)
            // Use the SMS timestamp from the carrier (actual send/receive time)
            if (!smsTimestamps.containsKey(sender)) {
                smsTimestamps[sender] = message.timestampMillis
            }
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                for ((sender, bodyBuilder) in smsByAddress) {
                    val body = bodyBuilder.toString()
                    val smsDate = smsTimestamps[sender] ?: System.currentTimeMillis()
                    // Gate on Confirmed, not expense-only keywords: incomes must also reach the parser.
                    if (SmsParser.isMpesaSms(sender) && body.contains("Confirmed", ignoreCase = true)) {
                        processTransaction(context, sender, body, smsDate)
                        continue
                    }
                    try {
                        val parser = SmsParserRegistry.findParser(sender, body)
                        if (parser != null && parser.displayName != "M-PESA") {
                            if (appPreferences.isBankEnabled(parser.displayName)) {
                                processTransaction(context, sender, body, smsDate)
                            } else {
                                Log.d(TAG, "Ignoring ${parser.displayName} SMS — bank tracking not enabled")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking bank SMS", e)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Process a transaction SMS from any supported source.
     *
     * Uses [SmsParserRegistry] to dispatch to the correct parser.
     * Saves the main expense and, if present, a separate transaction cost expense.
     * Applies auto-categorization using:
     * 1. Deterministic rules (Airtime → 202, Transaction Cost → 606)
     * 2. Recipient mapping (learned from previous categorizations)
     */
    private suspend fun processTransaction(context: Context, sender: String, smsBody: String, smsDate: Long = System.currentTimeMillis()) {
        try {
            when (val parsed = SmsParserRegistry.parseSms(sender, smsBody, smsDate)) {
                is ParsedSms.ExpenseResult -> handleExpenseResult(context, parsed, smsBody, smsDate, sender)
                is ParsedSms.IncomeResult -> handleIncomeResult(context, parsed.income, smsBody, sender)
                ParsedSms.NotARelevantMessage -> {
                    val current = NcbaPairedSmsResolver.Message(sender, smsBody, smsDate)
                    if (NcbaPairedSmsResolver.parseDebit(current) != null) {
                        // Retry approvals when their KES debit arrives later. Never import a debit alone.
                        val inbox = readNcbaPairingMessages(context, smsDate) + current
                        for (approval in inbox.distinct()) {
                            if (kotlin.math.abs(approval.timestamp - smsDate) > NcbaPairedSmsResolver.WINDOW_MS) continue
                            if (NcbaPairedSmsResolver.parseApproval(approval) == null) continue
                            val card = SmsParserRegistry.parseSms(approval.sender, approval.body, approval.timestamp)
                            if (card is ParsedSms.ExpenseResult) {
                                handleExpenseResult(context, card, approval.body, approval.timestamp, approval.sender, inbox)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS from $sender", e)
        }
    }

    /**
     * Emit a `sms_parsed` telemetry event with the parser display name.
     * Falls back to the raw sender if we can't identify a parser (rare — we
     * only reach this code when a parser already handled the SMS). No PII
     * is included: only the parser bucket (e.g. `"M-PESA"`) and the kind.
     */
    private fun logSmsParsed(sender: String, body: String, kind: String) {
        val source = SmsParserRegistry.findParser(sender, body)?.displayName ?: "other"
        telemetryClient.logEvent(
            TelemetryEvents.SMS_PARSED,
            mapOf(
                TelemetryEvents.PARAM_SOURCE to source,
                TelemetryEvents.PARAM_KIND to kind
            )
        )
    }

    /**
     * Handle an income SMS — dedupe by `transactionId`, persist, and (for
     * UNCATEGORIZED sources) prompt the user to pick a source.
     */
    private suspend fun handleIncomeResult(context: Context, income: IncomeTransaction, smsBody: String, senderAddress: String) {
        val toSave = income.copy(rawSms = smsBody)
        val rowId = incomeRepository.insertIfNew(toSave)
        if (rowId == null) {
            Log.d(TAG, "Income ${income.transactionId} already recorded, skipping")
            return
        }
        Log.d(
            TAG,
            "Saved income: Ksh${income.amount} source=${income.source.name} sender=${income.sender} txid=${income.transactionId}"
        )
        logSmsParsed(senderAddress, smsBody, TelemetryEvents.KIND_INCOME)

        if (income.source == IncomeSource.UNCATEGORIZED) {
            val displaySender = income.sender ?: "Unknown sender"
            NotificationHelper.showIncomeNotification(
                context = context,
                incomeId = rowId,
                amount = income.amount,
                sender = displaySender
            )
        }
    }

    private suspend fun handleExpenseResult(
        context: Context,
        parsed: ParsedSms.ExpenseResult,
        smsBody: String,
        smsDate: Long,
        senderAddress: String,
        ncbaInbox: List<NcbaPairedSmsResolver.Message>? = null
    ) {
        var mainExpense = parsed.expense.copy(rawSms = smsBody)

        // Handle card approval update — look up paired debit from inbox
        if (parsed.isCardApprovalUpdate) {
            val message = NcbaPairedSmsResolver.Message(senderAddress, smsBody, smsDate)
            mainExpense = NcbaPairedSmsResolver.resolve(
                message, ncbaInbox ?: readNcbaPairingMessages(context, smsDate)
            )?.expense ?: run {
                Log.d(TAG, "NCBA foreign card approval deferred: no unambiguous KES debit")
                return
            }
            if (expenseRepository.cardApprovalAlreadySaved(smsBody, smsDate)) return
        }

        // Check if transaction already exists
        val transactionId = mainExpense.transactionId
        if (transactionId != null && expenseRepository.transactionExists(transactionId)) {
            Log.d(TAG, "Transaction $transactionId already recorded, skipping")
            return
        }

        // Apply auto-categorization
        mainExpense = applyAutoCategorization(mainExpense)

        // Save the main expense
        // IGNORE, not REPLACE: simultaneous replay must preserve categorization and row identity.
        val expenseId = expenseRepository.saveExpenses(listOf(mainExpense)).single()
        if (expenseId <= 0) return
        Log.d(TAG, "Saved expense: ${mainExpense.paymentType.displayName()} " +
                "Ksh${mainExpense.amount} to ${mainExpense.recipientName ?: mainExpense.recipient}" +
                " [${mainExpense.source}]" +
                if (mainExpense.isCategorized) " (auto-categorized)" else "")

        // Track SMS parsed milestone and counter (fire-and-forget)
        appPreferences.recordFirstSmsParsed()
        appPreferences.incrementSmsParsedCount()
        logSmsParsed(senderAddress, smsBody, TelemetryEvents.KIND_EXPENSE)

        // Show notification to categorize (only if not auto-categorized)
        if (expenseId > 0 && !mainExpense.isCategorized) {
            val recipient = mainExpense.recipientName ?: mainExpense.recipient
            showCategorizeNotification(context, expenseId, mainExpense.amount, recipient)
        }

        // Nudge the user to reclassify auto-Misc catch-alls (SEND_MONEY fallback etc.).
        if (expenseId > 0 && mainExpense.isCategorized &&
            mainExpense.categoryId == KeywordRulesEngine.MISCELLANEOUS_CATEGORY_ID
        ) {
            val recipient = mainExpense.recipientName ?: mainExpense.recipient
            NotificationHelper.showMiscAutoCategorizedNotification(
                context = context,
                expenseId = expenseId,
                amount = mainExpense.amount,
                recipient = recipient
            )
        }

        // Check budget alerts (only for categorized expenses)
        if (mainExpense.isCategorized && mainExpense.categoryId != null) {
            try {
                val alerts = budgetService.checkBudgetsAfterExpense(mainExpense.categoryId)
                for (alert in alerts) {
                    val categoryName = alert.budget.categoryName ?: "Total Spending"
                    NotificationHelper.showBudgetAlertNotification(
                        context = context,
                        budgetId = alert.budget.id,
                        categoryName = categoryName,
                        spent = alert.spent,
                        budgetAmount = alert.budget.amount,
                        percentage = alert.percentage.toInt(),
                        threshold = alert.threshold
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking budget alerts", e)
            }
        }

        // Save the transaction cost as a separate auto-categorized expense
        val costExpense = parsed.transactionCost?.copy(rawSms = smsBody)
        if (costExpense != null) {
            val costTxId = costExpense.transactionId
            if (costTxId != null && !expenseRepository.transactionExists(costTxId)) {
                expenseRepository.saveExpense(costExpense)
                Log.d(TAG, "Saved transaction cost: Ksh${costExpense.amount}")
            }
        }
    }

    /**
     * Apply auto-categorization rules to an expense:
     * 1. Deterministic rules (Airtime → category 202)
     * 2. Recipient mapping (learned from user categorizations)
     * 3. CategorizationService (user rules + built-in KeywordRulesEngine —
     *    e.g. OPENAI → AI Subscriptions for card payments)
     */
    private suspend fun applyAutoCategorization(
        expense: com.pesatrack.domain.models.Expense
    ): com.pesatrack.domain.models.Expense {
        // Already categorized (shouldn't happen for main expense, but safety check)
        if (expense.isCategorized) return expense

        // 1. Deterministic rules
        when (expense.paymentType) {
            PaymentType.AIRTIME -> {
                return expense.copy(categoryId = 202L, isCategorized = true)
            }
            PaymentType.TRANSACTION_COST -> {
                return expense.copy(
                    categoryId = SmsParser.MPESA_TRANSACTION_COST_CATEGORY_ID,
                    isCategorized = true
                )
            }
            else -> { /* continue to mapping lookup */ }
        }

        // 2. Recipient mapping lookup
        //    Paybill payments use a composite (paybill, account) key so aggregator
        //    paybills (e.g. NCBA Loop 247247) don't misfire across unrelated merchants.
        val mappedCategory = if (expense.paymentType == PaymentType.PAY_BILL) {
            recipientMappingRepository.getCategoryForPaybill(
                paybillName = expense.recipientName,
                account = expense.recipient
            )
        } else {
            recipientMappingRepository.getCategoryForRecipientOrName(
                recipient = expense.recipient,
                recipientName = expense.recipientName
            )
        }
        if (mappedCategory != null) {
            Log.d(TAG, "Auto-categorized ${expense.recipientName ?: expense.recipient} via mapping → category $mappedCategory")
            return expense.copy(
                categoryId = mappedCategory,
                isCategorized = true
            )
        }

        // 3. CategorizationService (user rules + built-in keyword engine)
        val recipientKey = (expense.recipientName?.trim()?.lowercase()
            ?: expense.recipient.trim().lowercase())
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
            Log.d(TAG, "Auto-categorized $displayName via rules engine → category ${suggestion.categoryId} (${suggestion.categoryName})")
            return expense.copy(
                categoryId = suggestion.categoryId,
                isCategorized = true
            )
        }

        return expense
    }

    /**
     * Show notification prompting user to categorize the expense
     */
    private fun showCategorizeNotification(
        context: Context,
        expenseId: Long,
        amount: Double,
        recipient: String
    ) {
        NotificationHelper.showExpenseNotification(
            context = context,
            expenseId = expenseId,
            amount = amount,
            recipient = recipient
        )
    }

    /** One snapshot containing both debits and competing approvals, never two independent lookups. */
    private fun readNcbaPairingMessages(context: Context, targetTimestamp: Long): List<NcbaPairedSmsResolver.Message> {
        val messages = mutableListOf<NcbaPairedSmsResolver.Message>()
        try {
            // Three windows also cover competitors when retrying an approval near a late debit.
            val minTime = (targetTimestamp - 3 * NcbaPairedSmsResolver.WINDOW_MS).toString()
            val maxTime = (targetTimestamp + 3 * NcbaPairedSmsResolver.WINDOW_MS).toString()
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("body", "date", "date_sent", "address"),
                "address = ? COLLATE NOCASE AND ((date >= ? AND date <= ?) OR (date_sent >= ? AND date_sent <= ?))",
                arrayOf(NcbaPairedSmsResolver.SENDER, minTime, maxTime, minTime, maxTime),
                "date ASC"
            )?.use {
                while (it.moveToNext()) {
                    val body = it.getString(0) ?: continue
                    val date = it.getLong(2).takeIf { sent -> sent > 0 } ?: it.getLong(1)
                    messages.add(NcbaPairedSmsResolver.Message(it.getString(3), body, date))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to read NCBA pairing inbox", e)
        }
        return messages
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
