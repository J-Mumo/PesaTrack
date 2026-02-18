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
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isCategorized: Boolean = false
)

/**
 * Payment types supported by M-PESA
 */
enum class PaymentType {
    SEND_MONEY,
    BUY_GOODS,
    PAY_BILL;

    companion object {
        fun fromString(value: String): PaymentType {
            return when (value) {
                "Send Money" -> SEND_MONEY
                "Buy Goods" -> BUY_GOODS
                "Pay Bill" -> PAY_BILL
                else -> SEND_MONEY // Default
            }
        }
    }

    fun displayName(): String {
        return when (this) {
            SEND_MONEY -> "Send Money"
            BUY_GOODS -> "Buy Goods"
            PAY_BILL -> "Pay Bill"
        }
    }
}

/**
 * Source of the expense record
 */
enum class ExpenseSource {
    STK_PUSH,    // Created via app-initiated payment
    SMS_PARSED,  // Detected from M-PESA SMS
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
