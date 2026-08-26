package com.pesatrack.presentation.screens.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.presentation.components.ExpenseCard
import com.pesatrack.utils.formatAsCurrency
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCategorize: (Long) -> Unit,
    onNavigateToManualEntry: () -> Unit = {},
    viewModel: ExpensesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToManualEntry,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add expense",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.expenses.isEmpty()) {
            EmptyExpenses(modifier = Modifier.padding(paddingValues))
        } else {
            // Filter client-side. `expenses` is already the full history the
            // user cares about; there's no pagination boundary to worry
            // about, and the query has to match across recipient, notes,
            // category, and amount — cheaper to do it in Kotlin than to add
            // a dedicated DAO query.
            val query = uiState.searchQuery.trim()
            val displayed = remember(uiState.expenses, query) {
                if (query.isEmpty()) uiState.expenses
                else {
                    val needle = query.lowercase()
                    val amountNeedle = query.replace(",", "").replace(" ", "")
                    uiState.expenses.filter { ewc ->
                        val e = ewc.expense
                        (e.recipient.lowercase().contains(needle)) ||
                        (e.recipientName?.lowercase()?.contains(needle) == true) ||
                        (ewc.categoryName?.lowercase()?.contains(needle) == true) ||
                        (e.notes?.lowercase()?.contains(needle) == true) ||
                        (amountNeedle.isNotEmpty() && e.amount.toLong().toString().contains(amountNeedle))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ExpenseSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    resultCount = if (query.isEmpty()) null else displayed.size
                )

                if (displayed.isEmpty()) {
                    NoSearchResults(query = query)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Month summary shows total for the current month
                        // regardless of the search query — the summary card
                        // is context, not a search result.
                        if (query.isEmpty()) {
                            item {
                                MonthSummaryCard(total = uiState.totalThisMonth)
                            }
                        }

                        // Group (filtered) expenses by date
                        val groupedExpenses = displayed.groupBy { ewc ->
                            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                                .format(Date(ewc.expense.timestamp))
                        }

                        groupedExpenses.forEach { (date, expenses) ->
                            item {
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            items(expenses) { ewc ->
                                ExpenseCard(
                                    expense = ewc.expense,
                                    categoryName = ewc.categoryName,
                                    categoryColor = ewc.categoryColor,
                                    onClick = {
                                        onNavigateToCategorize(ewc.expense.id)
                                    },
                                    onLongClick = {
                                        viewModel.toggleExcluded(
                                            ewc.expense.id,
                                            ewc.expense.isExcluded
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int?
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search recipient, category, amount…") },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        supportingText = if (resultCount != null) {
            { Text("$resultCount match${if (resultCount == 1) "" else "es"}") }
        } else null
    )
}

@Composable
private fun NoSearchResults(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No expenses match \u201C$query\u201D",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Try a recipient name, a category, or an amount.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MonthSummaryCard(
    total: Double
) {
    val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = currentMonth,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Total Expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Text(
                text = total.formatAsCurrency(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun EmptyExpenses(
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
            Icons.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No expenses yet",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Text(
            text = "Your expenses will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
