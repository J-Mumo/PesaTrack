package com.pesatrack.presentation.screens.batch_categorize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.data.local.database.dao.RecipientGroup
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.presentation.components.GroupedCategoryPicker
import com.pesatrack.services.CategorySuggestion
import com.pesatrack.utils.formatAsCurrency
import java.text.SimpleDateFormat
import java.util.*

/**
 * Batch Categorize Screen
 *
 * Shows uncategorized expenses grouped by recipient.
 * Three modes per group:
 * - Quick mode: Tap "Categorize All" → apply category to entire group
 * - Review mode: Tap "Review" → expand to see individual transactions, each overridable
 * - Auto mode: Tap "Auto-Suggest" → rules engine suggests categories with confidence levels
 *
 * Multi-select mode:
 * - Long-press a group card to enter selection mode
 * - Select multiple groups → apply one category to all of them at once
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchCategorizeScreen(
    onNavigateBack: () -> Unit,
    viewModel: BatchCategorizeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Intercept back press in selection mode to exit selection instead of navigating back
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                // Selection mode top app bar
                SelectionModeTopBar(
                    selectedCount = uiState.selectedGroupKeys.size,
                    totalCount = uiState.recipientGroups.size,
                    allSelected = uiState.selectedGroupKeys.size == uiState.recipientGroups.size,
                    onClose = { viewModel.exitSelectionMode() },
                    onSelectAll = { viewModel.selectAllGroups() },
                    onDeselectAll = { viewModel.deselectAllGroups() }
                )
            } else {
                // Normal top app bar
                TopAppBar(
                    title = { Text("Categorize Expenses") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        },
        bottomBar = {
            // Bulk action bottom bar — shown when groups are selected
            if (uiState.isSelectionMode && uiState.selectedGroupKeys.isNotEmpty()) {
                BulkActionBottomBar(
                    selectedCount = uiState.selectedGroupKeys.size,
                    onClick = { viewModel.showBulkCategoryPicker() }
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.recipientGroups.isEmpty()) {
            // All categorized!
            AllCategorizedContent(
                categorizedCount = uiState.categorizedCount,
                individualCount = uiState.individualCategorizedCount,
                onDone = onNavigateBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Summary header
                item {
                    SummaryCard(
                        totalGroups = uiState.recipientGroups.size,
                        totalExpenses = uiState.recipientGroups.sumOf { it.transactionCount },
                        categorizedThisSession = uiState.categorizedCount,
                        individualCategorized = uiState.individualCategorizedCount
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Auto-Suggest section — hidden during selection mode
                if (!uiState.isSelectionMode) {
                    item {
                        AutoSuggestSection(
                            isLoading = uiState.isAutoSuggestLoading,
                            error = uiState.autoSuggestError,
                            hasSuggestions = uiState.autoSuggestions.isNotEmpty(),
                            onRequestSuggestions = { viewModel.requestAutoSuggestions() },
                            onApplyAll = { viewModel.applyAllAutoSuggestions() },
                            onDismissError = { viewModel.dismissAutoSuggestError() }
                        )
                    }
                }

                // Info text
                item {
                    Text(
                        text = if (uiState.isSelectionMode) {
                            "Tap groups to select/deselect. Use \"Categorize Selected\" to apply a category to all selected groups."
                        } else if (uiState.autoSuggestions.isNotEmpty()) {
                            "Suggestions are shown below. Tap a suggestion to apply it, or use \"Categorize All\" to pick a different category."
                        } else {
                            "Tap \"Categorize All\" to assign one category to all transactions from a recipient, or \"Review\" to categorize them individually. Long-press to multi-select."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Recipient groups
                items(
                    items = uiState.recipientGroups,
                    key = { it.recipientKey }
                ) { group ->
                    val isExpanded = uiState.expandedGroupKey == group.recipientKey
                    val suggestion = uiState.autoSuggestions[group.recipientKey]
                    val isSelected = group.recipientKey in uiState.selectedGroupKeys

                    RecipientGroupCard(
                        group = group,
                        isExpanded = isExpanded,
                        expandedExpenses = if (isExpanded) uiState.expandedGroupExpenses else emptyList(),
                        isLoadingExpanded = isExpanded && uiState.isLoadingExpanded,
                        suggestion = suggestion,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = isSelected,
                        onCategorizeAll = { viewModel.selectRecipientGroup(group) },
                        onToggleExpand = { viewModel.toggleExpandGroup(group) },
                        onCategorizeExpense = { expenseId ->
                            viewModel.selectExpenseForCategorize(expenseId)
                        },
                        onApplySuggestion = {
                            viewModel.applyAutoSuggestion(group.recipientKey)
                        },
                        onIgnoreGroup = { viewModel.ignoreRecipientGroup(group) },
                        onIgnoreExpense = { expenseId -> viewModel.ignoreExpense(expenseId) },
                        onLongPress = {
                            if (!uiState.isSelectionMode) {
                                viewModel.enterSelectionMode(group.recipientKey)
                            }
                        },
                        onSelectionTap = {
                            viewModel.toggleGroupSelection(group.recipientKey)
                        }
                    )
                }
            }
        }

        // Category picker dialog — Quick mode (apply to all)
        if (uiState.showCategoryPicker && uiState.selectedRecipientGroup != null) {
            GroupedCategoryPicker(
                categoryGroups = uiState.categoryGroups,
                selectedCategoryId = null,
                onCategorySelected = { category ->
                    viewModel.applyCategory(category)
                },
                onDismiss = { viewModel.dismissCategoryPicker() }
            )
        }

        // Category picker dialog — Individual expense override
        if (uiState.showIndividualCategoryPicker && uiState.selectedExpenseId != null) {
            GroupedCategoryPicker(
                categoryGroups = uiState.categoryGroups,
                selectedCategoryId = null,
                onCategorySelected = { category ->
                    viewModel.applyCategoryToExpense(uiState.selectedExpenseId!!, category)
                },
                onDismiss = { viewModel.dismissIndividualCategoryPicker() }
            )
        }

        // Category picker dialog — Bulk action on selected groups
        if (uiState.showBulkCategoryPicker) {
            GroupedCategoryPicker(
                categoryGroups = uiState.categoryGroups,
                selectedCategoryId = null,
                onCategorySelected = { category ->
                    viewModel.applyBulkCategory(category)
                },
                onDismiss = { viewModel.dismissBulkCategoryPicker() }
            )
        }

        // Saving overlay
        if (uiState.isSaving) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

// ==================== Selection Mode Top Bar ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionModeTopBar(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    TopAppBar(
        title = {
            Text("$selectedCount selected")
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            TextButton(
                onClick = if (allSelected) onDeselectAll else onSelectAll
            ) {
                Text(
                    text = if (allSelected) "Deselect All" else "Select All ($totalCount)"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

// ==================== Bulk Action Bottom Bar ====================

@Composable
private fun BulkActionBottomBar(
    selectedCount: Int,
    onClick: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    Icons.Filled.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Categorize Selected ($selectedCount)",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

// ==================== Auto-Suggest Section ====================

@Composable
private fun AutoSuggestSection(
    isLoading: Boolean,
    error: String?,
    hasSuggestions: Boolean,
    onRequestSuggestions: () -> Unit,
    onApplyAll: () -> Unit,
    onDismissError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "Auto-Suggest" button
            Button(
                onClick = onRequestSuggestions,
                enabled = !isLoading,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing...", color = MaterialTheme.colorScheme.onTertiary)
                } else {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasSuggestions) "Re-analyze" else "Auto-Suggest All")
                }
            }

            // "Apply All" button (only shown when suggestions exist)
            if (hasSuggestions) {
                Button(
                    onClick = onApplyAll,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Filled.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply All")
                }
            }
        }

        // Loading progress
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        // Error message
        if (error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissError,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== Summary & Group Cards ====================

@Composable
private fun SummaryCard(
    totalGroups: Int,
    totalExpenses: Int,
    categorizedThisSession: Int,
    individualCategorized: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Batch Categorize",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$totalGroups recipient${if (totalGroups != 1) "s" else ""} · $totalExpenses expense${if (totalExpenses != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            val totalCategorized = categorizedThisSession + individualCategorized
            if (totalCategorized > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val parts = mutableListOf<String>()
                if (categorizedThisSession > 0) {
                    parts.add("$categorizedThisSession group${if (categorizedThisSession != 1) "s" else ""}")
                }
                if (individualCategorized > 0) {
                    parts.add("$individualCategorized individual")
                }
                Text(
                    text = "✓ Categorized: ${parts.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipientGroupCard(
    group: RecipientGroup,
    isExpanded: Boolean,
    expandedExpenses: List<Expense>,
    isLoadingExpanded: Boolean,
    suggestion: CategorySuggestion?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onCategorizeAll: () -> Unit,
    onToggleExpand: () -> Unit,
    onCategorizeExpense: (Long) -> Unit,
    onApplySuggestion: () -> Unit,
    onIgnoreGroup: () -> Unit,
    onIgnoreExpense: (Long) -> Unit,
    onLongPress: () -> Unit,
    onSelectionTap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectionTap()
                    }
                    // In normal mode, clicks are handled by individual buttons
                },
                onLongClick = {
                    if (!isSelectionMode) {
                        onLongPress()
                    }
                }
            ),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox in selection mode, payment type icon otherwise
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectionTap() },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    // Payment type icon
                    Icon(
                        imageVector = getPaymentTypeIcon(group.paymentType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Recipient info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.recipientName ?: group.recipient,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row {
                        Text(
                            text = "${group.transactionCount} txn${if (group.transactionCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            text = group.totalAmount.formatAsCurrency(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Everything below the header is hidden in selection mode
            if (!isSelectionMode) {
                // Suggestion chip (when available)
                if (suggestion != null) {
                    SuggestionChip(
                        suggestion = suggestion,
                        onApply = onApplySuggestion,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }

                // Action buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "Categorize All" button — quick mode
                    OutlinedButton(
                        onClick = onCategorizeAll,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Categorize All", style = MaterialTheme.typography.labelMedium)
                    }

                    // "Review" button — expand to see individual transactions
                    OutlinedButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isExpanded) "Collapse" else "Review",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // "Ignore" button — exclude all expenses from this recipient
                    OutlinedButton(
                        onClick = onIgnoreGroup,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ignore", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Expanded individual expenses
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        if (isLoadingExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else if (expandedExpenses.isEmpty()) {
                            Text(
                                text = "All transactions categorized!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            Text(
                                text = "Tap a transaction to assign a category individually:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                            )
                            expandedExpenses.forEach { expense ->
                                IndividualExpenseRow(
                                    expense = expense,
                                    onClick = { onCategorizeExpense(expense.id) },
                                    onIgnore = { onIgnoreExpense(expense.id) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==================== Suggestion Chip ====================

/**
 * Displays a category suggestion with confidence indicator.
 * Color-coded: green (≥90%), amber (70-89%), red (<70%)
 */
