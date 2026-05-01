package com.pesatrack.presentation.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.pesatrack.data.local.database.dao.YearMonthTotal
import com.pesatrack.domain.models.CategoryTrend
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.domain.models.YearComparison
import com.pesatrack.presentation.theme.getCategoryColor
import com.pesatrack.utils.formatAsCurrency
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row: Monthly / Yearly
        TabRow(
            selectedTabIndex = if (uiState.selectedTab == AnalyticsTab.MONTHLY) 0 else 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Tab(
                selected = uiState.selectedTab == AnalyticsTab.MONTHLY,
                onClick = { viewModel.selectTab(AnalyticsTab.MONTHLY) },
                text = { Text("Monthly") }
            )
            Tab(
                selected = uiState.selectedTab == AnalyticsTab.YEARLY,
                onClick = { viewModel.selectTab(AnalyticsTab.YEARLY) },
                text = { Text("Yearly") }
            )
        }

        // Tab content
        when (uiState.selectedTab) {
            AnalyticsTab.MONTHLY -> MonthlyTabContent(
                uiState = uiState,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() },
                canGoNext = viewModel.canGoNext(),
                onNavigateToBudget = onNavigateToBudget,
                onSearchRecipient = { viewModel.searchRecipient(it) },
                onClearSearch = { viewModel.clearRecipientSearch() }
            )
            AnalyticsTab.YEARLY -> YearlyTabContent(
                uiState = uiState,
                onPreviousYear = { viewModel.previousYear() },
                onNextYear = { viewModel.nextYear() },
                canGoNextYear = viewModel.canGoNextYear(),
                onSearchRecipient = { viewModel.searchRecipient(it) },
                onClearSearch = { viewModel.clearRecipientSearch() }
            )
        }
    }
}

// ==================== Monthly Tab (existing content) ====================

