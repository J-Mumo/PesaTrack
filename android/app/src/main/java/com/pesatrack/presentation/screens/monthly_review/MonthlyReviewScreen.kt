package com.pesatrack.presentation.screens.monthly_review

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
import com.pesatrack.domain.insights.MonthlyReviewSnapshot
import com.pesatrack.utils.formatAsCurrency
import java.util.Locale
import kotlin.math.abs

/**
 * Monthly Review screen (Insights & Reports v1.1).
 *
 * Renders one [MonthlyReviewSnapshot] as a vertical list of cards following the
 * Monthly Review anatomy from plans/insights-and-reports-plan.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReviewScreen(
    snapshotId: Long?,
    onBack: () -> Unit,
    viewModel: MonthlyReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(snapshotId) {
        viewModel.load(snapshotId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Review") },
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
                    text = uiState.error ?: "No monthly review available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> MonthlyReviewContent(
                snapshot = uiState.snapshot!!,
                previousReports = uiState.previousReports,
                onPreviousClick = { id -> viewModel.viewSnapshot(id.toLong()) },
                padding = padding
            )
        }
    }
}

@Composable
private fun MonthlyReviewContent(
    snapshot: MonthlyReviewSnapshot,
    previousReports: List<MonthlyReviewSnapshot>,
    onPreviousClick: (String) -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Total
        item { MonthlyTotalCard(snapshot) }

        // Average per day
        item { AveragePerDayCard(snapshot) }

        // Top categories
        if (snapshot.topCategories.isNotEmpty()) {
            item { TopCategoriesCard(snapshot) }
        }

        // Biggest change category
        if (snapshot.biggestChangeCategory != null) {
            item { BiggestChangeCategoryCard(snapshot) }
        }

        // Fees paid
        if (snapshot.feesPaid > 0.0) {
            item { FeesCard(snapshot) }
        }

        // Headroom
        if (snapshot.headroom != null) {
            item { HeadroomCard(snapshot) }
        }

        // Pace / projection
        item { PaceCard(snapshot) }

        // Investment Illustration
        item { InvestmentIllustrationCard(snapshot) }

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
                PreviousMonthlyReportRow(
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
private fun MonthlyTotalCard(snapshot: MonthlyReviewSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = snapshot.monthName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.totalSpent.formatAsCurrency(),
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
                    text = "$arrow $absPct% vs previous month (${snapshot.previousMonthTotal.formatAsCurrency()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun AveragePerDayCard(snapshot: MonthlyReviewSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Average per day",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.averagePerDay.formatAsCurrency(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "across ${snapshot.daysInMonth} days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TopCategoriesCard(snapshot: MonthlyReviewSnapshot) {
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
private fun BiggestChangeCategoryCard(snapshot: MonthlyReviewSnapshot) {
    val change = snapshot.biggestChangeCategory ?: return
    val delta = change.currentAmount - change.previousAmount
    val arrow = if (delta >= 0.0) "\u2191" else "\u2193"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Biggest change",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${change.categoryName} $arrow ${abs(delta).formatAsCurrency()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "vs. previous month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeesCard(snapshot: MonthlyReviewSnapshot) {
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
                text = snapshot.feesPaid.formatAsCurrency(),
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
private fun HeadroomCard(snapshot: MonthlyReviewSnapshot) {
    val headroom = snapshot.headroom ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${snapshot.monthName} headroom",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = headroom.formatAsCurrency(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (snapshot.monthlyIncome != null) {
                Text(
                    text = "Income: ${snapshot.monthlyIncome.formatAsCurrency()} − Spent: ${snapshot.totalSpent.formatAsCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PaceCard(snapshot: MonthlyReviewSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Month-end projection",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.pace.formatAsCurrency(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Based on current daily average of ${snapshot.averagePerDay.formatAsCurrency()} × ${snapshot.daysInMonth} days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InvestmentIllustrationCard(snapshot: MonthlyReviewSnapshot) {
    val illust = snapshot.investmentIllustration
    if (illust.principalAmount <= 0.0) return

    val heading = when (illust.source) {
        com.pesatrack.domain.insights.InvestmentSource.ACTUAL_INVESTMENT -> {
            val pct = illust.currentPercent
            when {
                pct != null && pct >= 50.0 -> "Your investments are compounding"
                pct != null && pct >= 30.0 -> "Exceptional investing"
                pct != null && pct >= 20.0 -> "Strong investment discipline"
                pct != null && pct >= 10.0 -> "Building momentum"
                else -> "You've started investing"
            }
        }
        com.pesatrack.domain.insights.InvestmentSource.HEADROOM -> "Your unspent income this month"
        com.pesatrack.domain.insights.InvestmentSource.NUDGE_TARGET -> "A small redirect goes far"
    }

    val ratePct = (illust.annualRate * 100).toInt()
    val years = illust.horizonMonths / 12
    val body = when (illust.source) {
        com.pesatrack.domain.insights.InvestmentSource.ACTUAL_INVESTMENT -> {
            val base = "You invested ${illust.principalAmount.formatAsCurrency()} this month"
            val pctStr = illust.currentPercent?.let { " (${String.format("%.0f", it)}% of income)" } ?: ""
            val growth = ". If left to grow at $ratePct% p.a. for $years years it could become ${illust.futureValue.formatAsCurrency()}"
            val nextTarget = if (illust.nextTargetPercent != null && illust.gapAmount != null) {
                ". Next milestone: ${String.format("%.0f", illust.nextTargetPercent)}% — just ${illust.gapAmount.formatAsCurrency()} more."
            } else {
                ". Your money is growing faster than most."
            }
            "$base$pctStr$growth$nextTarget"
        }
        com.pesatrack.domain.insights.InvestmentSource.HEADROOM -> {
            "You had ${illust.principalAmount.formatAsCurrency()} left over this month. If invested at $ratePct% p.a. for $years years it could grow to ${illust.futureValue.formatAsCurrency()}."
        }
        com.pesatrack.domain.insights.InvestmentSource.NUDGE_TARGET -> {
            "Redirecting just ${illust.principalAmount.formatAsCurrency()} this month and leaving it to grow at $ratePct% p.a. for $years years could become ${illust.futureValue.formatAsCurrency()}."
        }
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
private fun PreviousMonthlyReportRow(
    report: MonthlyReviewSnapshot,
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
                Text(text = report.monthName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = report.totalSpent.formatAsCurrency(),
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
