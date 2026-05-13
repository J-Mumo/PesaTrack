package com.pesatrack.presentation.screens.weekly_review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.data.local.database.entities.ReportSnapshotEntity
import com.pesatrack.domain.insights.WeeklyReviewSnapshot
import com.pesatrack.utils.formatAsCurrency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Weekly Review screen (Insights & Reports v1.0).
 *
 * Renders one [WeeklyReviewSnapshot] as a vertical list of cards and a
 * "Previous reports" section. Copy follows the AGENTS.md UX rules: neutral,
 * factual framing; comparisons always paired with numbers; no shaming.
 *
 * Navigation:
 * - Notification deep link \u2192 hydrates the exact snapshot via [snapshotId].
 * - Settings entry / Insights tab \u2192 loads the most recent snapshot
 *   (generating one on the fly if none exists).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReviewScreen(
    snapshotId: Long?,
    onBack: () -> Unit,
    viewModel: WeeklyReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(snapshotId) {
        viewModel.load(snapshotId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(padding)
            uiState.snapshot == null -> EmptyState(
                message = uiState.errorMessage ?: "No weekly review available yet.",
                padding = padding
            )
            else -> WeeklyReviewContent(
                snapshot = uiState.snapshot!!,
                previousSnapshots = uiState.previousSnapshots,
                onPreviousClick = viewModel::viewSnapshot,
                padding = padding
            )
        }
    }
}

@Composable
private fun LoadingState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(message: String, padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeeklyReviewContent(
    snapshot: WeeklyReviewSnapshot,
    previousSnapshots: List<ReportSnapshotEntity>,
    onPreviousClick: (Long) -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PeriodHeader(snapshot) }
        item { PeriodTotalCard(snapshot) }

        if (snapshot.biggestChange != null) {
            item { BiggestChangeCard(snapshot) }
        }

        if (snapshot.topCategories.isNotEmpty()) {
            item {
                TopCategoriesCard(
                    snapshot = snapshot
                )
            }
        }

        if (snapshot.feesTotal > 0.0) {
            item { FeesCard(snapshot) }
        }

        if (snapshot.headroom != null) {
            item { HeadroomCard(snapshot) }
        }

        if (snapshot.limitedData) {
            item { LimitedDataNotice() }
        }

        if (previousSnapshots.isNotEmpty()) {
            item {
                Text(
                    text = "Previous reports",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(previousSnapshots, key = { it.id }) { entity ->
                PreviousReportRow(
                    entity = entity,
                    isCurrent = entity.id == 0L || entity.periodStart == snapshot.periodStart,
                    onClick = { onPreviousClick(entity.id) }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PeriodHeader(snapshot: WeeklyReviewSnapshot) {
    val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
    val start = fmt.format(Date(snapshot.periodStart))
    val end = fmt.format(Date(snapshot.periodEnd - 1)) // inclusive label
    Text(
        text = "$start \u2013 $end",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PeriodTotalCard(snapshot: WeeklyReviewSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Spent this week",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.periodTotal.formatAsCurrency(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            val pct = snapshot.periodDeltaPercent
            if (pct != null) {
                val arrow = if (pct >= 0.0) "\u2191" else "\u2193"
                val absPct = String.format(Locale.getDefault(), "%.0f", abs(pct))
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$arrow $absPct% vs last week (${snapshot.previousPeriodTotal.formatAsCurrency()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Average ${snapshot.averagePerDay.formatAsCurrency()} per day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BiggestChangeCard(snapshot: WeeklyReviewSnapshot) {
    val change = snapshot.biggestChange ?: return
    val arrow = if (change.deltaAmount >= 0.0) "\u2191" else "\u2193"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Biggest change",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${change.name} $arrow ${abs(change.deltaAmount).formatAsCurrency()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "vs. previous week",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TopCategoriesCard(snapshot: WeeklyReviewSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Where it went",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            snapshot.topCategories.forEach { share ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = share.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = share.amount.formatAsCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "  ${String.format(Locale.getDefault(), "%.0f", share.percentageOfPeriod)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (snapshot.othersCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${snapshot.othersCount} others: ${snapshot.othersAmount.formatAsCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeesCard(snapshot: WeeklyReviewSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Transaction fees paid",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.feesTotal.formatAsCurrency(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "Fees are tracked separately so your category totals stay honest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun HeadroomCard(snapshot: WeeklyReviewSnapshot) {
    val h = snapshot.headroom ?: return
    val remaining = (h.income - h.spendSoFar).coerceAtLeast(0.0)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${h.label} headroom",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = remaining.formatAsCurrency(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "left of ${h.income.formatAsCurrency()} income, with ${h.daysRemaining} days to go.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LimitedDataNotice() {
    Text(
        text = "Limited history \u2014 we'll show comparisons once you have a full prior week of data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun PreviousReportRow(
    entity: ReportSnapshotEntity,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
    val label = "${fmt.format(Date(entity.periodStart))} \u2013 ${fmt.format(Date(entity.periodEnd - 1))}"
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = if (isCurrent) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = entity.periodTotal.formatAsCurrency(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entity.viewedAt == null) {
                Text(
                    text = "New",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// EOF
