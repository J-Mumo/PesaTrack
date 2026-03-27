package com.pesatrack.presentation.screens.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.domain.models.BudgetPeriod
import com.pesatrack.domain.models.BudgetProgress
import com.pesatrack.domain.models.BudgetStatus
import com.pesatrack.utils.formatAsCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Budget"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Budget")
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Period Selector at the top
                item(key = "period_selector") {
                    PeriodSelector(
                        selectedPeriodType = uiState.selectedPeriodType,
                        periodLabel = uiState.selectedPeriodLabel,
                        onPeriodTypeChanged = { viewModel.setPeriodType(it) },
                        onNavigatePrevious = { viewModel.navigatePeriod(-1) },
                        onNavigateNext = { viewModel.navigatePeriod(1) }
                    )
                }

                // Income & Allocation Summary Card (always visible)
                item(key = "income_allocation") {
                    IncomeAllocationCard(
                        monthlyIncome = uiState.monthlyIncome,
                        totalBudgeted = uiState.totalBudgeted,
                        periodLabel = uiState.selectedPeriodLabel,
                        periodType = uiState.selectedPeriodType,
                        onSetIncome = { viewModel.showIncomeDialog() }
                    )
                }

                if (uiState.budgetProgressList.isEmpty()) {
                    // Empty state for the selected period (income card is still above)
                    item(key = "empty_state") {
                        EmptyBudgetContent(
                            onAddBudget = { viewModel.showAddDialog() }
                        )
                    }
                } else {
                    items(uiState.budgetProgressList) { progress ->
                        BudgetProgressCard(
                            progress = progress,
                            onEdit = { viewModel.showEditDialog(progress.budget) },
                            onDelete = { viewModel.showDeleteConfirmation(progress.budget) }
                        )
                    }
                }
            }
        }

        // Add/Edit Dialog
        if (uiState.showAddEditDialog) {
            AddEditBudgetDialog(
                uiState = uiState,
                onDismiss = { viewModel.dismissDialog() },
                onSave = { viewModel.saveBudget() },
                onCategoryChanged = { id, isGroup -> viewModel.updateDialogCategory(id, isGroup) },
                onAmountChanged = { viewModel.updateDialogAmount(it) }
            )
        }

        // Delete Confirmation Dialog
        if (uiState.showDeleteConfirmation && uiState.budgetToDelete != null) {
            val budget = uiState.budgetToDelete!!
            val name = budget.categoryName ?: "Budget"
            val levelLabel = if (budget.categoryId != null && budget.isGroupBudget) " (group)" else ""
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteConfirmation() },
                title = { Text("Delete Budget") },
                text = { Text("Remove the $name$levelLabel budget? This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.confirmDelete() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Income Dialog
        if (uiState.showIncomeDialog) {
            SetIncomeDialog(
                currentAmount = uiState.dialogIncomeAmount,
                periodLabel = uiState.selectedPeriodLabel,
                periodType = uiState.selectedPeriodType,
                onAmountChanged = { viewModel.updateIncomeAmount(it) },
                onSave = { viewModel.saveIncome() },
                onDismiss = { viewModel.dismissIncomeDialog() },
                error = uiState.error
            )
        }
    }
}

// ==================== Period Selector ====================

/**
 * Period selector composable: period type tabs (Weekly/Monthly/Yearly) + left/right arrows with label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelector(
    selectedPeriodType: BudgetPeriod,
    periodLabel: String,
    onPeriodTypeChanged: (BudgetPeriod) -> Unit,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit
) {
    val uiPeriods = BudgetPeriod.uiEntries

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Period type tabs (3 items: Weekly / Monthly / Yearly)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                uiPeriods.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = selectedPeriodType == period,
                        onClick = { onPeriodTypeChanged(period) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = uiPeriods.size
                        )
                    ) {
                        Text(
                            text = period.displayName(),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation row: ◀ Label ▶
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigatePrevious) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "Previous period"
                    )
                }

                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(onClick = onNavigateNext) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Next period"
                    )
                }
            }
        }
    }
}

// ==================== Income Allocation Card ====================

/**
 * Income & Budget allocation summary card.
 * Always visible — shows income vs total budgeted for the selected period,
 * with visual warning when over-allocated.
 */
