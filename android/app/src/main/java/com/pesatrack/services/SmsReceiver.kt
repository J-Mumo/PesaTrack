package com.pesatrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.pesatrack.data.repository.ExpenseRepository
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
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var expenseRepository: ExpenseRepository

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
     */
    private fun processTransaction(context: Context, smsBody: String) {
        scope.launch {
            try {
                // Parse the SMS into main expense + optional transaction cost
                val parsed = SmsParser.parseSms(smsBody) ?: return@launch

                val mainExpense = parsed.expense

                // Check if transaction already exists
                val transactionId = mainExpense.transactionId
                if (transactionId != null && expenseRepository.transactionExists(transactionId)) {
                    Log.d(TAG, "Transaction $transactionId already recorded, skipping")
                    return@launch
                }

                // Save the main expense
                val expenseId = expenseRepository.saveExpense(mainExpense)
                Log.d(TAG, "Saved expense: ${mainExpense.paymentType.displayName()} " +
                        "Ksh${mainExpense.amount} to ${mainExpense.recipientName ?: mainExpense.recipient}")

                // Show notification to categorize the main expense
                if (expenseId > 0) {
                    val recipient = mainExpense.recipientName ?: mainExpense.recipient
                    showCategorizeNotification(context, expenseId, mainExpense.amount, recipient)
                }

                // Save the transaction cost as a separate auto-categorized expense
                val costExpense = parsed.transactionCost
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
