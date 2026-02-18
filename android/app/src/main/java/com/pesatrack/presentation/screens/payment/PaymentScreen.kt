package com.pesatrack.presentation.screens.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.presentation.components.CategorySelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    paymentType: String?,
    onNavigateBack: () -> Unit,
    onPaymentComplete: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(paymentType) {
        paymentType?.let {
            viewModel.updatePaymentType(PaymentType.valueOf(it))
        }
    }
    
    // Handle payment success
    LaunchedEffect(uiState.paymentStatus) {
        if (uiState.paymentStatus is PaymentStatus.Success) {
            // Delay to show success message
            kotlinx.coroutines.delay(2000)
            onPaymentComplete()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Payment") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val status = uiState.paymentStatus) {
            is PaymentStatus.WaitingForPin,
            is PaymentStatus.Processing -> {
                PaymentProcessingView(
                    status = status,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            
            is PaymentStatus.Success -> {
                PaymentSuccessView(
                    transactionId = status.transactionId,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            
            is PaymentStatus.Failed -> {
                PaymentFailedView(
                    message = status.message,
                    onRetry = { viewModel.resetPaymentState() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            
            else -> {
                PaymentForm(
                    uiState = uiState,
                    onPhoneNumberChange = viewModel::updatePhoneNumber,
                    onAmountChange = viewModel::updateAmount,
                    onRecipientChange = viewModel::updateRecipient,
                    onAccountNumberChange = viewModel::updateAccountNumber,
                    onNotesChange = viewModel::updateNotes,
                    onPaymentTypeChange = viewModel::updatePaymentType,
                    onCategorySelect = viewModel::updateSelectedCategory,
                    onSubmit = viewModel::initiatePayment,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentForm(
    uiState: PaymentUiState,
    onPhoneNumberChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onRecipientChange: (String) -> Unit,
    onAccountNumberChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onPaymentTypeChange: (PaymentType) -> Unit,
    onCategorySelect: (com.pesatrack.domain.models.Category) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Payment Type Selector
        Text(
            text = "Payment Type",
            style = MaterialTheme.typography.titleMedium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaymentType.values().forEach { type ->
                FilterChip(
                    selected = uiState.paymentType == type,
                    onClick = { onPaymentTypeChange(type) },
                    label = { Text(type.displayName()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Your Phone Number
        OutlinedTextField(
            value = uiState.phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = { Text("Your Phone Number") },
            placeholder = { Text("0712345678") },
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Amount
        OutlinedTextField(
            value = uiState.amount,
            onValueChange = onAmountChange,
            label = { Text("Amount (KES)") },
            placeholder = { Text("0.00") },
            leadingIcon = { Text("KES", modifier = Modifier.padding(start = 12.dp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Recipient (changes based on payment type)
        when (uiState.paymentType) {
            PaymentType.SEND_MONEY -> {
                OutlinedTextField(
                    value = uiState.recipient,
                    onValueChange = onRecipientChange,
                    label = { Text("Recipient Phone Number") },
                    placeholder = { Text("0712345678") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            PaymentType.BUY_GOODS -> {
                OutlinedTextField(
                    value = uiState.recipient,
                    onValueChange = onRecipientChange,
                    label = { Text("Till Number") },
                    placeholder = { Text("123456") },
                    leadingIcon = { Icon(Icons.Filled.Store, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            PaymentType.PAY_BILL -> {
                OutlinedTextField(
                    value = uiState.recipient,
                    onValueChange = onRecipientChange,
                    label = { Text("Paybill Number") },
                    placeholder = { Text("123456") },
                    leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = uiState.accountNumber,
                    onValueChange = onAccountNumberChange,
                    label = { Text("Account Number") },
                    placeholder = { Text("Account / Reference") },
                    leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Category Selection with Grouped Picker
        Text(
            text = "Category",
            style = MaterialTheme.typography.titleMedium
        )
        
        CategorySelector(
            selectedCategory = uiState.selectedCategory,
            categoryGroups = uiState.categoryGroups,
            onCategorySelected = onCategorySelect
        )
        
        // Notes
        OutlinedTextField(
            value = uiState.notes,
            onValueChange = onNotesChange,
            label = { Text("Notes (optional)") },
            placeholder = { Text("e.g., Lunch, Uber ride, Electricity bill") },
            leadingIcon = { Icon(Icons.Filled.Notes, contentDescription = null) },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Error message
        if (uiState.error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Pay Button
        Button(
            onClick = onSubmit,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Filled.Payment, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pay Now", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun PaymentProcessingView(
    status: PaymentStatus,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        when (status) {
            is PaymentStatus.WaitingForPin -> {
                Text(
                    text = "Enter M-PESA PIN",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            is PaymentStatus.Processing -> {
                Text(
                    text = "Processing Payment...",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please wait while we confirm your payment",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            else -> {}
        }
    }
}

@Composable
fun PaymentSuccessView(
    transactionId: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Payment Successful!",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Transaction ID: $transactionId",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun PaymentFailedView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Error,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Payment Failed",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}
