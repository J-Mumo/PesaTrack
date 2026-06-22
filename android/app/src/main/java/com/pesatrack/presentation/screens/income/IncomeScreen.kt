package com.pesatrack.presentation.screens.income

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.IncomeSource
import com.pesatrack.domain.models.IncomeSourceTotal
import com.pesatrack.domain.models.IncomeTransaction
import com.pesatrack.utils.formatAsCurrency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncomeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCategorizeIncome: (Long) -> Unit,
    viewModel: IncomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Income") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showManualEntryDialog() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add income") },
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        IncomeContent(
            uiState = uiState,
            contentPadding = padding,
            onPeriodChange = viewModel::setPeriod,
            onIncomeClick = onNavigateToCategorizeIncome,
        )
    }

    if (uiState.showManualEntryDialog) {
        ManualIncomeEntryDialog(
            state = uiState,
            onAmount = viewModel::updateDialogAmount,
            onSender = viewModel::updateDialogSender,
            onSource = viewModel::updateDialogSource,
            onNote = viewModel::updateDialogNote,
            onDate = viewModel::updateDialogDate,
            onSave = viewModel::saveManualEntry,
            onDismiss = viewModel::dismissManualEntryDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IncomeContent(
    uiState: IncomeUiState,
    contentPadding: PaddingValues,
    onPeriodChange: (IncomePeriod) -> Unit,
    onIncomeClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header card: period switch + total + breakdown ──────────────
        item(key = "header") {
            IncomeHeaderCard(
                periodLabel = uiState.periodLabel,
                period = uiState.period,
                total = uiState.totalInflow,
                breakdown = uiState.breakdown,
                effectiveIncomeSource = uiState.effectiveIncomeSource,
                onPeriodChange = onPeriodChange,
            )
        }

        if (uiState.transactions.isEmpty()) {
            item("empty") {
                IncomeEmptyState()
            }
        } else {
            items(uiState.transactions, key = { it.id }) { tx ->
                IncomeRow(tx = tx, onClick = { onIncomeClick(tx.id) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomeHeaderCard(
    periodLabel: String,
    period: IncomePeriod,
    total: Double,
    breakdown: List<IncomeSourceTotal>,
    effectiveIncomeSource: EffectiveIncomeSource,
    onPeriodChange: (IncomePeriod) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            val periods = listOf(IncomePeriod.MONTH, IncomePeriod.QUARTER, IncomePeriod.YEAR)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                periods.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = p == period,
                        onClick = { onPeriodChange(p) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                    ) {
                        Text(
                            text = when (p) {
                                IncomePeriod.MONTH -> "Month"
                                IncomePeriod.QUARTER -> "Quarter"
                                IncomePeriod.YEAR -> "Year"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = periodLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = total.formatAsCurrency(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "received this ${periodLabelWord(period)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )

            // Reconciliation chip (only meaningful for monthly view).
            if (period == IncomePeriod.MONTH) {
                val chipText = when (effectiveIncomeSource) {
                    EffectiveIncomeSource.NONE -> null
                    EffectiveIncomeSource.MANUAL_OVERRIDE -> "Using your set income"
                    EffectiveIncomeSource.DETECTED -> "Detected from SMS"
                    EffectiveIncomeSource.DETECTED_BELOW_OVERRIDE -> "Using your set income — some income may not be detected"
                }
                if (chipText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = chipText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }

            if (breakdown.isNotEmpty() && total > 0.0) {
                Spacer(Modifier.height(12.dp))
                SourceBreakdownBar(breakdown = breakdown, total = total)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    breakdown.forEach { row ->
                        SourceLegendChip(row = row, total = total)
                    }
                }
            }
        }
    }
}

private fun periodLabelWord(period: IncomePeriod): String = when (period) {
    IncomePeriod.MONTH -> "month"
    IncomePeriod.QUARTER -> "quarter"
    IncomePeriod.YEAR -> "year"
}

@Composable
private fun SourceBreakdownBar(breakdown: List<IncomeSourceTotal>, total: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
    ) {
        breakdown.forEach { row ->
            val pct = (row.total / total).coerceIn(0.0, 1.0).toFloat()
            if (pct > 0f) {
                Box(
                    modifier = Modifier
                        .weight(pct)
                        .fillMaxSize()
                        .background(colorForSource(row.source)),
                )
            }
        }
    }
}

@Composable
private fun SourceLegendChip(row: IncomeSourceTotal, total: Double) {
    val pct = if (total > 0.0) (row.total / total) * 100.0 else 0.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colorForSource(row.source)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${row.source.displayName} ${"%.0f".format(pct)}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
    }
}

private fun colorForSource(source: IncomeSource): Color = when (source) {
    IncomeSource.SALARY -> Color(0xFF388E3C)        // green
    IncomeSource.BUSINESS -> Color(0xFF1976D2)      // blue
    IncomeSource.REFUND -> Color(0xFF8E24AA)        // purple
    IncomeSource.INTEREST -> Color(0xFF00897B)      // teal
    IncomeSource.FAMILY -> Color(0xFFD81B60)        // pink
    IncomeSource.TRANSFER_IN -> Color(0xFF757575)   // grey
    IncomeSource.OTHER -> Color(0xFFFB8C00)         // amber
    IncomeSource.UNCATEGORIZED -> Color(0xFFB0BEC5) // grey-blue
}

@Composable
private fun IncomeRow(tx: IncomeTransaction, onClick: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colorForSource(tx.source)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.amount.formatAsCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        tx.source.displayName,
                        tx.sender?.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Text(
                text = dateFmt.format(Date(tx.timestamp)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun IncomeEmptyState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No income detected yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "When you receive money via M-PESA or a supported bank, PesaTrack reads the SMS on your device and shows it here. Tap “Add income” to add one manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────
//                       Manual entry dialog
// ────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualIncomeEntryDialog(
    state: IncomeUiState,
    onAmount: (String) -> Unit,
    onSender: (String) -> Unit,
    onSource: (IncomeSource) -> Unit,
    onNote: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onDate: (Long) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add income") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.dialogAmount,
                    onValueChange = onAmount,
                    label = { Text("Amount (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.dialogSender,
                    onValueChange = onSender,
                    label = { Text("Source / sender (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IncomeSource.entries
                        .filter { it != IncomeSource.UNCATEGORIZED }
                        .forEach { src ->
                            FilterChip(
                                selected = src == state.dialogSource,
                                onClick = { onSource(src) },
                                label = { Text(src.displayName) },
                            )
                        }
                }
                OutlinedTextField(
                    value = state.dialogNote,
                    onValueChange = onNote,
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.dialogError != null) {
                    Text(
                        text = state.dialogError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