@Composable
private fun SuggestionChip(
    suggestion: CategorySuggestion,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val confidencePercent = (suggestion.confidence * 100).toInt()
    val chipColor = when {
        suggestion.confidence >= 0.90f -> Color(0xFF4CAF50) // Green
        suggestion.confidence >= 0.70f -> Color(0xFFFF9800) // Amber
        else -> Color(0xFFF44336) // Red
    }
    val chipContainerColor = chipColor.copy(alpha = 0.12f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onApply() },
        shape = MaterialTheme.shapes.small,
        color = chipContainerColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = chipColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.categoryName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${suggestion.groupName} · $confidencePercent% confident",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            FilledTonalButton(
                onClick = onApply,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = chipColor.copy(alpha = 0.2f),
                    contentColor = chipColor
                )
            ) {
                Text("Apply", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ==================== Individual Expense Row ====================

@Composable
private fun IndividualExpenseRow(
    expense: Expense,
    onClick: () -> Unit,
    onIgnore: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(Date(expense.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Amount
        Text(
            text = expense.amount.formatAsCurrency(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 80.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Date
        Text(
            text = dateStr,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )

        // "Categorize" indicator
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Categorize",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // "Ignore" button
        IconButton(
            onClick = onIgnore,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = "Ignore",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ==================== All Categorized Content ====================

@Composable
private fun AllCategorizedContent(
    categorizedCount: Int,
    individualCount: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCategorized = categorizedCount + individualCount

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "All Caught Up!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (totalCategorized > 0) {
                buildString {
                    append("You categorized ")
                    if (categorizedCount > 0) {
                        append("$categorizedCount recipient group${if (categorizedCount != 1) "s" else ""}")
                    }
                    if (categorizedCount > 0 && individualCount > 0) {
                        append(" and ")
                    }
                    if (individualCount > 0) {
                        append("$individualCount individual expense${if (individualCount != 1) "s" else ""}")
                    }
                    append(". Future transactions from these recipients will be auto-categorized.")
                }
            } else {
                "All your expenses are categorized. Future transactions from known recipients will be auto-categorized."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
        ) {
            Text("Done")
        }
    }
}

/**
 * Get an icon for the payment type
 */
private fun getPaymentTypeIcon(paymentType: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (paymentType) {
        PaymentType.SEND_MONEY.name -> Icons.Filled.Send
        PaymentType.BUY_GOODS.name -> Icons.Filled.Store
        PaymentType.PAY_BILL.name -> Icons.Filled.Receipt
        PaymentType.WITHDRAW.name -> Icons.Filled.AccountBalance
        PaymentType.AIRTIME.name -> Icons.Filled.PhoneAndroid
        PaymentType.MPESA_CARD.name -> Icons.Filled.CreditCard
        PaymentType.TRANSACTION_COST.name -> Icons.Filled.PriceCheck
        else -> Icons.Filled.Payment
    }
}
