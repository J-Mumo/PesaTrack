package com.pesatrack.presentation.screens.quarterly_review

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
import com.pesatrack.domain.insights.QuarterlyReviewSnapshot
import com.pesatrack.utils.formatAsCurrency
import java.util.Locale
import kotlin.math.abs

/**
 * Quarterly Review screen (Insights & Reports v1.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarterlyReviewScreen(
    snapshotId: Long?,
    onBack: () -> Unit,
    viewModel: QuarterlyReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(snapshotId) {
        viewModel.load(snapshotId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quarterly Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.snapshot == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error ?: "No quarterly review available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> QuarterlyReviewContent(
                snapshot = uiState.snapshot!!,
                previousReports = uiState.previousReports,
                onPreviousClick = { id -> viewModel.viewSnapshot(id.toLong()) },
                padding = padding
            )
        }
    }
}

@Composable
private fun QuarterlyReviewContent(
    snapshot: QuarterlyReviewSnapshot,
    previousReports: List<QuarterlyReviewSnapshot>,
    onPreviousClick: (String) -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary card
        item { QuarterlyTotalCard(snapshot) }

        // Top categories
        if (snapshot.topCategories.isNotEmpty()) {
            item { QuarterlyTopCategoriesCard(snapshot) }
        }

        // Biggest mover
        if (snapshot.biggestMover != null) {
            item { BiggestMoverCard(snapshot) }
        }

        // Fees
        if (snapshot.totalFees > 0.0) {
            item { QuarterlyFeesCard(snapshot) }
        }

        // Savings momentum
        if (snapshot.savingsMomentum != null) {
            item { SavingsMomentumCard(snapshot) }
        }

        // Investment illustration
        if (snapshot.investmentIllustration != null) {
            item { QuarterlyInvestmentCard(snapshot) }
        }

        // Previous reports
        if (previousReports.isNotEmpty()) {
            item {
                Text(
                    text = "Previous reports",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(previousReports, key = { it.id }) { report ->
                PreviousQuarterlyReportRow(
                    report = report,
                    isCurrent = report.id == snapshot.id,
                    onClick = { onPreviousClick(report.id) }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun QuarterlyTotalCard(snapshot: QuarterlyReviewSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = snapshot.periodLabel,
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
            val pct = snapshot.deltaPercent
            if (pct != null) {
                val arrow = if (pct >= 0.0) "\u2191" else "\u2193"
                val absPct = String.format(Locale.getDefault(), "%.0f", abs(pct))
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$arrow $absPct% vs previous quarter (${snapshot.prevQuarterTotal.formatAsCurrency()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun QuarterlyTopCategoriesCard(snapshot: QuarterlyReviewSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Where it went",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            snapshot.topCategories.forEach { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cat.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = cat.amount.formatAsCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "  ${String.format(Locale.getDefault(), "%.0f", cat.percent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BiggestMoverCard(snapshot: QuarterlyReviewSnapshot) {
    val mover = snapshot.biggestMover ?: return
    val delta = mover.currentAmount - mover.previousAmount
    val arrow = if (delta >= 0.0) "\u2191" else "\u2193"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Biggest mover",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${mover.categoryName} $arrow ${abs(delta).formatAsCurrency()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "vs. previous quarter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuarterlyFeesCard(snapshot: QuarterlyReviewSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Transaction fees paid",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.totalFees.formatAsCurrency(),
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
private fun SavingsMomentumCard(snapshot: QuarterlyReviewSnapshot) {
    val momentum = snapshot.savingsMomentum ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Savings momentum",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            momentum.headroomPerMonth.forEach { month ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = month.monthLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = month.headroom.formatAsCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (month.headroom >= 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Monthly headroom = income − spending. Positive means you had room to save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuarterlyInvestmentCard(snapshot: QuarterlyReviewSnapshot) {
    val illust = snapshot.investmentIllustration ?: return
    if (illust.principalAmount <= 0.0) return

    val heading = when (illust.source) {
        com.pesatrack.domain.insights.InvestmentSource.ACTUAL_INVESTMENT -> {
            val pct = illust.currentPercent
            when {
                pct != null && pct >= 50.0 -> "Your investments are compounding"
                pct != null && pct >= 20.0 -> "Strong investment discipline"
                else -> "You've started investing"
            }
        }
        com.pesatrack.domain.insights.InvestmentSource.HEADROOM -> "What your savings could become"
        com.pesatrack.domain.insights.InvestmentSource.NUDGE_TARGET -> "A small redirect goes far"
    }

    val body = when (illust.source) {
        com.pesatrack.domain.insights.InvestmentSource.ACTUAL_INVESTMENT -> {
            val base = "You invested ${illust.principalAmount.formatAsCurrency()} this quarter"
            val pctStr = illust.currentPercent?.let { " (${String.format("%.0f", it)}% of income)" } ?: ""
            val growth = ". At ${(illust.annualRate * 100).toInt()}% p.a. for ${illust.horizonMonths / 12} years → ${illust.futureValue.formatAsCurrency()}"
            val next = if (illust.nextTargetPercent != null && illust.gapAmount != null) {
                ". Next milestone: ${String.format("%.0f", illust.nextTargetPercent)}%."
            } else ". Exceptional — keep compounding."
            "$base$pctStr$growth$next"
        }
        com.pesatrack.domain.insights.InvestmentSource.HEADROOM ->
            "You saved ${illust.principalAmount.formatAsCurrency()} this quarter. At ${(illust.annualRate * 100).toInt()}% p.a. for ${illust.horizonMonths / 12} years → ${illust.futureValue.formatAsCurrency()}."
        com.pesatrack.domain.insights.InvestmentSource.NUDGE_TARGET ->
            "20% of income invested consistently adds up. That's ${illust.principalAmount.formatAsCurrency()}/quarter. At ${(illust.annualRate * 100).toInt()}% p.a. for ${illust.horizonMonths / 12} years → ${illust.futureValue.formatAsCurrency()}."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = heading,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = illust.disclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PreviousQuarterlyReportRow(
    report: QuarterlyReviewSnapshot,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = if (isCurrent) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = report.periodLabel, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = report.periodTotal.formatAsCurrency(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val pct = report.deltaPercent
            if (pct != null) {
                val arrow = if (pct >= 0.0) "\u2191" else "\u2193"
                Text(
                    text = "$arrow ${String.format(Locale.getDefault(), "%.0f", abs(pct))}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