@Composable
fun IncomeAllocationCard(
    monthlyIncome: Double?,
    totalBudgeted: Double,
    periodLabel: String,
    periodType: BudgetPeriod,
    onSetIncome: () -> Unit
) {
    val incomeLabel = when (periodType) {
        BudgetPeriod.WEEKLY -> "Weekly Income"
        BudgetPeriod.MONTHLY -> "Monthly Income"
        BudgetPeriod.YEARLY -> "Yearly Income"
        BudgetPeriod.CUSTOM -> "Period Income"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSetIncome() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 $periodLabel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Set Income",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (monthlyIncome == null) {
                // No income set — prompt
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tap to set your $incomeLabel so we can check if your budgets are realistic",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Income set — show allocation
                val allocationPct = if (monthlyIncome > 0) (totalBudgeted / monthlyIncome) * 100.0 else 0.0
                val isOverAllocated = totalBudgeted > monthlyIncome
                val remaining = monthlyIncome - totalBudgeted

                val barColor = when {
                    allocationPct >= 100.0 -> MaterialTheme.colorScheme.error
                    allocationPct >= 80.0 -> Color(0xFFFF9800) // Amber
                    else -> MaterialTheme.colorScheme.primary
                }

                // Income row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = incomeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = monthlyIncome.formatAsCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Budgeted row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total Budgeted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = totalBudgeted.formatAsCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { (allocationPct / 100.0).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isOverAllocated) {
                        Text(
                            text = "⚠️ Over by ${(-remaining).formatAsCurrency()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "✅ ${remaining.formatAsCurrency()} unallocated",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "${String.format("%.0f", allocationPct)}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                }
            }
        }
    }
}

// ==================== Income Dialog ====================

/**
 * Dialog to set income for the selected period.
 */
