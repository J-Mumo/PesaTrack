package com.pesatrack.domain.models

/**
 * Domain model for an expense
 */
data class Expense(
    val id: Long = 0,
    val transactionId: String? = null,
    val amount: Double,
    val recipient: String,
    val recipientName: String? = null,
    val categoryId: Long? = null,
    val paymentType: PaymentType,
    val source: ExpenseSource,
    val notes: String? = null,
    val rawSms: String? = null,
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isCategorized: Boolean = false
)

/**
 * Payment types supported by M-PESA
 *
 * Only expense-producing transaction types are included.
 * Receive Money, Deposit, and Reversal are NOT expenses and are excluded.
 *
 * Stored as String in Room DB, so new values can be added without migration.
 */
enum class PaymentType {
    SEND_MONEY,      // Sent to person (name + phone)
    BUY_GOODS,       // Paid to till number (shop name)
    PAY_BILL,        // Sent to paybill for account
    WITHDRAW,        // Withdrawn from agent
    AIRTIME,         // Bought airtime (self or other)
    MPESA_CARD,      // Sent to M-PESA Card (global payments)
    TRANSACTION_COST, // M-PESA transaction cost (auto-categorized)
    BANK_DEBIT;      // Generic bank debit (for non-MPESA bank transactions)

    companion object {
        fun fromString(value: String): PaymentType {
            return try {
                valueOf(value) // Try enum name first
            } catch (e: Exception) {
                when (value) {
                    "Send Money" -> SEND_MONEY
                    "Buy Goods" -> BUY_GOODS
                    "Pay Bill" -> PAY_BILL
                    "Withdraw" -> WITHDRAW
                    "Airtime" -> AIRTIME
                    "M-PESA Card" -> MPESA_CARD
                    "Transaction Cost" -> TRANSACTION_COST
                    "Bank Debit" -> BANK_DEBIT
                    // Legacy values (for backward compat with old DB records)
                    "REVERSAL" -> SEND_MONEY
                    "RECEIVE_MONEY" -> SEND_MONEY
                    "DEPOSIT" -> SEND_MONEY
                    else -> SEND_MONEY // Default
                }
            }
        }
    }

    fun displayName(): String {
        return when (this) {
            SEND_MONEY -> "Send Money"
            BUY_GOODS -> "Buy Goods"
            PAY_BILL -> "Pay Bill"
            WITHDRAW -> "Withdraw"
            AIRTIME -> "Airtime"
            MPESA_CARD -> "M-PESA Card"
            TRANSACTION_COST -> "Transaction Cost"
            BANK_DEBIT -> "Bank Debit"
        }
    }
}

/**
 * Source of the expense record
 */
enum class ExpenseSource {
    STK_PUSH,    // Created via app-initiated payment (legacy, kept for DB compat)
    SMS_PARSED,  // Detected from M-PESA SMS
    SMS_BANK,    // Detected from bank SMS (NCBA, etc.)
    MANUAL;      // Manually entered

    companion object {
        fun fromString(value: String): ExpenseSource {
            return try {
                valueOf(value)
            } catch (e: Exception) {
                MANUAL // Default
            }
        }
    }
}
