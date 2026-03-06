package com.pesatrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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
 * parses them into expense records.
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
        
        for (message in messages) {
            val sender = message.displayOriginatingAddress
            val body = message.messageBody
            
            // Check if it's an M-PESA message
            if (SmsParser.isMpesaSms(sender) && SmsParser.isTransactionSms(body)) {
                processTransaction(context, body)
            }
        }
    }
    
    /**
     * Process the M-PESA transaction SMS
     */
    private fun processTransaction(context: Context, smsBody: String) {
        scope.launch {
            try {
                // Parse the SMS
                val expense = SmsParser.parseSms(smsBody)
                
                if (expense != null) {
                    // Check if transaction already exists
                    val transactionId = expense.transactionId
                    if (transactionId != null && expenseRepository.transactionExists(transactionId)) {
                        // Transaction already recorded (probably via STK Push)
                        return@launch
                    }
                    
                    // Save the expense
                    val expenseId = expenseRepository.saveExpense(expense)
                    
                    // Show notification to categorize
                    if (expenseId > 0) {
                        val recipient = expense.recipientName ?: expense.recipient
                        showCategorizeNotification(context, expenseId, expense.amount, recipient)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Show notification prompting user to categorize the expense
     */
    private fun showCategorizeNotification(context: Context, expenseId: Long, amount: Double, recipient: String) {
        NotificationHelper.showExpenseNotification(
            context = context,
            expenseId = expenseId,
            amount = amount,
            recipient = recipient
        )
    }
}
