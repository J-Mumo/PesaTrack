package com.pesatrack.presentation.screens.manual_entry

import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.CategoryGroup
import com.pesatrack.domain.models.PaymentType

/**
 * UI State for the Manual Expense Entry screen
 */
data class ManualEntryUiState(
    /** Form fields */
    val amount: String = "",
    val recipient: String = "",
    val recipientName: String = "",
    val notes: String = "",
    val paymentType: PaymentType = PaymentType.CASH,
    val selectedDate: Long = System.currentTimeMillis(),

    /** Category selection */
    val categoryGroups: List<CategoryGroup> = emptyList(),
    val selectedCategory: Category? = null,

    /** Validation */
    val amountError: String? = null,
    val recipientNameError: String? = null,
    val recipientError: String? = null,

    /** Save state */
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    /** Payment types available for manual entry (excludes TRANSACTION_COST which is auto-only) */
    val availablePaymentTypes: List<PaymentType>
        get() = listOf(
            PaymentType.CASH,
            PaymentType.BUY_GOODS,
            PaymentType.SEND_MONEY,
            PaymentType.PAY_BILL,
            PaymentType.WITHDRAW,
            PaymentType.AIRTIME,
            PaymentType.MPESA_CARD,
            PaymentType.BANK_DEBIT
        )
}
