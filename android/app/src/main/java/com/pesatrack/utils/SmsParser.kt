package com.pesatrack.utils

import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * M-PESA SMS Parser
 * 
 * Parses M-PESA confirmation SMS messages to extract transaction details.
 * 
 * SMS Formats:
 * - Send Money: "ABC123XYZ Confirmed. Ksh1,000.00 sent to John Doe 0712345678 on 15/1/24 at 12:34 PM..."
 * - Buy Goods: "ABC123XYZ Confirmed. Ksh500.00 paid to SHOP NAME. on 15/1/24 at 2:30 PM..."
 * - Pay Bill: "ABC123XYZ Confirmed. Ksh2,000.00 paid to COMPANY NAME for account 12345 on 15/1/24 at 3:00 PM..."
 */
object SmsParser {
    
    // M-PESA sender IDs
    private val MPESA_SENDERS = listOf("MPESA", "M-PESA", "Safaricom")
    
    // Regex patterns
    private val TRANSACTION_ID_PATTERN = Pattern.compile("^([A-Z0-9]{10})")
    private val AMOUNT_PATTERN = Pattern.compile("Ksh([\\d,]+\\.\\d{2})")
    private val SEND_MONEY_PATTERN = Pattern.compile("sent to (.+?) (\\d{10}|\\d{12})")
    private val BUY_GOODS_PATTERN = Pattern.compile("paid to ([^.]+?)\\. on")
    private val PAY_BILL_PATTERN = Pattern.compile("paid to (.+?) for account (.+?) on")
    private val DATE_PATTERN = Pattern.compile("on (\\d{1,2}/\\d{1,2}/\\d{2,4}) at (\\d{1,2}:\\d{2} [AP]M)")
    
    /**
     * Check if SMS is from M-PESA
     */
    fun isMpesaSms(sender: String?): Boolean {
        if (sender == null) return false
        return MPESA_SENDERS.any { sender.contains(it, ignoreCase = true) }
    }
    
    /**
     * Check if SMS is a transaction confirmation
     */
    fun isTransactionSms(message: String): Boolean {
        return message.contains("Confirmed", ignoreCase = true) &&
               message.contains("Ksh", ignoreCase = true) &&
               (message.contains("sent to", ignoreCase = true) ||
                message.contains("paid to", ignoreCase = true))
    }
    
    /**
     * Parse M-PESA SMS into an Expense object
     * 
     * @param message The SMS message body
     * @return Expense object if parsing successful, null otherwise
     */
    fun parseSms(message: String): Expense? {
        if (!isTransactionSms(message)) return null
        
        try {
            // Extract transaction ID
            val transactionId = extractTransactionId(message) ?: return null
            
            // Extract amount
            val amount = extractAmount(message) ?: return null
            
            // Determine payment type and extract recipient
            val (paymentType, recipient, recipientName) = extractRecipientInfo(message)
                ?: return null
            
            // Extract timestamp
            val timestamp = extractTimestamp(message) ?: System.currentTimeMillis()
            
            return Expense(
                transactionId = transactionId,
                amount = amount,
                recipient = recipient,
                recipientName = recipientName,
                paymentType = paymentType,
                source = ExpenseSource.SMS_PARSED,
                timestamp = timestamp,
                isCategorized = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Extract transaction ID from SMS
     */
    private fun extractTransactionId(message: String): String? {
        val matcher = TRANSACTION_ID_PATTERN.matcher(message)
        return if (matcher.find()) matcher.group(1) else null
    }
    
    /**
     * Extract amount from SMS
     */
    private fun extractAmount(message: String): Double? {
        val matcher = AMOUNT_PATTERN.matcher(message)
        return if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            amountStr?.toDoubleOrNull()
        } else null
    }
    
    /**
     * Extract recipient information based on payment type
     * 
     * @return Triple of (PaymentType, recipient identifier, recipient name)
     */
    private fun extractRecipientInfo(message: String): Triple<PaymentType, String, String?>? {
        // Try Pay Bill first (more specific pattern)
        var matcher = PAY_BILL_PATTERN.matcher(message)
        if (matcher.find()) {
            val recipientName = matcher.group(1)?.trim()
            val accountNumber = matcher.group(2)?.trim()
            return Triple(
                PaymentType.PAY_BILL,
                accountNumber ?: recipientName ?: "",
                recipientName
            )
        }
        
        // Try Send Money
        matcher = SEND_MONEY_PATTERN.matcher(message)
        if (matcher.find()) {
            val recipientName = matcher.group(1)?.trim()
            val phoneNumber = matcher.group(2)?.trim()
            return Triple(
                PaymentType.SEND_MONEY,
                phoneNumber ?: "",
                recipientName
            )
        }
        
        // Try Buy Goods
        matcher = BUY_GOODS_PATTERN.matcher(message)
        if (matcher.find()) {
            val recipientName = matcher.group(1)?.trim()
            return Triple(
                PaymentType.BUY_GOODS,
                recipientName ?: "",
                recipientName
            )
        }
        
        return null
    }
    
    /**
     * Extract timestamp from SMS
     */
    private fun extractTimestamp(message: String): Long? {
        val matcher = DATE_PATTERN.matcher(message)
        if (matcher.find()) {
            val dateStr = matcher.group(1)
            val timeStr = matcher.group(2)
            
            return try {
                // Try different date formats
                val formats = listOf(
                    SimpleDateFormat("d/M/yy h:mm a", Locale.US),
                    SimpleDateFormat("d/M/yyyy h:mm a", Locale.US),
                    SimpleDateFormat("dd/MM/yy h:mm a", Locale.US),
                    SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.US)
                )
                
                val combinedDateTime = "$dateStr $timeStr"
                
                for (format in formats) {
                    try {
                        return format.parse(combinedDateTime)?.time
                    } catch (e: Exception) {
                        continue
                    }
                }
                
                // If parsing fails, return current time
                System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
        
        return null
    }
}
