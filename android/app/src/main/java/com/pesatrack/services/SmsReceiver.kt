package com.pesatrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.utils.SmsParser
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
    lateinit var recipientMappingRepository: RecipientMappingRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var budgetService: BudgetService

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
                // Parse the SMS using the registry (dispatches to correct parser)
                // Pass smsDate so parsers use the actual SMS timestamp instead of current time
                val parsed = SmsParserRegistry.parseTransaction(sender, smsBody, smsDate) ?: return@launch

                var mainExpense = parsed.expense.copy(rawSms = smsBody)

                // Check if transaction already exists
                val transactionId = mainExpense.transactionId
                if (transactionId != null && expenseRepository.transactionExists(transactionId)) {
                    Log.d(TAG, "Transaction $transactionId already recorded, skipping")
                    return@launch
                }

                // Apply auto-categorization
                mainExpense = applyAutoCategorization(mainExpense)

                // Save the main expense
                val expenseId = expenseRepository.saveExpense(mainExpense)
                Log.d(TAG, "Saved expense: ${mainExpense.paymentType.displayName()} " +
                        "Ksh${mainExpense.amount} to ${mainExpense.recipientName ?: mainExpense.recipient}" +
                        " [${mainExpense.source}]" +
                        if (mainExpense.isCategorized) " (auto-categorized)" else "")

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
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS from $sender", e)
            }
        }
    }

    /**
     * Apply auto-categorization rules to an expense:
     * 1. Deterministic rules (Airtime → category 202)
     * 2. Recipient mapping (learned from user categorizations)
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

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
