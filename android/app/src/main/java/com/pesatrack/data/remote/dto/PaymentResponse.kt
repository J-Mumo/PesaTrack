package com.pesatrack.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response from STK Push initiation
 */
data class PaymentResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("checkoutRequestId")
    val checkoutRequestId: String?,
    
    @SerializedName("merchantRequestId")
    val merchantRequestId: String?,
    
    @SerializedName("responseDescription")
    val responseDescription: String?,
    
    @SerializedName("customerMessage")
    val customerMessage: String?,
    
    @SerializedName("error")
    val error: String?
)

/**
 * Response from payment status query
 */
data class PaymentStatusResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("status")
    val status: String?, // PENDING, COMPLETED, FAILED, RATE_LIMITED
    
    @SerializedName("transaction")
    val transaction: TransactionDetails?,
    
    @SerializedName("reason")
    val reason: String?,
    
    @SerializedName("resultCode")
    val resultCode: String?,
    
    @SerializedName("resultDesc")
    val resultDesc: String?,
    
    @SerializedName("retryAfter")
    val retryAfter: Int?, // Seconds to wait before retrying (for rate limiting)
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("fromCache")
    val fromCache: Boolean? = false
)

/**
 * Transaction details from callback
 */
data class TransactionDetails(
    @SerializedName("transactionId")
    val transactionId: String?,
    
    @SerializedName("amount")
    val amount: Double?,
    
    @SerializedName("phoneNumber")
    val phoneNumber: String?,
    
    @SerializedName("transactionDate")
    val transactionDate: String?,
    
    @SerializedName("status")
    val status: String?,
    
    @SerializedName("categoryId")
    val categoryId: Long?,
    
    @SerializedName("notes")
    val notes: String?,
    
    @SerializedName("recipient")
    val recipient: String?,
    
    @SerializedName("paymentType")
    val paymentType: String?
)
