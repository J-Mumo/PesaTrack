package com.pesatrack.domain.models

/**
 * Sealed class representing payment operation results
 */
sealed class PaymentResult {
    
    /**
     * STK Push has been sent, waiting for user to enter PIN
     */
    data class StkPushSent(
        val checkoutRequestId: String,
        val customerMessage: String
    ) : PaymentResult()
    
    /**
     * Payment completed successfully
     */
    data class Success(
        val transactionId: String,
        val amount: Double,
        val transactionDate: String? = null
    ) : PaymentResult()
    
    /**
     * Payment failed
     */
    data class Error(
        val message: String
    ) : PaymentResult()
    
    /**
     * Payment status check timed out
     */
    data class Timeout(
        val message: String
    ) : PaymentResult()
}
