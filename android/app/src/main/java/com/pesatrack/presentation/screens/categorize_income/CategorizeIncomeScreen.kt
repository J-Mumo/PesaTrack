package com.pesatrack.presentation.screens.categorize_income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.utils.formatAsCurrency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CategorizeIncomeScreen(
    incomeId: Long,
    onNavigateBack: () -> Unit,
    viewModel: CategorizeIncomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    // Confirmation gate for the (destructive) delete action — kept locally
    // because it's pure UI state and doesn't need to survive process death.
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isDeleting) showDeleteConfirm = false },
            title = { Text("Delete this income?") },
            text = {
                Text(
                    "This income row will be removed permanently and won't count " +
                        "toward income totals, savings rate, or analytics. " +
                        "This can't be undone.\n\n" +
                        "If you'd rather keep the record but hide it from totals, " +
                        "use \"Not income\" instead."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.delete() },
                    enabled = !uiState.isDeleting,
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    enabled = !uiState.isDeleting,
                ) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorize income") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    // Delete action for rows entered in error (typically manual
                    // entries). Distinct from the "Not income" chip below —
                    // that flag-hides; this removes the row entirely.
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = uiState.income != null && !uiState.isSaving && !uiState.isDeleting,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete income",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                TextButton(
                    onClick = { viewModel.save() },
                    enabled = !uiState.isSaving && !uiState.isDeleting && uiState.income != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Save", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.income == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(uiState.errorMessage ?: "Income not found")
            }

            else -> {
                val income = uiState.income!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Amount + sender summary card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = income.amount.formatAsCurrency(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "From ${income.sender ?: "Unknown sender"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                    .format(Date(income.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Text(
                        text = "Source",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IncomeSource.entries.forEach { source ->
                            FilterChip(
                                selected = uiState.selectedSource == source && !uiState.isExcluded,
                                onClick = { viewModel.selectSource(source) },
                                label = { Text(source.displayName) },
                                colors = FilterChipDefaults.filterChipColors(),
                                enabled = !uiState.isExcluded,
                            )
                        }
                        // Dedicated "Not income" chip — mirrors the isExcluded
                        // flag, so the user can mark a one-off transfer / refund
                        // / cash-back as something that should be filtered out
                        // of all income analytics.
                        FilterChip(
                            selected = uiState.isExcluded,
                            onClick = { viewModel.toggleExcluded() },
                            label = { Text("Not income") },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }

                    if (uiState.isExcluded) {
                        Text(
                            text = "This won't count toward income totals, savings rate, or analytics. You can undo with a long-press on the row.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