@Composable
fun MonthlyTabContent(
    uiState: AnalyticsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
    onNavigateToBudget: () -> Unit = {},
    onSearchRecipient: (String) -> Unit = {},
    onClearSearch: () -> Unit = {}
) {
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
                canGoNext = canGoNext,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        // Month-over-Month Comparison Card
        item {
            if (uiState.monthComparison != null) {
                MonthComparisonCard(comparison = uiState.monthComparison!!)
            }
        }

        // Weekly Snapshot Card (rolling 7 days vs previous 7 days)
        if (uiState.weeklyTotal > 0 || uiState.previousWeekTotal > 0) {
            item {
                WeeklySnapshotCard(
                    weekDateLabel = uiState.weekDateLabel,
                    weeklyTotal = uiState.weeklyTotal,
                    previousWeekTotal = uiState.previousWeekTotal,
                    weekOverWeekChange = uiState.weekOverWeekChange,
                    topCategoryName = uiState.topCategoryThisWeek,
                    topCategoryAmount = uiState.topCategoryThisWeekAmount
                )
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

        // Budget Banner — shown when no budgets are set up yet
        if (!uiState.hasActiveBudgets) {
            item {
                BudgetSetupBanner(onSetUpBudgets = onNavigateToBudget)
            }
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

        // Variable-Spend Category Trends
        if (uiState.categoryTrends.isNotEmpty()) {
            item {
                SectionHeader(title = "Spending Trends")
            }
            item {
                Text(
                    text = "Categories with variable monthly spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            items(uiState.categoryTrends) { trend ->
                CategoryTrendCard(trend = trend)
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

        // Forecast Projection Chart (current month only, when projection data exists)
        if (uiState.projectionLine.isNotEmpty()) {
            item {
                SectionHeader(title = "Spending Projection")
            }
            item {
                ForecastProjectionChart(
                    dailySpending = uiState.dailySpending,
                    projectionLine = uiState.projectionLine,
                    budgetCeiling = uiState.budgetCeiling
                )
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

        // Top Spenders with Recipient Search
        if (uiState.topSpenders.isNotEmpty() || uiState.recipientSearchResults != null) {
            item {
                RecipientSearchHeader(
                    searchQuery = uiState.recipientSearchQuery,
                    onSearchQueryChange = onSearchRecipient,
                    onClearSearch = onClearSearch,
                    isLoading = uiState.recipientSearchLoading
                )
            }

            // Show search results or default top-10
            val displayList = uiState.recipientSearchResults ?: uiState.topSpenders
            val maxTotal = displayList.firstOrNull()?.total ?: 1.0

            if (uiState.recipientSearchResults != null && displayList.isEmpty()) {
                item {
                    Text(
                        text = "No recipients found for \"${uiState.recipientSearchQuery}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(displayList) { spender ->
                    TopSpenderRow(
                        spender = spender,
                        maxTotal = maxTotal
                    )
                }
            }

            // Aggregate total row when searching with results
            if (uiState.recipientSearchResults != null && uiState.recipientSearchResults!!.size > 1) {
                item {
                    RecipientSearchTotalRow(
                        query = uiState.recipientSearchQuery,
                        total = uiState.recipientSearchTotal,
                        transactionCount = uiState.recipientSearchResults!!.sumOf { it.transactionCount }
                    )
                }
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

        // Recurring vs One-time Spending Breakdown
        if (uiState.hasRecurringData && uiState.totalForMonth > 0) {
            item {
                SectionHeader(title = "Spending Breakdown")
            }
            item {
                RecurringBreakdownCard(
                    recurringTotal = uiState.recurringTotal,
                    oneTimeTotal = uiState.oneTimeTotal,
                    totalForMonth = uiState.totalForMonth,
                    topRecurringNames = uiState.topRecurringNames
                )
            }
        }

        // Bottom spacer for navigation bar
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==================== Yearly Tab (new content) ====================

@Composable
fun YearlyTabContent(
    uiState: AnalyticsUiState,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    canGoNextYear: Boolean,
    onSearchRecipient: (String) -> Unit = {},
    onClearSearch: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Year Selector
        item {
            YearSelectorRow(
                yearLabel = uiState.selectedYearForYearly.toString(),
                canGoNext = canGoNextYear,
                onPrevious = onPreviousYear,
                onNext = onNextYear
            )
        }

        // Loading indicator
        if (uiState.yearlyIsLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Year-over-Year Comparison Card
        if (uiState.yearComparison != null) {
            item {
                YearComparisonCard(comparison = uiState.yearComparison!!)
            }
        }

        // Yearly Summary Stats Row
        if (!uiState.yearlyIsLoading) {
            item {
                YearlySummaryStatsRow(
                    avgMonthly = uiState.yearlyAvgMonthlySpend,
                    transactionCount = uiState.yearlyTransactionCount
                )
            }
        }

        // 12-Month Overlay Chart (this year vs last year)
        if (uiState.currentYearMonthlyTotals.isNotEmpty()) {
            item {
                SectionHeader(title = "Monthly Comparison")
            }
            item {
                YearlyOverlayChart(
                    currentYearData = uiState.currentYearMonthlyTotals,
                    previousYearData = uiState.previousYearMonthlyTotals,
                    currentYearLabel = uiState.selectedYearForYearly.toString(),
                    previousYearLabel = (uiState.selectedYearForYearly - 1).toString()
                )
            }
        }

        // Category Breakdown for Year
        if (uiState.yearlyCategoryBreakdown.isNotEmpty()) {
            item {
                SectionHeader(title = "By Category")
            }
            item {
                CategoryBreakdownChart(
                    data = uiState.yearlyCategoryBreakdown,
                    totalForMonth = uiState.yearlyTotalForYear
                )
            }
        }

        // Top Spenders for Year with Recipient Search
        if (uiState.yearlyTopSpenders.isNotEmpty() || uiState.yearlyRecipientSearchResults != null) {
            item {
                RecipientSearchHeader(
                    searchQuery = uiState.recipientSearchQuery,
                    onSearchQueryChange = onSearchRecipient,
                    onClearSearch = onClearSearch,
                    isLoading = uiState.recipientSearchLoading
                )
            }

            // Show search results or default top-10
            val displayList = uiState.yearlyRecipientSearchResults ?: uiState.yearlyTopSpenders
            val maxTotal = displayList.firstOrNull()?.total ?: 1.0

            if (uiState.yearlyRecipientSearchResults != null && displayList.isEmpty()) {
                item {
                    Text(
                        text = "No recipients found for \"${uiState.recipientSearchQuery}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(displayList) { spender ->
                    TopSpenderRow(
                        spender = spender,
                        maxTotal = maxTotal
                    )
                }
            }

            // Aggregate total row when searching with results
            if (uiState.yearlyRecipientSearchResults != null && uiState.yearlyRecipientSearchResults!!.size > 1) {
                item {
                    RecipientSearchTotalRow(
                        query = uiState.recipientSearchQuery,
                        total = uiState.yearlyRecipientSearchTotal,
                        transactionCount = uiState.yearlyRecipientSearchResults!!.sumOf { it.transactionCount }
                    )
                }
            }
        }

        // Payment Type Breakdown for Year
        if (uiState.yearlyPaymentTypeBreakdown.isNotEmpty()) {
            item {
                SectionHeader(title = "By Payment Type")
            }
            item {
                PaymentTypeBreakdownChart(
                    data = uiState.yearlyPaymentTypeBreakdown,
                    totalForMonth = uiState.yearlyTotalForYear
                )
            }
        }

        // Bottom spacer for navigation bar
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==================== Year Selector ====================

@Composable
fun YearSelectorRow(
    yearLabel: String,
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
                contentDescription = "Previous year"
            )
        }
        Text(
            text = yearLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = onNext,
            enabled = canGoNext
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Next year",
                tint = if (canGoNext)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

// ==================== Year-over-Year Comparison Card ====================

@Composable
fun YearComparisonCard(comparison: YearComparison) {
    val isIncrease = comparison.percentageChange > 0
    val changeColor = if (isIncrease) {
        MaterialTheme.colorScheme.error // red = spending more
    } else {
        Color(0xFF2E7D32) // green = spending less
    }
    val arrow = if (isIncrease) "↑" else "↓"
    val changeText = if (comparison.previousYearTotal == 0.0 && comparison.currentYearTotal == 0.0) {
        "No data"
    } else if (comparison.previousYearTotal == 0.0) {
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
                text = "Annual Total",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comparison.currentYearTotal.formatAsCurrency(),
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
                    text = "vs ${comparison.previousYearLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            if (comparison.previousYearTotal > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Previous: ${comparison.previousYearTotal.formatAsCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ==================== Yearly Summary Stats ====================

@Composable
fun YearlySummaryStatsRow(
    avgMonthly: Double,
    transactionCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Avg/Month",
            value = avgMonthly.formatAsCurrency()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Transactions",
            value = transactionCount.toString()
        )
    }
}

// ==================== 12-Month Overlay Chart ====================

@Composable
fun YearlyOverlayChart(
    currentYearData: List<YearMonthTotal>,
    previousYearData: List<YearMonthTotal>,
    currentYearLabel: String,
    previousYearLabel: String
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val monthAbbreviations = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    val monthLabels = remember {
        (0..11).associateWith { monthAbbreviations[it] }
    }

    val hasPreviousData = previousYearData.any { it.total > 0 }

    LaunchedEffect(currentYearData, previousYearData) {
        modelProducer.runTransaction {
            lineSeries {
                series(currentYearData.map { it.total })
                if (hasPreviousData) {
                    series(previousYearData.map { it.total })
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentYearLabel,
                    style = MaterialTheme.typography.labelSmall
                )
                if (hasPreviousData) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = previousYearLabel,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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

// ==================== Weekly Snapshot Card ====================

@Composable
fun WeeklySnapshotCard(
    weekDateLabel: String,
    weeklyTotal: Double,
    previousWeekTotal: Double,
    weekOverWeekChange: Double,
    topCategoryName: String?,
    topCategoryAmount: Double
) {
    val isIncrease = weekOverWeekChange > 0
    val changeColor = if (isIncrease) {
        Color(0xFFE53935) // Red for spending increase
    } else {
        Color(0xFF43A047) // Green for spending decrease
    }
    val changeIcon = if (isIncrease) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown
    val dailyAvg = if (weeklyTotal > 0) weeklyTotal / 7.0 else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📅 ",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "This Week",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = weekDateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats row: Total | Daily avg | vs last week
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Total
                Column {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                    Text(
                        text = weeklyTotal.formatAsCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // Daily avg
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Daily avg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                    Text(
                        text = dailyAvg.formatAsCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // vs last week
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "vs last week",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                    if (previousWeekTotal > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = changeIcon,
                                contentDescription = null,
                                tint = changeColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${String.format("%.0f", weekOverWeekChange.absoluteValue)}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = changeColor
                            )
                        }
                    } else {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Top category this week
            if (topCategoryName != null && topCategoryAmount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Top category: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                    Text(
                        text = topCategoryName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = " (${topCategoryAmount.formatAsCurrency()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ==================== Month Comparison Card ====================

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

// ==================== Forecast Projection Chart ====================

/**
 * Cumulative spending projection chart.
 * Shows: solid line (actual cumulative) + dashed projection (burn rate to month-end)
 * + optional horizontal budget ceiling line.
 */
@Composable
fun ForecastProjectionChart(
    dailySpending: List<DailyTotal>,
    projectionLine: List<DailyTotal>,
    budgetCeiling: Double?
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // Build cumulative actual data and merge with projection
    LaunchedEffect(dailySpending, projectionLine, budgetCeiling) {
        // Build cumulative actual
        val cumulativeActual = mutableListOf<Double>()
        var runningTotal = 0.0
        for (dt in dailySpending) {
            runningTotal += dt.total
            cumulativeActual.add(runningTotal)
        }

        // Total days = last actual day + projection days
        val actualDays = dailySpending.size
        val projDays = projectionLine.size
        val totalDays = actualDays + projDays

        if (totalDays == 0) return@LaunchedEffect

        // Series 1: Actual cumulative (solid line) — values for actual days, null/0 for projected days
        val actualSeries = mutableListOf<Number>()
        for (i in 0 until totalDays) {
            if (i < actualDays) {
                actualSeries.add(cumulativeActual[i])
            } else {
                // Use last actual value as a bridge point for the first projected day,
                // then 0 for the rest (won't be rendered since line stops)
                actualSeries.add(cumulativeActual.lastOrNull() ?: 0.0)
            }
        }

        // Series 2: Projection line (dashed) — 0 for actual days, cumulative projected for rest
        val projSeries = mutableListOf<Number>()
        val lastActual = cumulativeActual.lastOrNull() ?: 0.0
        for (i in 0 until totalDays) {
            if (i < actualDays) {
                // Bridge: use last actual value at the junction point
                if (i == actualDays - 1) {
                    projSeries.add(lastActual)
                } else {
                    projSeries.add(0)
                }
            } else {
                val projIndex = i - actualDays
                if (projIndex < projectionLine.size) {
                    projSeries.add(projectionLine[projIndex].total)
                } else {
                    projSeries.add(0)
                }
            }
        }

        // Series 3: Budget ceiling (horizontal line)
        val ceilingSeries = mutableListOf<Number>()
        if (budgetCeiling != null && budgetCeiling > 0) {
            for (i in 0 until totalDays) {
                ceilingSeries.add(budgetCeiling)
            }
        }

        modelProducer.runTransaction {
            if (ceilingSeries.isNotEmpty()) {
                lineSeries {
                    series(actualSeries)
                    series(projSeries)
                    series(ceilingSeries)
                }
            } else {
                lineSeries {
                    series(actualSeries)
                    series(projSeries)
                }
            }
        }
    }

    // Build day labels
    val dayLabels = remember(dailySpending, projectionLine) {
        val labels = mutableMapOf<Int, String>()
        dailySpending.forEachIndexed { index, dt ->
            labels[index] = dt.dayOfMonth.toString()
        }
        projectionLine.forEachIndexed { index, dt ->
            labels[dailySpending.size + index] = dt.dayOfMonth.toString()
        }
        labels
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Legend row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendDot(color = MaterialTheme.colorScheme.primary, label = "Actual")
                LegendDot(color = Color.Gray, label = "Projected")
                if (budgetCeiling != null) {
                    LegendDot(color = MaterialTheme.colorScheme.error, label = "Budget")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
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

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
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

// ==================== Recipient Search ====================

/**
 * Header for the Top Recipients section with an integrated search bar.
 * Shows "Top Recipients" title with a search icon that toggles the search field.
 */
@Composable
fun RecipientSearchHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    isLoading: Boolean = false
) {
    var isSearchExpanded by remember { mutableStateOf(searchQuery.isNotBlank()) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) "Top Recipients" else "Recipient Search",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    if (isSearchExpanded && searchQuery.isNotBlank()) {
                        onClearSearch()
                    }
                    isSearchExpanded = !isSearchExpanded
                }
            ) {
                Icon(
                    imageVector = if (isSearchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (isSearchExpanded) "Close search" else "Search recipients"
                )
            }
        }

        if (isSearchExpanded) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search recipient...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        }
    }
}

/**
 * Aggregate total row shown at the bottom of search results.
 * Displays the combined total across all matching recipients.
 */
@Composable
fun RecipientSearchTotalRow(
    query: String,
    total: Double,
    transactionCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total for \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$transactionCount transactions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Text(
                text = total.formatAsCurrency(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
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

// ==================== Category Trend Card (Variable-Spend) ====================

@Composable
fun CategoryTrendCard(trend: CategoryTrend) {
    val categoryColor = trend.categoryColor?.let {
        try { getCategoryColor(it) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
    } ?: MaterialTheme.colorScheme.primary

    val spendLevel = trend.spendLevel
    val statusColor = when (spendLevel) {
        CategoryTrend.SpendLevel.NORMAL -> Color(0xFF2E7D32)   // green
        CategoryTrend.SpendLevel.ELEVATED -> Color(0xFFF57C00) // amber
        CategoryTrend.SpendLevel.HIGH -> Color(0xFFD32F2F)     // red
    }
    val statusIcon = when (spendLevel) {
        CategoryTrend.SpendLevel.NORMAL -> "✅"
        CategoryTrend.SpendLevel.ELEVATED -> "⚠️"
        CategoryTrend.SpendLevel.HIGH -> "🔴"
    }
    val statusLabel = when (spendLevel) {
        CategoryTrend.SpendLevel.NORMAL -> "Normal"
        CategoryTrend.SpendLevel.ELEVATED -> "+${String.format("%.0f", trend.overspendPercentage.absoluteValue)}% above avg"
        CategoryTrend.SpendLevel.HIGH -> "+${String.format("%.0f", trend.overspendPercentage.absoluteValue)}% above avg"
    }

    // Chart model
    val modelProducer = remember { CartesianChartModelProducer() }
    val monthLabels = remember(trend.monthlyData) {
        trend.monthlyData.mapIndexed { index, mt ->
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

    LaunchedEffect(trend.monthlyData) {
        modelProducer.runTransaction {
            lineSeries {
                series(trend.monthlyData.map { it.total })
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: colored dot + name + current total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(categoryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trend.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = trend.currentMonthTotal.formatAsCurrency(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Status badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$statusIcon $statusLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Line chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
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

            Spacer(modifier = Modifier.height(4.dp))

            // Average footer
            Text(
                text = "Avg: ${trend.mean.formatAsCurrency()}/mo  •  CV: ${String.format("%.0f", trend.coefficientOfVariation)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ==================== Budget Setup Banner ====================

/**
 * Banner shown in the Monthly analytics tab when no budgets exist.
 * Prompts users to set up spending limits.
 */
@Composable
fun BudgetSetupBanner(
    onSetUpBudgets: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Track your spending limits",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Set up budgets to monitor spending by category",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            FilledTonalButton(
                onClick = onSetUpBudgets,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Set Up")
            }
        }
    }
}

// ==================== Recurring vs One-time Spending Breakdown ====================

@Composable
fun RecurringBreakdownCard(
    recurringTotal: Double,
    oneTimeTotal: Double,
    totalForMonth: Double,
    topRecurringNames: String
) {
    val recurringPct = if (totalForMonth > 0) (recurringTotal / totalForMonth * 100).toInt() else 0
    val oneTimePct = if (totalForMonth > 0) (oneTimeTotal / totalForMonth * 100).toInt() else 0
    val recurringFraction = if (totalForMonth > 0) (recurringTotal / totalForMonth).toFloat().coerceIn(0f, 1f) else 0f
    val oneTimeFraction = if (totalForMonth > 0) (oneTimeTotal / totalForMonth).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Recurring vs One-time",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Recurring bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recurring",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${recurringTotal.formatAsCurrency()} ($recurringPct%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                LinearProgressIndicator(
                    progress = { recurringFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // One-time bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "One-time",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${oneTimeTotal.formatAsCurrency()} ($oneTimePct%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                LinearProgressIndicator(
                    progress = { oneTimeFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Top recurring names
            if (topRecurringNames.isNotBlank()) {
                Text(
                    text = "Top recurring: $topRecurringNames",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
