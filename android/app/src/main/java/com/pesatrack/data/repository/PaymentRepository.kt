package com.pesatrack.data.repository

import com.pesatrack.data.remote.api.PesaTrackApi
import com.pesatrack.data.remote.dto.PaymentRequest
import com.pesatrack.data.remote.dto.PaymentResponse
import com.pesatrack.data.remote.dto.PaymentStatusResponse
import com.pesatrack.domain.models.PaymentResult
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for payment operations
 */
@Singleton
class PaymentRepository @Inject constructor(
    private val api: PesaTrackApi
) {
    
    /**
     * Initiate STK Push payment
     */
    suspend fun initiatePayment(
        phoneNumber: String,
        amount: Double,
        paymentType: String,
        recipient: String,
        categoryId: Long?,
        notes: String?
    ): PaymentResult {
        return try {
            val request = PaymentRequest(
                phoneNumber = formatPhoneNumber(phoneNumber),
                amount = amount,
                paymentType = paymentType,
                recipient = recipient,
                categoryId = categoryId,
                notes = notes
            )
            
            val response = api.initiatePayment(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                PaymentResult.StkPushSent(
                    checkoutRequestId = body.checkoutRequestId ?: "",
                    customerMessage = body.customerMessage ?: "Please enter your M-PESA PIN"
                )
            } else {
                val errorMessage = response.body()?.error 
                    ?: response.body()?.responseDescription
                    ?: "Failed to initiate payment"
                PaymentResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            PaymentResult.Error(e.message ?: "Network error occurred")
        }
    }
    
    /**
     * Poll for payment status with rate limit handling and exponential backoff
     *
     * Daraja API allows only 5 queries per 60 seconds, so we use:
     * - Initial delay of 5 seconds (give time for callback)
     * - Base polling interval of 12 seconds (max 5 polls per minute)
     * - Exponential backoff on rate limit (429) responses
     * - Max polling time of ~2 minutes
     *
     * @param checkoutRequestId The checkout request ID from STK Push
     * @param maxAttempts Maximum number of polling attempts
     * @param initialDelayMs Initial delay before first poll (wait for callback)
     * @param baseDelayMs Base delay between attempts in milliseconds
     */
    suspend fun pollPaymentStatus(
        checkoutRequestId: String,
        maxAttempts: Int = 10,
        initialDelayMs: Long = 5000,
        baseDelayMs: Long = 12000
    ): PaymentResult {
        // Wait before first poll to give callback time to arrive
        delay(initialDelayMs)
        
        var currentDelayMs = baseDelayMs
        
        repeat(maxAttempts) { attempt ->
            try {
                val response = api.getPaymentStatus(checkoutRequestId)
                
                when {
                    response.isSuccessful -> {
                        val body = response.body()
                        when (body?.status) {
                            "COMPLETED" -> {
                                return PaymentResult.Success(
                                    transactionId = body.transaction?.transactionId ?: "",
                                    amount = body.transaction?.amount ?: 0.0,
                                    transactionDate = body.transaction?.transactionDate
                                )
                            }
                            "FAILED" -> {
                                return PaymentResult.Error(
                                    body.reason ?: body.resultDesc ?: "Payment failed"
                                )
                            }
                            "RATE_LIMITED" -> {
                                // Backend returned rate limit, use retryAfter or exponential backoff
                                val retryAfter = body.retryAfter?.times(1000L) ?: (currentDelayMs * 2)
                                currentDelayMs = retryAfter.coerceAtMost(60000L)
                                android.util.Log.w("PaymentRepo", "Rate limited, waiting ${currentDelayMs}ms")
                            }
                            // PENDING - continue polling
                        }
                    }
                    response.code() == 429 -> {
                        // HTTP 429 Too Many Requests - extract Retry-After header
                        val retryAfterHeader = response.headers()["Retry-After"]
                        val retryAfterSeconds = retryAfterHeader?.toLongOrNull() ?: 15
                        currentDelayMs = (retryAfterSeconds * 1000).coerceAtMost(60000L)
                        android.util.Log.w("PaymentRepo", "HTTP 429, Retry-After: ${retryAfterSeconds}s")
                    }
                    // Other errors - continue with normal delay
                }
            } catch (e: Exception) {
                // Network error - continue polling with slightly longer delay
                android.util.Log.e("PaymentRepo", "Poll error: ${e.message}")
                currentDelayMs = (currentDelayMs * 1.5).toLong().coerceAtMost(60000L)
            }
            
            if (attempt < maxAttempts - 1) {
                delay(currentDelayMs)
                // Gradually increase delay (rate-limit-aware exponential backoff)
                currentDelayMs = (currentDelayMs * 1.2).toLong().coerceAtMost(30000L)
            }
        }
        
        return PaymentResult.Timeout("Payment status check timed out. Please check your M-PESA messages.")
    }
    
    /**
     * Check if backend is reachable
     */
    suspend fun checkConnection(): Boolean {
        return try {
            val response = api.healthCheck()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Format phone number to 254 format
     */
    private fun formatPhoneNumber(phone: String): String {
        var cleaned = phone.replace(Regex("[\\s\\-+]"), "")
        
        when {
            cleaned.startsWith("0") -> cleaned = "254" + cleaned.substring(1)
            cleaned.startsWith("7") || cleaned.startsWith("1") -> cleaned = "254$cleaned"
        }
        
        return cleaned
    }
}
