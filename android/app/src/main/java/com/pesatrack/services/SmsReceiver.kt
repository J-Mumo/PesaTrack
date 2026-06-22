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
import com.pesatrack.utils.SmsParser
import com.pesatrack.utils.parsers.ParsedSms
import com.pesatrack.utils.parsers.SmsParserRegistry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern
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

        for ((sender, bodyBuilder) in smsByAddress) {
            val body = bodyBuilder.toString()
            val smsDate = smsTimestamps[sender] ?: System.currentTimeMillis()

            // M-PESA SMS — always processed (no preference check needed)
            if (SmsParser.isMpesaSms(sender) && SmsParser.isTransactionSms(body)) {
                processTransaction(context, sender, body, smsDate)
                continue
            }

            // Bank SMS — check if the sender's bank parser is enabled
            scope.launch {
                try {
                    val parser = SmsParserRegistry.findParser(sender, body)
                    if (parser != null && parser.displayName != "M-PESA") {
                        // Check if this bank is enabled in preferences
                        val bankEnabled = appPreferences.isBankEnabled(parser.displayName)
                        if (bankEnabled) {
                            processTransaction(context, sender, body, smsDate)
                        } else {
                            Log.d(TAG, "Ignoring ${parser.displayName} SMS — bank tracking not enabled")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking bank SMS", e)
                }
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
    private fun processTransaction(context: Context, sender: String, smsBody: String, smsDate: Long = System.currentTimeMillis()) {
        scope.launch {
            try {
                when (val parsed = SmsParserRegistry.parseSms(sender, smsBody, smsDate)) {
                    is ParsedSms.ExpenseResult -> handleExpenseResult(context, parsed, smsBody, smsDate)
                    is ParsedSms.IncomeResult -> handleIncomeResult(context, parsed.income, smsBody)
                    ParsedSms.NotARelevantMessage -> Unit
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS from $sender", e)
            }
        }
    }

    /**
     * Handle an income SMS — dedupe by `transactionId`, persist, and (for
     * UNCATEGORIZED sources) prompt the user to pick a source.
     */
    private suspend fun handleIncomeResult(context: Context, income: IncomeTransaction, smsBody: String) {
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
        smsDate: Long
    ) {
        var mainExpense = parsed.expense.copy(rawSms = smsBody)

        // Handle card approval update — look up paired debit from inbox
        if (parsed.isCardApprovalUpdate) {
            handleCardApprovalUpdate(context, mainExpense, smsDate)
            return
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
        val expenseId = expenseRepository.saveExpense(mainExpense)
        Log.d(TAG, "Saved expense: ${mainExpense.paymentType.displayName()} " +
                "Ksh${mainExpense.amount} to ${mainExpense.recipientName ?: mainExpense.recipient}" +
                " [${mainExpense.source}]" +
                if (mainExpense.isCategorized) " (auto-categorized)" else "")

        // Track SMS parsed milestone and counter (fire-and-forget)
        appPreferences.recordFirstSmsParsed()
        appPreferences.incrementSmsParsedCount()

        // Show notification to categorize (only if not auto-categorized)
        if (expenseId > 0 && !mainExpense.isCategorized) {
            val recipient = mainExpense.recipientName ?: mainExpense.recipient
            showCategorizeNotification(context, expenseId, mainExpense.amount, recipient)
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
        val mappedCategory = recipientMappingRepository.getCategoryForRecipientOrName(
            recipient = expense.recipient,
            recipientName = expense.recipientName
        )
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

    /**
     * Handle a card approval SMS by looking up the paired generic debit SMS
     * from the device inbox to get the KES amount, then saving a complete expense.
     *
     * Strategy:
     * - Card approval has: merchant name, foreign currency amount, card last-4
     * - Paired generic debit has: KES amount, bank ref, timestamp
     * - We query the SMS inbox within a 2-minute window for the debit SMS
     * - If found, use KES amount from debit + merchant from approval
     * - If not found, save with the foreign currency amount as fallback
     */
    private suspend fun handleCardApprovalUpdate(
        context: Context,
        cardApproval: com.pesatrack.domain.models.Expense,
        smsDate: Long
    ) {
        // Look up the paired debit SMS from inbox (2-minute window)
        val kesAmount = lookupCardDebitFromInbox(context, smsDate)

        val finalAmount = kesAmount ?: cardApproval.amount // Fallback to foreign currency amount
        val bankRef = lookupCardDebitRefFromInbox(context, smsDate)

        val expense = cardApproval.copy(
            amount = finalAmount,
            transactionId = bankRef, // Use bank ref as transaction ID for dedup
        )

        // Check if already exists (by bank ref)
        if (bankRef != null && expenseRepository.transactionExists(bankRef)) {
            Log.d(TAG, "Card payment $bankRef already recorded, skipping")
            return
        }

        // Apply auto-categorization and save
        val categorized = applyAutoCategorization(expense)
        val expenseId = expenseRepository.saveExpense(categorized)
        Log.d(TAG, "Saved card payment: KES $finalAmount at ${cardApproval.recipientName} (ref: $bankRef)")

        if (expenseId > 0 && !categorized.isCategorized) {
            showCategorizeNotification(
                context, expenseId, finalAmount,
                cardApproval.recipientName ?: "Card Payment"
            )
        }
    }

    /**
     * Query the SMS inbox for the paired NCBA generic debit SMS within 2 minutes
     * of the card approval timestamp. Extracts the KES amount.
     *
     * Pattern: "Your account 763****018 has been debited with KES 1,574.87 on ..."
     */
    private fun lookupCardDebitFromInbox(context: Context, approvalTimestamp: Long): Double? {
        val windowMs = 2 * 60 * 1000L // 2 minutes
        val body = findNcbaDebitSmsBody(context, approvalTimestamp, windowMs) ?: return null

        val amountPattern = Pattern.compile(
            "has been debited with KES\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = amountPattern.matcher(body)
        return if (matcher.find()) {
            matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        } else null
    }

    /**
     * Query the SMS inbox for the paired NCBA generic debit SMS within 2 minutes
     * and extract the bank reference (Ref: FTC...).
     */
    private fun lookupCardDebitRefFromInbox(context: Context, approvalTimestamp: Long): String? {
        val windowMs = 2 * 60 * 1000L // 2 minutes
        val body = findNcbaDebitSmsBody(context, approvalTimestamp, windowMs) ?: return null

        val refPattern = Pattern.compile("Ref:\\s*(\\S+)", Pattern.CASE_INSENSITIVE)
        val matcher = refPattern.matcher(body)
        return if (matcher.find()) matcher.group(1)?.trimEnd('.') else null
    }

    /**
     * Find the closest NCBA debit SMS body from the inbox within a time window.
     */
    private fun findNcbaDebitSmsBody(context: Context, targetTimestamp: Long, windowMs: Long): String? {
        try {
            val minTime = (targetTimestamp - windowMs).toString()
            val maxTime = targetTimestamp.toString()

            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("body", "date"),
                "address LIKE ? AND date >= ? AND date <= ? AND body LIKE ?",
                arrayOf("%NCBA%", minTime, maxTime, "%has been debited%"),
                "date DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SMS inbox for card debit", e)
        }
        return null
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
