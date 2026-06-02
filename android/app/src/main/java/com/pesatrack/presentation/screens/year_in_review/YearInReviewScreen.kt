package com.pesatrack.presentation.screens.year_in_review

import android.graphics.Bitmap
import android.view.View
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.domain.insights.YearInReviewSnapshot
import com.pesatrack.utils.formatAsCurrency
import java.util.Locale
import kotlin.math.abs

/**
 * Year-in-Review screen (Insights & Reports v1.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearInReviewScreen(
    year: Int?,
    onBack: () -> Unit,
    viewModel: YearInReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(year) {
        viewModel.load(year)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Year in Review") },
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
                    text = uiState.error ?: "No year-in-review available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> YearInReviewContent(
                snapshot = uiState.snapshot!!,
                previousReports = uiState.previousReports,
                isSharing = uiState.isSharing,
                onPreviousClick = { y -> viewModel.viewYear(y) },
                onShare = {
                    viewModel.shareReport(context) {
                        captureView(view)
                    }
                },
                padding = padding
            )
        }
    }
}

/**
 * Capture the current view as a Bitmap using the drawing cache.
 */
private fun captureView(view: View): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        view.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun YearInReviewContent(
    snapshot: YearInReviewSnapshot,
    previousReports: List<YearInReviewSnapshot>,
    isSharing: Boolean,
    onPreviousClick: (Int) -> Unit,
    onShare: () -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Annual summary
        item { AnnualTotalCard(snapshot) }

        // Top categories
        if (snapshot.topCategories.isNotEmpty()) {
            item { YearTopCategoriesCard(snapshot) }
        }

        // Biggest mover
        if (snapshot.biggestMover != null) {
            item { YearBiggestMoverCard(snapshot) }
        }

        // Fees
        if (snapshot.totalFees > 0.0) {
            item { YearFeesCard(snapshot) }
        }

        // Quiet leaks
        if (snapshot.quietLeaks.isNotEmpty()) {
            item { QuietLeaksCard(snapshot) }
        }

        // Savings story
        if (snapshot.savingsStory != null) {
            item { SavingsStoryCard(snapshot) }
        }

        // Investment illustration
        if (snapshot.investmentIllustration != null) {
            item { YearInvestmentCard(snapshot) }
        }

        // Goals progress
        if (!snapshot.goalsProgress.isNullOrEmpty()) {
            item { GoalsProgressCard(snapshot) }
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
            items(previousReports, key = { it.year }) { report ->
                PreviousYearReportRow(
                    report = report,
                    isCurrent = report.year == snapshot.year,
                    onClick = { onPreviousClick(report.year) }
                )
            }
        }

        // Share button
        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSharing
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.padding(start = 8.dp))
                Text(if (isSharing) "Sharing…" else "Share as image")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnnualTotalCard(snapshot: YearInReviewSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${snapshot.year} Year in Review",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.annualTotal.formatAsCurrency(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (snapshot.prevYearTotal > 0.0) {
                val pct = ((snapshot.annualTotal - snapshot.prevYearTotal) / snapshot.prevYearTotal) * 100.0
                val arrow = if (pct >= 0.0) "\u2191" else "\u2193"
                val absPct = String.format(Locale.getDefault(), "%.0f", abs(pct))
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$arrow $absPct% vs ${snapshot.year - 1} (${snapshot.prevYearTotal.formatAsCurrency()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun YearTopCategoriesCard(snapshot: YearInReviewSnapshot) {
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
private fun YearBiggestMoverCard(snapshot: YearInReviewSnapshot) {
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
                text = "vs. previous year",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun YearFeesCard(snapshot: YearInReviewSnapshot) {
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
                text = "Monthly average: ${snapshot.monthlyAvgFees.formatAsCurrency()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun QuietLeaksCard(snapshot: YearInReviewSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quiet leaks",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "High-frequency, low-average categories that add up over a year.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            snapshot.quietLeaks.forEach { leak ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = leak.categoryName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${leak.totalTransactions} transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = leak.totalAmount.formatAsCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingsStoryCard(snapshot: YearInReviewSnapshot) {
    val story = snapshot.savingsStory ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Savings story",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You had headroom (income > expenses) in ${story.monthsInHeadroom} of 12 months.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Best month: ${story.bestMonth} (${story.bestMonthHeadroom.formatAsCurrency()} headroom)",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Total headroom across the year: ${story.totalHeadroom.formatAsCurrency()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun YearInvestmentCard(snapshot: YearInReviewSnapshot) {
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
        com.pesatrack.domain.insights.InvestmentSource.HEADROOM -> "Your unspent income this year"
        com.pesatrack.domain.insights.InvestmentSource.NUDGE_TARGET -> "A small redirect goes far"
    }

    val ratePct = (illust.annualRate * 100).toInt()
    val years = illust.horizonMonths / 12
    val body = when (illust.source) {
        com.pesatrack.domain.insights.InvestmentSource.ACTUAL_INVESTMENT -> {
            val base = "You invested ${illust.principalAmount.formatAsCurrency()} this year"
            val pctStr = illust.currentPercent?.let { " (${String.format("%.0f", it)}% of income)" } ?: ""
            val growth = ". If left to grow at $ratePct% p.a. for $years years it could become ${illust.futureValue.formatAsCurrency()}"
            val next = if (illust.nextTargetPercent != null) {
                ". Next milestone: ${String.format("%.0f", illust.nextTargetPercent)}%."
            } else ". Your money is growing faster than most."
            "$base$pctStr$growth$next"
        }
        com.pesatrack.domain.insights.InvestmentSource.HEADROOM ->
            "You had ${illust.principalAmount.formatAsCurrency()} left over this year. If invested at $ratePct% p.a. for $years years it could grow to ${illust.futureValue.formatAsCurrency()}."
        com.pesatrack.domain.insights.InvestmentSource.NUDGE_TARGET ->
            "Redirecting just ${illust.principalAmount.formatAsCurrency()} this year and leaving it to grow at $ratePct% p.a. for $years years could become ${illust.futureValue.formatAsCurrency()}."
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
private fun GoalsProgressCard(snapshot: YearInReviewSnapshot) {
    val goals = snapshot.goalsProgress ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Goals progress",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            goals.forEach { goal ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = goal.goalName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.0f", goal.percentage)}% achieved",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${goal.achieved.formatAsCurrency()} / ${goal.target.formatAsCurrency()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviousYearReportRow(
    report: YearInReviewSnapshot,
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
                Text(text = "${report.year}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = report.annualTotal.formatAsCurrency(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (report.prevYearTotal > 0.0) {
                val pct = ((report.annualTotal - report.prevYearTotal) / report.prevYearTotal) * 100.0
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
