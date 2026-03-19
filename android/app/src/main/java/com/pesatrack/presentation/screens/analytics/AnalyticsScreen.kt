package com.pesatrack.presentation.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.DailyTotal
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.database.dao.PaymentTypeTotal
import com.pesatrack.data.local.database.dao.TopSpender
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.presentation.theme.getCategoryColor
import com.pesatrack.utils.formatAsCurrency
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month Selector
        item {
            MonthSelectorRow(
                monthLabel = uiState.selectedMonthLabel,
                canGoNext = viewModel.canGoNext(),
                onPrevious = { viewModel.previousMonth() },
                onNext = { viewModel.nextMonth() }
            )
        }

        // Month-over-Month Comparison Card
        item {
            if (uiState.monthComparison != null) {
                MonthComparisonCard(comparison = uiState.monthComparison!!)
            }
        }

        // Summary Stats Row
        item {
            SummaryStatsRow(
                total = uiState.totalForMonth,
                avgDaily = uiState.avgDailySpend,
                transactionCount = uiState.transactionCountForMonth
            )
        }

        // Loading indicator
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Monthly Trend Line Chart
        if (uiState.monthlyTrend.isNotEmpty()) {
            item {
                SectionHeader(title = "Monthly Trend")
            }
            item {
                MonthlyTrendChart(data = uiState.monthlyTrend)
            }
        }

        // Daily Spending Column Chart
        if (uiState.dailySpending.isNotEmpty()) {
            item {
                SectionHeader(title = "Daily Spending")
            }
            item {
                DailySpendingChart(data = uiState.dailySpending)
            }
        }

        // Category Breakdown
        if (uiState.categoryBreakdown.isNotEmpty()) {
            item {
                SectionHeader(title = "By Category")
            }
            item {
                CategoryBreakdownChart(
                    data = uiState.categoryBreakdown,
                    totalForMonth = uiState.totalForMonth
                )
            }
        }

        // Top Spenders
        if (uiState.topSpenders.isNotEmpty()) {
            item {
                SectionHeader(title = "Top Recipients")
            }
            items(uiState.topSpenders) { spender ->
                TopSpenderRow(
                    spender = spender,
                    maxTotal = uiState.topSpenders.firstOrNull()?.total ?: 1.0
                )
            }
        }

        // Payment Type Breakdown
        if (uiState.paymentTypeBreakdown.isNotEmpty()) {
            item {
                SectionHeader(title = "By Payment Type")
            }
            item {
                PaymentTypeBreakdownChart(
                    data = uiState.paymentTypeBreakdown,
                    totalForMonth = uiState.totalForMonth
                )
            }
        }

        // Bottom spacer for navigation bar
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==================== Month Selector ====================

@Composable
fun MonthSelectorRow(
    monthLabel: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Previous month"
            )
        }
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = onNext,
            enabled = canGoNext
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Next month",
                tint = if (canGoNext)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

// ==================== Month-over-Month Comparison ====================

@Composable
fun MonthComparisonCard(comparison: MonthComparison) {
    val isIncrease = comparison.percentageChange > 0
    val changeColor = if (isIncrease) {
        MaterialTheme.colorScheme.error // red = spending more
    } else {
        Color(0xFF2E7D32) // green = spending less
    }
    val arrow = if (isIncrease) "↑" else "↓"
    val changeText = if (comparison.previousMonthTotal == 0.0 && comparison.currentMonthTotal == 0.0) {
        "No data"
    } else if (comparison.previousMonthTotal == 0.0) {
        "New spending"
    } else {
        "$arrow ${String.format("%.1f", comparison.percentageChange.absoluteValue)}%"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = comparison.currentMonthTotal.formatAsCurrency(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = changeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = changeColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "vs ${comparison.previousMonthLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            if (comparison.previousMonthTotal > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Previous: ${comparison.previousMonthTotal.formatAsCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ==================== Summary Stats ====================

@Composable
fun SummaryStatsRow(
    total: Double,
    avgDaily: Double,
    transactionCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Avg/Day",
            value = avgDaily.formatAsCurrency()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Transactions",
            value = transactionCount.toString()
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ==================== Section Header ====================

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

// ==================== Monthly Trend Line Chart ====================

@Composable
fun MonthlyTrendChart(data: List<MonthlyTotal>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val monthLabels = remember(data) {
        data.mapIndexed { index, mt ->
            index to mt.monthKey.takeLast(2).let { m ->
                when (m) {
                    "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"
                    "04" -> "Apr"; "05" -> "May"; "06" -> "Jun"
                    "07" -> "Jul"; "08" -> "Aug"; "09" -> "Sep"
                    "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
                    else -> m
                }
            }
        }.toMap()
    }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            lineSeries {
                series(data.map { it.total })
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = CartesianValueFormatter { _, value, _ ->
                            monthLabels[value.toInt()] ?: ""
                        }
                    ),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ==================== Daily Spending Column Chart ====================

@Composable
fun DailySpendingChart(data: List<DailyTotal>) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            columnSeries {
                series(data.map { it.total })
            }
        }
    }

    val dayLabels = remember(data) {
        data.mapIndexed { index, dt -> index to dt.dayOfMonth.toString() }.toMap()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(
                                fill = fill(MaterialTheme.colorScheme.primary),
                                thickness = 6.dp,
                            )
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = CartesianValueFormatter { _, value, _ ->
                            dayLabels[value.toInt()] ?: ""
                        }
                    ),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ==================== Category Breakdown ====================

@Composable
fun CategoryBreakdownChart(
    data: List<CategoryTotal>,
    totalForMonth: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            data.forEach { category ->
                val proportion = if (totalForMonth > 0) {
                    (category.total / totalForMonth).toFloat()
                } else 0f
                val barColor = category.categoryColor?.let {
                    try { getCategoryColor(it) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                } ?: MaterialTheme.colorScheme.primary

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category name
                    Text(
                        text = category.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(100.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = proportion.coerceIn(0.01f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Amount
                    Text(
                        text = category.total.formatAsCurrency(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
        }
    }
}

// ==================== Top Spenders ====================

@Composable
fun TopSpenderRow(
    spender: TopSpender,
    maxTotal: Double
) {
    val proportion = if (maxTotal > 0) (spender.total / maxTotal).toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spender.recipientKey,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = spender.total.formatAsCurrency(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = proportion.coerceIn(0.01f, 1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${spender.transactionCount} txns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ==================== Payment Type Breakdown ====================

@Composable
fun PaymentTypeBreakdownChart(
    data: List<PaymentTypeTotal>,
    totalForMonth: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            data.forEach { pt ->
                val proportion = if (totalForMonth > 0) {
                    (pt.total / totalForMonth).toFloat()
                } else 0f
                val displayName = try {
                    PaymentType.fromString(pt.paymentType).displayName()
                } catch (_: Exception) {
                    pt.paymentType
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(100.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = proportion.coerceIn(0.01f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pt.total.formatAsCurrency(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
        }
    }
}
