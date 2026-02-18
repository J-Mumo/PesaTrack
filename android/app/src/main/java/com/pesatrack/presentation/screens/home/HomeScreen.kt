package com.pesatrack.presentation.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.presentation.components.ExpenseCard
import com.pesatrack.utils.formatAsCurrency
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPayment: (String) -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToCategorize: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Text(
                text = "PesaTrack",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Monthly Summary Card
        item {
            MonthlySummaryCard(
                total = uiState.totalThisMonth,
                onViewAll = onNavigateToExpenses
            )
        }
        
        // Quick Actions
        item {
            QuickActionsRow(
                onPayClick = onNavigateToPayment
            )
        }
        
        // Uncategorized Alert
        if (uiState.uncategorizedCount > 0) {
            item {
                UncategorizedAlert(
                    count = uiState.uncategorizedCount,
                    onCategorize = {
                        // Navigate to first uncategorized expense
                        uiState.recentExpenses
                            .firstOrNull { !it.isCategorized }
                            ?.let { onNavigateToCategorize(it.id) }
                    }
                )
            }
        }
        
        // Recent Expenses Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Expenses",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onNavigateToExpenses) {
                    Text("View All")
                }
            }
        }
        
        // Recent Expenses List
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (uiState.recentExpenses.isEmpty()) {
            item {
                EmptyExpensesCard(onPayClick = { onNavigateToPayment("") })
            }
        } else {
            items(uiState.recentExpenses) { expense ->
                ExpenseCard(
                    expense = expense,
                    onClick = {
                        if (!expense.isCategorized) {
                            onNavigateToCategorize(expense.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MonthlySummaryCard(
    total: Double,
    onViewAll: () -> Unit
) {
    val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = currentMonth,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = total.formatAsCurrency(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Total expenses this month",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun QuickActionsRow(
    onPayClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            icon = Icons.Filled.Send,
            label = "Send Money",
            onClick = { onPayClick(PaymentType.SEND_MONEY.name) },
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Filled.ShoppingCart,
            label = "Buy Goods",
            onClick = { onPayClick(PaymentType.BUY_GOODS.name) },
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Filled.Receipt,
            label = "Pay Bill",
            onClick = { onPayClick(PaymentType.PAY_BILL.name) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun UncategorizedAlert(
    count: Int,
    onCategorize: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCategorize() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$count uncategorized expense${if (count > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Tap to categorize",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null
            )
        }
    }
}

@Composable
fun EmptyExpensesCard(
    onPayClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No expenses yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Start tracking by making a payment",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onPayClick) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Make a Payment")
            }
        }
    }
}
