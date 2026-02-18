package com.pesatrack.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request to initiate STK Push payment
 */
data class PaymentRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("paymentType")
    val paymentType: String,
    
    @SerializedName("recipient")
    val recipient: String,
    
    @SerializedName("accountReference")
    val accountReference: String = "PesaTrack",
    
    @SerializedName("transactionDesc")
    val transactionDesc: String = "Payment",
    
    @SerializedName("categoryId")
    val categoryId: Long? = null,
    
    @SerializedName("notes")
    val notes: String? = null
)
