package com.pesatrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.RecipientMappingRepository
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.utils.SmsParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver for incoming SMS messages
 *
 * Listens for M-PESA confirmation SMS and automatically
 * parses them into expense records. Also extracts transaction
 * costs and saves them as separate auto-categorized expenses.
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

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        // M-PESA SMS may be split across multiple parts — concatenate them
        val smsByAddress = mutableMapOf<String, StringBuilder>()
        for (message in messages) {
            val sender = message.displayOriginatingAddress ?: continue
            val body = message.messageBody ?: continue
            smsByAddress.getOrPut(sender) { StringBuilder() }.append(body)
        }

        for ((sender, bodyBuilder) in smsByAddress) {
            val body = bodyBuilder.toString()
            // Check if it's an M-PESA message
            if (SmsParser.isMpesaSms(sender) && SmsParser.isTransactionSms(body)) {
                processTransaction(context, body)
            }
        }
    }

    /**
     * Process the M-PESA transaction SMS.
     * Saves the main expense and, if present, a separate transaction cost expense.
     * Applies auto-categorization using:
     * 1. Deterministic rules (Airtime → 1001, Transaction Cost → 811)
     * 2. Recipient mapping (learned from previous categorizations)
     */
    private fun processTransaction(context: Context, smsBody: String) {
        scope.launch {
            try {
                // Parse the SMS into main expense + optional transaction cost
                val parsed = SmsParser.parseSms(smsBody) ?: return@launch

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
                        if (mainExpense.isCategorized) " (auto-categorized)" else "")

                // Show notification to categorize (only if not auto-categorized)
                if (expenseId > 0 && !mainExpense.isCategorized) {
                    val recipient = mainExpense.recipientName ?: mainExpense.recipient
                    showCategorizeNotification(context, expenseId, mainExpense.amount, recipient)
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
                Log.e(TAG, "Error processing M-PESA SMS", e)
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
