package com.pesatrack.presentation.screens.manual_entry

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.presentation.components.GroupedCategoryPicker
import com.pesatrack.presentation.components.getCategoryIcon
import com.pesatrack.presentation.theme.getCategoryColor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManualEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showPaymentTypeMenu by remember { mutableStateOf(false) }

    // Navigate back after saving
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { viewModel.saveExpense() },
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Expense", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== Amount ====================
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text("Amount (KES)") },
                placeholder = { Text("0.00") },
                leadingIcon = {
                    Text(
                        "KES",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = uiState.amountError != null,
                supportingText = uiState.amountError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ==================== Recipient Name ====================
            OutlinedTextField(
                value = uiState.recipientName,
                onValueChange = { viewModel.updateRecipientName(it) },
                label = { Text("Recipient Name") },
                placeholder = { Text("e.g. Naivas Supermarket") },
                leadingIcon = {
                    Icon(Icons.Filled.Badge, contentDescription = null)
                },
                isError = uiState.recipientNameError != null,
                supportingText = uiState.recipientNameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ==================== Recipient Number (optional) ====================
            OutlinedTextField(
                value = uiState.recipient,
                onValueChange = { viewModel.updateRecipient(it) },
                label = { Text("Phone / Till / Paybill Number (optional)") },
                placeholder = { Text("e.g. 0712345678") },
                leadingIcon = {
                    Icon(Icons.Filled.Phone, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = uiState.recipientError != null,
                supportingText = uiState.recipientError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ==================== Payment Type ====================
            ExposedDropdownMenuBox(
                expanded = showPaymentTypeMenu,
                onExpandedChange = { showPaymentTypeMenu = it }
            ) {
                OutlinedTextField(
                    value = uiState.paymentType.displayName(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Type") },
                    leadingIcon = {
                        Icon(
                            imageVector = getPaymentTypeIcon(uiState.paymentType),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPaymentTypeMenu)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = showPaymentTypeMenu,
                    onDismissRequest = { showPaymentTypeMenu = false }
                ) {
                    uiState.availablePaymentTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName()) },
                            leadingIcon = {
                                Icon(
                                    imageVector = getPaymentTypeIcon(type),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                viewModel.updatePaymentType(type)
                                showPaymentTypeMenu = false
                            }
                        )
                    }
                }
            }

            // ==================== Date ====================
            val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }

            OutlinedTextField(
                value = dateFormatter.format(Date(uiState.selectedDate)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                leadingIcon = {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Change date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ==================== Category ====================
            Text(
                text = "Category (optional)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CategorySelectionCard(
                selectedCategory = uiState.selectedCategory,
                categoryGroups = uiState.categoryGroups,
                onClick = { showCategoryPicker = true }
            )

            // ==================== Notes ====================
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Add any notes about this expense") },
                leadingIcon = {
                    Icon(Icons.Filled.Notes, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // ==================== Error ====================
            if (uiState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Bottom spacer for scroll clearance above the save button
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ==================== Date Picker Dialog ====================
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // Adjust to keep the current time of day but change the date
                            val selectedCal = Calendar.getInstance().apply { timeInMillis = millis }
                            val currentCal = Calendar.getInstance().apply { timeInMillis = uiState.selectedDate }
                            selectedCal.set(Calendar.HOUR_OF_DAY, currentCal.get(Calendar.HOUR_OF_DAY))
                            selectedCal.set(Calendar.MINUTE, currentCal.get(Calendar.MINUTE))
                            selectedCal.set(Calendar.SECOND, currentCal.get(Calendar.SECOND))
                            viewModel.updateDate(selectedCal.timeInMillis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ==================== Category Picker Dialog ====================
    if (showCategoryPicker) {
        GroupedCategoryPicker(
            categoryGroups = uiState.categoryGroups,
            selectedCategoryId = uiState.selectedCategory?.id,
            onCategorySelected = { category ->
                viewModel.selectCategory(category)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

// ==================== Helper Composables ====================

@Composable
fun CategorySelectionCard(
    selectedCategory: com.pesatrack.domain.models.Category?,
    categoryGroups: List<com.pesatrack.domain.models.CategoryGroup>,
    onClick: () -> Unit
) {
    val parentName = remember(selectedCategory, categoryGroups) {
        selectedCategory?.parentId?.let { parentId ->
            categoryGroups.find { it.parent.id == parentId }?.parent?.name
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selectedCategory != null) {
                getCategoryColor(selectedCategory.color).copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedCategory != null) {
                val categoryColor = getCategoryColor(selectedCategory.color)

                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = categoryColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getCategoryIcon(selectedCategory.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedCategory.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (parentName != null) {
                        Text(
                            text = parentName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Filled.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No category selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap to select a category",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Returns an appropriate icon for each payment type
 */
@Composable
fun getPaymentTypeIcon(paymentType: PaymentType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (paymentType) {
        PaymentType.CASH -> Icons.Filled.Payments
        PaymentType.SEND_MONEY -> Icons.Filled.Send
        PaymentType.BUY_GOODS -> Icons.Filled.ShoppingCart
        PaymentType.PAY_BILL -> Icons.Filled.Receipt
        PaymentType.WITHDRAW -> Icons.Filled.AccountBalance
        PaymentType.AIRTIME -> Icons.Filled.Phone
        PaymentType.MPESA_CARD -> Icons.Filled.CreditCard
        PaymentType.TRANSACTION_COST -> Icons.Filled.Money
        PaymentType.BANK_DEBIT -> Icons.Filled.AccountBalance
    }
}