@Composable
fun SetIncomeDialog(
    currentAmount: String,
    periodLabel: String,
    periodType: BudgetPeriod,
    onAmountChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    error: String?
) {
    val title = when (periodType) {
        BudgetPeriod.WEEKLY -> "Set Weekly Income"
        BudgetPeriod.MONTHLY -> "Set Monthly Income"
        BudgetPeriod.YEARLY -> "Set Yearly Income"
        BudgetPeriod.CUSTOM -> "Set Period Income"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter your expected income for $periodLabel. This helps you see if your budgets are realistic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = currentAmount,
                    onValueChange = { value ->
                        val filtered = value.filter { it.isDigit() || it == ',' || it == '.' }
                        onAmountChanged(filtered)
                    },
                    label = { Text("Income (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("KES ") }
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==================== Empty State ====================

@Composable
fun EmptyBudgetContent(
    modifier: Modifier = Modifier,
    onAddBudget: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No budgets for this period",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set spending limits for categories or sub-categories to stay on track.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddBudget) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Budget")
        }
    }
}

// ==================== Budget Progress Card ====================

@Composable
fun BudgetProgressCard(
    progress: BudgetProgress,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val budget = progress.budget
    val categoryName = budget.categoryName ?: "Unknown"
    val periodLabel = budget.period.displayName()
    // Show "(group)" tag for group-level budgets to distinguish from sub-category budgets
    val levelTag = if (budget.categoryId != null && budget.isGroupBudget) " (group)" else ""

    val barColor = when (progress.status) {
        BudgetStatus.UNDER -> MaterialTheme.colorScheme.primary
        BudgetStatus.WARNING -> Color(0xFFFF9800)  // Amber
        BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.error
    }

    val categoryColor = budget.categoryColor?.let {
        try { Color(it.toColorInt()) } catch (_: Exception) { null }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Category color dot
                    if (categoryColor != null) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(categoryColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "$categoryName$levelTag",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { (progress.percentage / 100.0).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${progress.spent.formatAsCurrency()} / ${budget.amount.formatAsCurrency()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (progress.status == BudgetStatus.WARNING) {
                        Text(
                            text = "⚠️ ",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (progress.status == BudgetStatus.EXCEEDED) {
                        Text(
                            text = "🚨 ",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "${String.format("%.0f", progress.percentage)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                }
            }
        }
    }
}

// ==================== Add/Edit Budget Dialog ====================

/**
 * Add/Edit budget dialog — no period selector (period is inherited from the screen's selected period type).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBudgetDialog(
    uiState: BudgetUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onCategoryChanged: (Long?, Boolean) -> Unit,
    onAmountChanged: (String) -> Unit
) {
    val isEditing = uiState.editingBudget != null
    var showCategoryPicker by remember { mutableStateOf(false) }

    // Resolve display name for the selected category
    val selectedName = if (uiState.dialogCategoryId == null) {
        "Select category"
    } else {
        val option = uiState.availableCategories.find {
            it.id == uiState.dialogCategoryId &&
                (it.isGroup == true) == uiState.dialogIsGroupBudget
        }
        if (option != null) {
            if (option.isGroup == false) {
                val parentName = uiState.availableCategories
                    .find { it.id == option.parentGroupId && it.isGroup == true }
                    ?.name
                if (parentName != null) "$parentName > ${option.name}" else option.name
            } else {
                "${option.name} (group)"
            }
        } else {
            "Select category"
        }
    }

    // Period subtitle
    val periodSubtitle = "${uiState.selectedPeriodType.displayName()} • ${uiState.selectedPeriodLabel}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(if (isEditing) "Edit Budget" else "Add Budget")
                Text(
                    text = periodSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Category Picker — tap to open searchable dialog
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isEditing) { showCategoryPicker = true }
                ) {
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = {
                            if (!isEditing) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false, // Always disabled so Box handles click
                        colors = if (!isEditing) {
                            OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedTextFieldDefaults.colors()
                        }
                    )
                }

                // Amount Input
                OutlinedTextField(
                    value = uiState.dialogAmount,
                    onValueChange = { value ->
                        // Only allow digits and commas
                        val filtered = value.filter { it.isDigit() || it == ',' || it == '.' }
                        onAmountChanged(filtered)
                    },
                    label = { Text("Budget Amount (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("KES ") }
                )

                // Error message
                if (uiState.error != null) {
                    Text(
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(if (isEditing) "Update" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Searchable category picker dialog
    if (showCategoryPicker) {
        BudgetCategoryPickerDialog(
            availableCategories = uiState.availableCategories,
            selectedCategoryId = uiState.dialogCategoryId,
            selectedIsGroup = uiState.dialogIsGroupBudget,
            onCategorySelected = { id, isGroup ->
                onCategoryChanged(id, isGroup)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

// ==================== Category Picker Dialog ====================

/**
 * Searchable category picker dialog for budget creation.
 * Shows a search bar + hierarchical list: Groups (expandable) → Sub-categories.
 * Filters by search query, auto-expands matching groups, and hides categories that already have budgets.
 */
@Composable
fun BudgetCategoryPickerDialog(
    availableCategories: List<BudgetCategoryOption>,
    selectedCategoryId: Long?,
    selectedIsGroup: Boolean,
    onCategorySelected: (Long?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedGroups by remember { mutableStateOf(setOf<Long>()) }

    // Build hierarchical structure from flat list
    val groupOptions = availableCategories.filter { it.isGroup == true }
    val subOptions = availableCategories.filter { it.isGroup == false }

    // Filter based on search
    data class FilteredGroup(
        val group: BudgetCategoryOption,
        val children: List<BudgetCategoryOption>
    )

    val filteredGroups = remember(availableCategories, searchQuery) {
        groupOptions.mapNotNull { group ->
            val children = subOptions.filter { it.parentGroupId == group.id }
            if (searchQuery.isBlank()) {
                FilteredGroup(group, children)
            } else {
                val matchingChildren = children.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }
                val groupMatches = group.name.contains(searchQuery, ignoreCase = true)
                if (matchingChildren.isNotEmpty() || groupMatches) {
                    FilteredGroup(group, if (matchingChildren.isNotEmpty()) matchingChildren else children)
                } else null
            }
        }
    }

    // Auto-expand groups when searching
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            val groupsWithMatches = filteredGroups
                .filter { fg -> fg.children.any { it.name.contains(searchQuery, ignoreCase = true) } }
                .mapNotNull { it.group.id }
                .toSet()
            expandedGroups = expandedGroups + groupsWithMatches
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Category",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                // Search bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search categories...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Category list — groups + sub-categories
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    filteredGroups.forEach { (group, children) ->
                        // Group header
                        item(key = "group_${group.id}") {
                            val groupColor = group.color?.let {
                                try { Color(it.toColorInt()) } catch (_: Exception) { null }
                            } ?: MaterialTheme.colorScheme.primary
                            val isGroupSelected = selectedCategoryId == group.id && selectedIsGroup
                            val isExpanded = expandedGroups.contains(group.id)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        expandedGroups = if (isExpanded) {
                                            expandedGroups - group.id!!
                                        } else {
                                            expandedGroups + group.id!!
                                        }
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(groupColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${children.size} sub-categories",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // "Set group budget" chip — only if no existing group budget
                                    if (!group.hasExistingBudget) {
                                        TextButton(
                                            onClick = { onCategorySelected(group.id, true) },
                                            modifier = Modifier.padding(end = 4.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = if (isGroupSelected) "✓ Group" else "Group",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isGroupSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Sub-categories (expandable)
                        item(key = "children_${group.id}") {
                            AnimatedVisibility(
                                visible = expandedGroups.contains(group.id) || searchQuery.isNotBlank(),
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    children.forEach { sub ->
                                        if (!sub.hasExistingBudget || (sub.id == selectedCategoryId && !selectedIsGroup)) {
                                            val isSelected = sub.id == selectedCategoryId && !selectedIsGroup
                                            val subColor = sub.color?.let {
                                                try { Color(it.toColorInt()) } catch (_: Exception) { null }
                                            } ?: MaterialTheme.colorScheme.primary

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 24.dp, top = 1.dp, bottom = 1.dp, end = 4.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onCategorySelected(sub.id, false) },
                                                color = if (isSelected) {
                                                    subColor.copy(alpha = 0.15f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(subColor)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = sub.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isSelected) subColor else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Filled.Check,
                                                            contentDescription = "Selected",
                                                            tint = subColor,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
