package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Expense entity representing a single expense/transaction
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["timestamp"]),
        Index(value = ["transactionId"], unique = true)
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** M-PESA transaction ID (e.g., ABC123XYZ) */
    val transactionId: String? = null,
    
    /** Amount in KES */
    val amount: Double,
    
    /** Recipient identifier (phone number, till, or paybill) */
    val recipient: String,
    
    /** Recipient name (parsed from SMS or entered by user) */
    val recipientName: String? = null,
    
    /** Category ID (foreign key) */
    val categoryId: Long? = null,
    
    /** Payment type: SEND_MONEY, BUY_GOODS, PAY_BILL */
    val paymentType: String,
    
    /** Source of the expense: STK_PUSH, SMS_PARSED, MANUAL */
    val source: String,
    
    /** Optional notes */
    val notes: String? = null,
    
    /** Raw SMS message body (for re-parsing when patterns improve) */
    val rawSms: String? = null,
    
    /** Transaction timestamp */
    val timestamp: Long,
    
    /** Record creation timestamp */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Whether the expense has been categorized */
    val isCategorized: Boolean = false
)
