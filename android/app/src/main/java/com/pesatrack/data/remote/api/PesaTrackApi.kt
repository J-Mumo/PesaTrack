package com.pesatrack.data.remote.api

import com.pesatrack.data.remote.dto.PaymentRequest
import com.pesatrack.data.remote.dto.PaymentResponse
import com.pesatrack.data.remote.dto.PaymentStatusResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * PesaTrack API interface for Retrofit
 */
interface PesaTrackApi {
    
    /**
     * Initiate STK Push payment
     */
    @POST("api/payment/initiate")
    suspend fun initiatePayment(
        @Body request: PaymentRequest
    ): Response<PaymentResponse>
    
    /**
     * Query payment status
     */
    @GET("api/payment/status/{checkoutRequestId}")
    suspend fun getPaymentStatus(
        @Path("checkoutRequestId") checkoutRequestId: String
    ): Response<PaymentStatusResponse>
    
    /**
     * Health check
     */
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, Any>>
}
