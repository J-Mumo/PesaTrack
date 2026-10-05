package com.pesatrack.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesatrack.domain.insights.FirstFindings
import com.pesatrack.domain.insights.FirstFindingsGenerator
import com.pesatrack.presentation.screens.onboarding.StatementHowTo
import com.pesatrack.utils.formatAsCurrency

/**
 * First-session "aha" card (onboarding Paths A and B).
 *
 * Facts only, with a stated period. Fees (cat 606) get their own line.
 * Categorising is offered as an optional, secondary action, never required.
 */
@Composable
fun FindingsCard(
    findings: FirstFindings,
    uncategorizedCount: Int,
    onViewAnalytics: () -> Unit,
    onCategorize: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(
                title = "What your M-PESA shows \u00b7 ${findings.periodLabel}",
                onDismiss = onDismiss
            )
            Spacer(Modifier.height(8.dp))
            FindingsBody(findings)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewAnalytics) { Text("See breakdown") }
                if (uncategorizedCount > 0) {
                    TextButton(onClick = onCategorize) {
                        Text("Tidy up $uncategorizedCount (optional)")
                    }
                }
            }
        }
    }
}

/**
 * Path C — no SMS access and no import. Shows a clearly labelled example so
 * the user can see what PesaTrack produces, plus two ways to get real data.
 */
@Composable
fun ExampleFindingsCard(
    onImportStatement: () -> Unit,
    onAddExpense: () -> Unit,
    onDismiss: () -> Unit
) {
    val example = FirstFindingsGenerator.example()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(title = "Example \u2014 not your data", onDismiss = onDismiss)
            Spacer(Modifier.height(4.dp))
            AssistChip(onClick = {}, label = { Text("Example data") })
            Spacer(Modifier.height(8.dp))
            FindingsBody(example)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "See your own numbers by importing an M-PESA statement, " +
                    "or add today's spending by hand.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportStatement) { Text("Import statement") }
                TextButton(onClick = onAddExpense) { Text("Add an expense") }
            }
            Text(
                text = StatementHowTo.SHORT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One-time contextual SMS re-ask, shown only after a manual entry
 * (the moment the user feels the cost of typing). Dismissible; never repeats.
 */
@Composable
fun SmsReaskCard(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(title = "Want these added automatically?", onDismiss = onDismiss)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "With SMS access, PesaTrack adds each M-PESA payment for you. " +
                    "Only M-PESA and bank messages are read, and nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAllow) { Text("Allow SMS reading") }
                TextButton(onClick = onDismiss) { Text("No thanks") }
            }
        }
    }
}

/** Monthly reminder for statement-only users. Dismissed until next month. */
@Composable
fun StatementRefreshCard(
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(title = "Keep your numbers current", onDismiss = onDismiss)
            Text(
                text = "Without SMS access, PesaTrack only knows what you import. " +
                    "Import a newer statement to include recent spending.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImport) { Text("Import statement") }
        }
    }
}

@Composable
private fun FindingsBody(f: FirstFindings) {
    Text(
        text = "You spent ${f.totalSpent.formatAsCurrency()} across ${f.transactionCount} transactions.",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold
    )
    if (f.topCategoryName != null) {
        Text(
            text = "Largest category: ${f.topCategoryName} \u2014 " +
                "${f.topCategoryTotal.formatAsCurrency()} (${f.topCategorySharePct}% of spending).",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if (f.feesTotal > 0.0) {
        Text(
            text = "M-PESA fees: ${f.feesTotal.formatAsCurrency()}. " +
                "That amount could have gone to savings instead.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CardHeader(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
        }
    }
}
