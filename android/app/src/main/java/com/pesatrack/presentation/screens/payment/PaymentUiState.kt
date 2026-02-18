package com.pesatrack.presentation.screens.payment

import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.CategoryGroup
import com.pesatrack.domain.models.PaymentType

/**
 * UI State for the Payment screen
 */
data class PaymentUiState(
    val phoneNumber: String = "",
    val amount: String = "",
    val recipient: String = "",
    val accountNumber: String = "",
    val notes: String = "",
    val paymentType: PaymentType = PaymentType.SEND_MONEY,
    val selectedCategory: Category? = null,
    val categoryGroups: List<CategoryGroup> = emptyList(),
    
    // Payment process state
    val isLoading: Boolean = false,
    val paymentStatus: PaymentStatus = PaymentStatus.Idle,
    val error: String? = null
)

/**
 * Payment process status
 */
sealed class PaymentStatus {
    object Idle : PaymentStatus()
    object Initiating : PaymentStatus()
    data class WaitingForPin(val message: String) : PaymentStatus()
    object Processing : PaymentStatus()
    data class Success(val transactionId: String) : PaymentStatus()
    data class Failed(val message: String) : PaymentStatus()
}
