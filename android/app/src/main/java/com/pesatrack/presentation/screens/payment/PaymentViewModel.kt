package com.pesatrack.presentation.screens.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.PaymentRepository
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentResult
import com.pesatrack.domain.models.PaymentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()
    
    init {
        loadCategoryGroups()
    }
    
    private fun loadCategoryGroups() {
        viewModelScope.launch {
            categoryRepository.getCategoryGroups().collect { groups ->
                _uiState.update { it.copy(categoryGroups = groups) }
            }
        }
    }
    
    fun updatePhoneNumber(value: String) {
        _uiState.update { it.copy(phoneNumber = value, error = null) }
    }
    
    fun updateAmount(value: String) {
        // Only allow numeric input
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _uiState.update { it.copy(amount = value, error = null) }
        }
    }
    
    fun updateRecipient(value: String) {
        _uiState.update { it.copy(recipient = value, error = null) }
    }
    
    fun updateAccountNumber(value: String) {
        _uiState.update { it.copy(accountNumber = value, error = null) }
    }
    
    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }
    
    fun updatePaymentType(type: PaymentType) {
        _uiState.update { 
            it.copy(
                paymentType = type, 
                recipient = "",
                accountNumber = "",
                error = null
            ) 
        }
    }
    
    fun updateSelectedCategory(category: Category) {
        _uiState.update { it.copy(selectedCategory = category, error = null) }
    }
    
    fun initiatePayment() {
        val state = _uiState.value
        
        // Validate inputs
        if (state.phoneNumber.isBlank()) {
            _uiState.update { it.copy(error = "Please enter your phone number") }
            return
        }
        
        if (state.amount.isBlank()) {
            _uiState.update { it.copy(error = "Please enter an amount") }
            return
        }
        
        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(error = "Please enter a valid amount") }
            return
        }
        
        if (state.recipient.isBlank()) {
            _uiState.update { it.copy(error = "Please enter recipient details") }
            return
        }
        
        if (state.selectedCategory == null) {
            _uiState.update { it.copy(error = "Please select a category") }
            return
        }
        
        // Start payment process
        viewModelScope.launch {
            _uiState.update { it.copy(paymentStatus = PaymentStatus.Initiating, isLoading = true) }
            
            val result = paymentRepository.initiatePayment(
                phoneNumber = state.phoneNumber,
                amount = amount,
                paymentType = state.paymentType.name,
                recipient = state.recipient,
                categoryId = state.selectedCategory.id,
                notes = state.notes.ifBlank { null }
            )
            
            when (result) {
                is PaymentResult.StkPushSent -> {
                    _uiState.update { 
                        it.copy(
                            paymentStatus = PaymentStatus.WaitingForPin(result.customerMessage)
                        ) 
                    }
                    
                    // Start polling for payment status
                    pollPaymentStatus(result.checkoutRequestId, amount)
                }
                
                is PaymentResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            paymentStatus = PaymentStatus.Failed(result.message),
                            isLoading = false
                        ) 
                    }
                }
                
                else -> {
                    _uiState.update { 
                        it.copy(
                            paymentStatus = PaymentStatus.Failed("Unexpected response"),
                            isLoading = false
                        ) 
                    }
                }
            }
        }
    }
    
    private suspend fun pollPaymentStatus(checkoutRequestId: String, amount: Double) {
        _uiState.update { it.copy(paymentStatus = PaymentStatus.Processing) }
        
        val result = paymentRepository.pollPaymentStatus(checkoutRequestId)
        
        when (result) {
            is PaymentResult.Success -> {
                // Save expense locally
                saveExpense(result.transactionId, amount)
                
                _uiState.update { 
                    it.copy(
                        paymentStatus = PaymentStatus.Success(result.transactionId),
                        isLoading = false
                    ) 
                }
            }
            
            is PaymentResult.Error -> {
                _uiState.update { 
                    it.copy(
                        paymentStatus = PaymentStatus.Failed(result.message),
                        isLoading = false
                    ) 
                }
            }
            
            is PaymentResult.Timeout -> {
                _uiState.update { 
                    it.copy(
                        paymentStatus = PaymentStatus.Failed(result.message),
                        isLoading = false
                    ) 
                }
            }
            
            else -> {
                _uiState.update { 
                    it.copy(
                        paymentStatus = PaymentStatus.Failed("Unknown error"),
                        isLoading = false
                    ) 
                }
            }
        }
    }
    
    private suspend fun saveExpense(transactionId: String, amount: Double) {
        val state = _uiState.value
        
        val expense = Expense(
            transactionId = transactionId,
            amount = amount,
            recipient = state.recipient,
            recipientName = null, // Will be parsed from SMS if available
            categoryId = state.selectedCategory?.id,
            paymentType = state.paymentType,
            source = ExpenseSource.STK_PUSH,
            notes = state.notes.ifBlank { null },
            timestamp = System.currentTimeMillis(),
            isCategorized = state.selectedCategory != null
        )
        
        expenseRepository.saveExpense(expense)
    }
    
    fun resetPaymentState() {
        _uiState.update { 
            PaymentUiState(categoryGroups = it.categoryGroups)
        }
    }
}
