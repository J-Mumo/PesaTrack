package com.pesatrack.presentation.screens.merchants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.data.repository.ExpenseRepository.MerchantGroupSummary
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.presentation.components.ExpenseCard
import com.pesatrack.presentation.components.GroupedCategoryPicker
import com.pesatrack.presentation.theme.getCategoryColor
import com.pesatrack.utils.formatAsCurrency

/**
 * Merchants (re-categorization) screen.
 *
 * Lists every distinct merchant + account seen in the DB with its current
 * dominant category, count, and total spent. Tapping a row opens a detail
 * sheet from which the user can reassign every historical transaction (and
 * the auto-cat mapping) to a different category. Designed to fix the
 * aggregator-paybill case where SMS-parsing auto-tagged many different
 * merchants under one category because they share a paybill number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MerchantsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Flat lookup: categoryId → Category, used to resolve dominant category
    // names and colors for each row without a second DB call per row.
    val categoriesById: Map<Long, Category> = remember(uiState.categoryGroups) {
        buildMap {
            uiState.categoryGroups.forEach { group ->
                put(group.parent.id, group.parent)
                group.children.forEach { put(it.id, it) }
            }
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merchants") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.merchants.isEmpty() -> {
                    EmptyMerchants()
                }
                else -> {
                    val query = uiState.searchQuery.trim().lowercase()
                    val filtered = remember(uiState.merchants, query, categoriesById) {
                        if (query.isEmpty()) uiState.merchants
                        else uiState.merchants.filter { m ->
                            val dominantName = m.dominantCategoryId
                                ?.let { categoriesById[it]?.name?.lowercase() }
                                .orEmpty()
                            (m.recipientName?.lowercase()?.contains(query) == true) ||
                                m.recipient.lowercase().contains(query) ||
                                dominantName.contains(query)
                        }
                    }

                    Column(Modifier.fillMaxSize()) {
                        MerchantsSearchField(
                            query = uiState.searchQuery,
                            onQueryChange = viewModel::setSearchQuery,
                            resultCount = if (query.isEmpty()) null else filtered.size
                        )

                        // Explainer — Awareness before action.
                        Text(
                            text = "Each row is one merchant (paybills are split by account). Tap to reassign every transaction to a different category.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        if (filtered.isEmpty()) {
                            NoMerchantsMatch(query = uiState.searchQuery.trim())
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered, key = { it.groupKey }) { m ->
                                    MerchantRow(
                                        merchant = m,
                                        dominantCategory = m.dominantCategoryId
                                            ?.let(categoriesById::get),
                                        onClick = { viewModel.openMerchant(m) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            uiState.error?.let { err ->
                LaunchedEffect(err) { snackbarHostState.showSnackbar(err) }
            }
        }

        // Detail sheet + category picker.
        val selected = uiState.selectedGroup
        if (selected != null) {
            MerchantDetailSheet(
                group = selected,
                dominantCategory = selected.dominantCategoryId?.let(categoriesById::get),
                expenses = uiState.selectedGroupExpenses,
                isLoading = uiState.isLoadingSelected,
                onReassign = { viewModel.requestCategoryPicker() },
                onDismiss = { viewModel.closeMerchant() }
            )
        }

        if (uiState.showCategoryPicker && selected != null) {
            GroupedCategoryPicker(
                categoryGroups = uiState.categoryGroups,
                selectedCategoryId = selected.dominantCategoryId,
                onCategorySelected = { category ->
                    viewModel.reassign(selected, category.id, category.name)
                },
                onDismiss = { viewModel.dismissCategoryPicker() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantsSearchField(
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
        placeholder = { Text("Search merchant, account, category…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
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
private fun MerchantRow(
    merchant: MerchantGroupSummary,
    dominantCategory: Category?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category swatch — colour = current dominant category.
            val swatch = dominantCategory?.color?.let(::getCategoryColor)
                ?: MaterialTheme.colorScheme.outline
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(swatch.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = paymentIcon(merchant.paymentType),
                    contentDescription = null,
                    tint = swatch
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = merchantDisplayName(merchant),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = merchantSubtitle(merchant, dominantCategory),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (merchant.hasMixedCategories) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Mixed categories — reassign to unify",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = merchant.totalAmount.formatAsCurrency(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${merchant.transactionCount} txn${if (merchant.transactionCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantDetailSheet(
    group: MerchantGroupSummary,
    dominantCategory: Category?,
    expenses: List<com.pesatrack.domain.models.Expense>,
    isLoading: Boolean,
    onReassign: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = merchantDisplayName(group),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = merchantSubtitle(group, dominantCategory),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onReassign,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reassign all ${group.transactionCount} to another category")
            }
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Transactions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(expenses, key = { it.id }) { e ->
                        ExpenseCard(
                            expense = e,
                            categoryName = null,
                            categoryColor = null,
                            onClick = { /* read-only preview */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMerchants() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Storefront,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No merchants yet",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Once you have expenses, merchants show up here so you can reassign them by account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoMerchantsMatch(query: String) {
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
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No merchants match \u201C$query\u201D",
            style = MaterialTheme.typography.titleSmall
        )
    }
}

// ==================== Copy helpers ====================

private fun merchantDisplayName(m: MerchantGroupSummary): String {
    val name = m.recipientName?.takeIf { it.isNotBlank() }
    val account = m.recipient.takeIf { it.isNotBlank() }
    return when {
        m.paymentType == PaymentType.PAY_BILL && name != null && account != null ->
            "$name · $account"
        name != null -> name
        account != null -> account
        else -> "Unknown"
    }
}

private fun merchantSubtitle(m: MerchantGroupSummary, dominantCategory: Category?): String {
    val paymentLabel = when (m.paymentType) {
        PaymentType.PAY_BILL -> "Paybill"
        PaymentType.BUY_GOODS -> "Till"
        PaymentType.SEND_MONEY -> "Send"
        PaymentType.WITHDRAW -> "Withdraw"
        PaymentType.AIRTIME -> "Airtime"
        PaymentType.MPESA_CARD -> "M-PESA Card"
        PaymentType.TRANSACTION_COST -> "Transaction cost"
        PaymentType.BANK_DEBIT -> "Bank debit"
        PaymentType.CARD_PAYMENT -> "Card"
        PaymentType.CASH -> "Cash"
    }
    val catLabel = dominantCategory?.name ?: "Uncategorized"
    return "$paymentLabel • $catLabel"
}

private fun paymentIcon(type: PaymentType) = when (type) {
    PaymentType.PAY_BILL -> Icons.Filled.ReceiptLong
    PaymentType.BUY_GOODS -> Icons.Filled.Store
    PaymentType.SEND_MONEY -> Icons.Filled.Send
    PaymentType.WITHDRAW -> Icons.Filled.LocalAtm
    PaymentType.AIRTIME -> Icons.Filled.PhoneAndroid
    PaymentType.MPESA_CARD -> Icons.Filled.CreditCard
    PaymentType.TRANSACTION_COST -> Icons.Filled.Percent
    PaymentType.BANK_DEBIT -> Icons.Filled.AccountBalance
    PaymentType.CARD_PAYMENT -> Icons.Filled.CreditCard
    PaymentType.CASH -> Icons.Filled.Payments
}
