package com.pesatrack.presentation.screens.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            if (uiState.budgetProgressList.isNotEmpty()) {
                FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Budget")
                }
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
        } else if (uiState.budgetProgressList.isEmpty()) {
            EmptyBudgetContent(
                modifier = Modifier.padding(paddingValues),
                onAddBudget = { viewModel.showAddDialog() }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(uiState.budgetProgressList) { progress ->
                    BudgetProgressCard(
                        progress = progress,
                        onEdit = { viewModel.showEditDialog(progress.budget) },
                        onDelete = { viewModel.showDeleteConfirmation(progress.budget) }
                    )
                }
            }
        }

        // Add/Edit Dialog
        if (uiState.showAddEditDialog) {
            AddEditBudgetDialog(
                uiState = uiState,
                onDismiss = { viewModel.dismissDialog() },
                onSave = { viewModel.saveBudget() },
                onCategoryGroupChanged = { viewModel.updateDialogCategoryGroupId(it) },
                onAmountChanged = { viewModel.updateDialogAmount(it) },
                onPeriodChanged = { viewModel.updateDialogPeriod(it) }
            )
        }

        // Delete Confirmation Dialog
        if (uiState.showDeleteConfirmation && uiState.budgetToDelete != null) {
            val budget = uiState.budgetToDelete!!
            val name = budget.categoryGroupName ?: "Total Spending"
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteConfirmation() },
                title = { Text("Delete Budget") },
                text = { Text("Remove the $name budget? This cannot be undone.") },
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
    }
}

@Composable
fun EmptyBudgetContent(
    modifier: Modifier = Modifier,
    onAddBudget: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No budgets set",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set spending limits for your categories to stay on track.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddBudget) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Your First Budget")
        }
    }
}

@Composable
fun BudgetProgressCard(
    progress: BudgetProgress,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val budget = progress.budget
    val categoryName = budget.categoryGroupName ?: "Total Spending"
    val periodLabel = budget.period.displayName()

    val barColor = when (progress.status) {
        BudgetStatus.UNDER -> MaterialTheme.colorScheme.primary
        BudgetStatus.WARNING -> Color(0xFFFF9800)  // Amber
        BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.error
    }

    val categoryColor = budget.categoryGroupColor?.let {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = categoryName,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBudgetDialog(
    uiState: BudgetUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onCategoryGroupChanged: (Long?) -> Unit,
    onAmountChanged: (String) -> Unit,
    onPeriodChanged: (BudgetPeriod) -> Unit
) {
    val isEditing = uiState.editingBudget != null
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Budget" else "Add Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Category Group Picker
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!isEditing) expanded = it }
                ) {
                    val selectedName = if (uiState.dialogCategoryGroupId == null) {
                        "Total Spending"
                    } else {
                        uiState.availableGroups.find { it.id == uiState.dialogCategoryGroupId }?.name
                            ?: "Select category"
                    }

                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = {
                            if (!isEditing) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !isEditing
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.availableGroups
                            .filter { !it.hasExistingBudget || it.id == uiState.dialogCategoryGroupId }
                            .forEach { group ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (group.color != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(
                                                            try { Color(group.color.toColorInt()) }
                                                            catch (_: Exception) { Color.Gray }
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(group.name)
                                        }
                                    },
                                    onClick = {
                                        onCategoryGroupChanged(group.id)
                                        expanded = false
                                    }
                                )
                            }
                    }
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

                // Period Selector
                Column {
                    Text(
                        text = "Period",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        BudgetPeriod.entries.forEachIndexed { index, period ->
                            SegmentedButton(
                                selected = uiState.dialogPeriod == period,
                                onClick = { onPeriodChanged(period) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = BudgetPeriod.entries.size
                                )
                            ) {
                                Text(period.displayName())
                            }
                        }
                    }
                }

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
}
