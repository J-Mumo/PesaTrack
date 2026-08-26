package com.pesatrack.presentation.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.database.dao.PaymentTypeTotal
import com.pesatrack.data.local.database.dao.TopSpender
import com.pesatrack.data.local.database.dao.YearMonthTotal
import com.pesatrack.domain.models.CategoryTrend
import com.pesatrack.domain.models.CategoryMonthGrid
import com.pesatrack.domain.models.GridRow
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.domain.models.YearComparison
import com.pesatrack.presentation.theme.getCategoryColor
import com.pesatrack.utils.formatAsCurrency
import java.util.Locale
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    onNavigateToWeeklyReview: () -> Unit = {},
    onNavigateToMonthlyReview: () -> Unit = {},
    onNavigateToQuarterlyReview: () -> Unit = {},
    onNavigateToYearInReview: () -> Unit = {},
    onNavigateToExpenseList: (categoryId: Int) -> Unit = {},
    onNavigateToCategorize: () -> Unit = {},
    initialSection: String? = null,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Honour a deep-link section by switching to Charts → Monthly on first
    // composition. The scroll-to behaviour is handled inside MonthlyTabContent.
    var pendingSection by remember { mutableStateOf(initialSection) }
    LaunchedEffect(initialSection) {
        when (initialSection) {
            com.pesatrack.presentation.navigation.Screen.Analytics.SECTION_BY_CATEGORY -> {
                viewModel.selectInsightsTab(InsightsTab.CHARTS)
                viewModel.selectTab(AnalyticsTab.MONTHLY)
            }
            com.pesatrack.presentation.navigation.Screen.Analytics.SECTION_YEARLY_GRID -> {
                viewModel.selectInsightsTab(InsightsTab.CHARTS)
                viewModel.selectTab(AnalyticsTab.YEARLY)
                viewModel.selectYearlyView(YearlyView.GRID)
                pendingSection = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top-level Tab Row: Insights / Charts
        TabRow(
            selectedTabIndex = if (uiState.selectedInsightsTab == InsightsTab.INSIGHTS) 0 else 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Tab(
                selected = uiState.selectedInsightsTab == InsightsTab.INSIGHTS,
                onClick = { viewModel.selectInsightsTab(InsightsTab.INSIGHTS) },
                text = { Text("Insights") }
            )
            Tab(
                selected = uiState.selectedInsightsTab == InsightsTab.CHARTS,
                onClick = { viewModel.selectInsightsTab(InsightsTab.CHARTS) },
                text = { Text("Charts") }
            )
        }

        when (uiState.selectedInsightsTab) {
            InsightsTab.INSIGHTS -> InsightsTabContent(
                uiState = uiState,
                onNavigateToWeeklyReview = onNavigateToWeeklyReview,
                onNavigateToMonthlyReview = onNavigateToMonthlyReview,
                onNavigateToQuarterlyReview = onNavigateToQuarterlyReview,
                onNavigateToYearInReview = onNavigateToYearInReview,
                onNavigateToExpenseList = onNavigateToExpenseList,
                onNavigateToCategorize = onNavigateToCategorize
            )
            InsightsTab.CHARTS -> ChartsTabContent(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateToBudget = onNavigateToBudget,
                onNavigateToWeeklyReview = onNavigateToWeeklyReview,
                onNavigateToMonthlyReview = onNavigateToMonthlyReview,
                pendingSection = pendingSection,
                onSectionConsumed = { pendingSection = null }
            )
        }
    }
}

// ==================== Insights Tab (new) ====================

@Composable
fun InsightsTabContent(
    uiState: AnalyticsUiState,
    onNavigateToWeeklyReview: () -> Unit,
    onNavigateToMonthlyReview: () -> Unit,
    onNavigateToQuarterlyReview: () -> Unit,
    onNavigateToYearInReview: () -> Unit = {},
    onNavigateToExpenseList: (categoryId: Int) -> Unit,
    onNavigateToCategorize: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Weekly Snapshot Card (rolling 7 days vs previous 7 days)
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

        // 2. Weekly Review navigation card
        item {
            val weeklyArrow = if (uiState.weekOverWeekChange >= 0) "↑" else "↓"
            val weeklyPct = String.format("%.0f", uiState.weekOverWeekChange.absoluteValue)
            val weeklySummary = if (uiState.weeklyTotal > 0) {
                "${uiState.weeklyTotal.formatAsCurrency()} spent this week $weeklyArrow ${weeklyPct}% vs last week."
            } else {
                "No spending recorded this week yet."
            }
            val biggestChangeText = uiState.topCategoryThisWeek?.let { cat ->
                "Top category: $cat (${uiState.topCategoryThisWeekAmount.formatAsCurrency()})"
            } ?: ""

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToWeeklyReview
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Your week in review",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = weeklySummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (biggestChangeText.isNotEmpty()) {
                                Text(
                                    text = biggestChangeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Weekly Review",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Monthly Review summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToMonthlyReview
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column {
                            Text(
                                text = "Monthly Review",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${uiState.selectedMonthLabel}: ${uiState.totalForMonth.formatAsCurrency()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Monthly Review",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Pace Card — shown after 7th of month
        if (uiState.showPaceCard && uiState.paceData != null) {
            item {
                PaceInsightCard(paceData = uiState.paceData!!)
            }
        }

        // 4. Quiet Leak Card
        if (uiState.showQuietLeakCard) {
            item {
                QuietLeakInsightCard(
                    quietLeaks = uiState.quietLeaks,
                    onCategoryTap = onNavigateToExpenseList
                )
            }
        }

        // 5. Categorization Nudge Card
        if (uiState.showCategorizationNudge) {
            item {
                CategorizationNudgeCard(
                    percentage = uiState.uncategorizedPercentage,
                    onTap = onNavigateToCategorize
                )
            }
        }

        // 5b. Savings Rate Card (Phase 4 — only when we have honest income data)
        if (uiState.showSavingsRateCard && uiState.savingsRate != null) {
            item {
                SavingsRateInsightCard(data = uiState.savingsRate!!)
            }
        }

        // 6. Quarterly Review summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToQuarterlyReview
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Column {
                            Text(
                                text = "Quarterly Review",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "See your spending patterns across 3 months",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Quarterly Review",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 7. Year-in-Review summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToYearInReview
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = "Year in Review",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "See your annual spending summary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Year in Review",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 8. Budget Burn-Down Card
        if (uiState.showBudgetBurnDown) {
            item {
                BudgetBurnDownCard(burnDowns = uiState.budgetBurnDowns)
            }
        }

        // Bottom spacer
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==================== Budget Burn-Down Card ====================

@Composable
fun BudgetBurnDownCard(burnDowns: List<BudgetBurnDownData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Budget burn-down",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            burnDowns.forEach { data ->
                val dayOrdinal = ordinalDay(data.exhaustionDay)
                Text(
                    text = "At today\u2019s pace your ${data.categoryName} budget runs out on the $dayOrdinal (${data.daysEarly} days early).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

private fun ordinalDay(day: Int): String {
    val suffix = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

// ==================== Pace Insight Card ====================

@Composable
fun PaceInsightCard(paceData: PaceCardData) {
    val arrow = if (paceData.delta >= 0) "↑" else "↓"
    val deltaFormatted = paceData.delta.absoluteValue.formatAsCurrency()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Spending Pace",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "At today's pace, you'll end ${paceData.monthName} at ${paceData.projected.formatAsCurrency()} ($arrow $deltaFormatted vs ${paceData.prevMonthName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ==================== Savings Rate Insight Card (Phase 4) ====================

@Composable
fun SavingsRateInsightCard(data: SavingsRateData) {
    var showAssumptions by remember { mutableStateOf(false) }
    val sourceLabel = when (data.effectiveIncomeSource) {
        EffectiveIncomeSource.DETECTED -> "detected income"
        EffectiveIncomeSource.DETECTED_BELOW_OVERRIDE -> "the income you set"
        EffectiveIncomeSource.MANUAL_OVERRIDE -> "the income you set"
        EffectiveIncomeSource.NONE -> "income"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { showAssumptions = !showAssumptions },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Savings rate",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "${String.format(Locale.getDefault(), "%.0f", data.currentMonthPct)}% this month",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "${String.format(Locale.getDefault(), "%.0f", data.rollingThreeMonthPct)}% across the last three months",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = if (showAssumptions) "Hide details" else "Tap for assumptions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
            if (showAssumptions) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Based on $sourceLabel of ${data.currentMonthIncome.formatAsCurrency()} and ${data.currentMonthSavings.formatAsCurrency()} moved into Investment & Savings this month. Savings rate = savings ÷ income.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

// ==================== Quiet Leak Insight Card ====================

@Composable
fun QuietLeakInsightCard(
    quietLeaks: List<QuietLeakData>,
    onCategoryTap: (categoryId: Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Quiet Leaks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = "Small, frequent transactions that add up:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            quietLeaks.forEach { leak ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCategoryTap(leak.categoryId) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${leak.categoryName}: ${leak.transactionCount} transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = leak.total.formatAsCurrency(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ==================== Categorization Nudge Card ====================

@Composable
fun CategorizationNudgeCard(
    percentage: Double,
    onTap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onTap,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${String.format("%.0f", percentage)}% of your spend is uncategorized",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Categorizing unlocks category insights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Categorize",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== Charts Tab (existing content restructured) ====================

@Composable
fun ChartsTabContent(
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onNavigateToBudget: () -> Unit,
    onNavigateToWeeklyReview: () -> Unit,
    onNavigateToMonthlyReview: () -> Unit,
    pendingSection: String? = null,
    onSectionConsumed: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Charts sub-tab Row: Monthly / Yearly
        TabRow(
            selectedTabIndex = if (uiState.selectedTab == AnalyticsTab.MONTHLY) 0 else 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                onClearSearch = { viewModel.clearRecipientSearch() },
                pendingSection = pendingSection,
                onSectionConsumed = onSectionConsumed
            )
            AnalyticsTab.YEARLY -> YearlyTabContent(
                uiState = uiState,
                onPreviousYear = { viewModel.previousYear() },
                onNextYear = { viewModel.nextYear() },
                canGoNextYear = viewModel.canGoNextYear(),
                onSearchRecipient = { viewModel.searchRecipient(it) },
                onClearSearch = { viewModel.clearRecipientSearch() },
                onSelectYearlyView = { viewModel.selectYearlyView(it) },
                onToggleGridGroup = { viewModel.toggleYearlyGridGroup(it) },
                onToggleGridIncludeFees = { viewModel.toggleYearlyGridIncludeFees() }
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
    onClearSearch: () -> Unit = {},
    pendingSection: String? = null,
    onSectionConsumed: () -> Unit = {}
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Index of the "By Category" SectionHeader inside the LazyColumn below.
    // Items above it: MonthSelectorRow(0) + MonthComparisonCard placeholder(1)
    // + SummaryStatsRow(2), plus optional BudgetSetupBanner and loading row.
    val byCategoryHeaderIndex = 3 +
        (if (!uiState.hasActiveBudgets) 1 else 0) +
        (if (uiState.isLoading) 1 else 0)

    LaunchedEffect(pendingSection, uiState.categoryBreakdown.isNotEmpty()) {
        if (pendingSection ==
                com.pesatrack.presentation.navigation.Screen.Analytics.SECTION_BY_CATEGORY &&
            uiState.categoryBreakdown.isNotEmpty()
        ) {
            listState.animateScrollToItem(byCategoryHeaderIndex)
            onSectionConsumed()
        }
    }

    LazyColumn(
        state = listState,
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

        // Monthly Trend Line Chart
        if (uiState.monthlyTrend.isNotEmpty()) {
            item {
                SectionHeader(title = "Monthly Trend")
            }
            item {
                MonthlyTrendChart(data = uiState.monthlyTrend)
            }
        }

        // Income vs Spend (Phase 4) — only when we have detected income
        if (uiState.incomeVsSpend.isNotEmpty()) {
            item {
                SectionHeader(title = "Income vs Spend")
            }
            item {
                Text(
                    text = "Last 12 months. Income is from M-PESA and bank SMS only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            item {
                IncomeVsSpendChart(data = uiState.incomeVsSpend)
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
    onClearSearch: () -> Unit = {},
    onSelectYearlyView: (YearlyView) -> Unit = {},
    onToggleGridGroup: (Long) -> Unit = {},
    onToggleGridIncludeFees: () -> Unit = {}
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

        // Overview | Grid segmented toggle. Defaults to Overview so existing
        // users see the cards they already know; Grid opens the Excel-style
        // Category × Month pivot.
        item {
            YearlyViewToggle(
                selected = uiState.yearlySelectedView,
                onSelect = onSelectYearlyView
            )
        }

        when (uiState.yearlySelectedView) {
            YearlyView.OVERVIEW -> yearlyOverviewItems(
                uiState = uiState,
                onSearchRecipient = onSearchRecipient,
                onClearSearch = onClearSearch
            )
            YearlyView.GRID -> yearlyGridItems(
                uiState = uiState,
                onToggleGridGroup = onToggleGridGroup,
                onToggleGridIncludeFees = onToggleGridIncludeFees
            )
        }

        // Bottom spacer for navigation bar
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Extracts the existing Yearly Overview items so they can live alongside
 * the new Grid view. Behaviour is unchanged from before the split.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.yearlyOverviewItems(
    uiState: AnalyticsUiState,
    onSearchRecipient: (String) -> Unit,
    onClearSearch: () -> Unit
) {
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
}

/**
 * Yearly → Grid sub-view items. Renders the Category × Month pivot table
 * matching the Excel workbook users described (rows = groups, columns =
 * periods, cells = KES, totals in the last column and last row).
 */
private fun androidx.compose.foundation.lazy.LazyListScope.yearlyGridItems(
    uiState: AnalyticsUiState,
    onToggleGridGroup: (Long) -> Unit,
    onToggleGridIncludeFees: () -> Unit
) {
    when {
        uiState.yearlyGridLoading || uiState.yearlyGrid == null -> {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        uiState.yearlyGrid.rows.isEmpty() -> {
            item {
                EmptyGridCard(year = uiState.yearlyGrid.year)
            }
        }
        else -> {
            item {
                CategoryMonthGridCard(
                    grid = uiState.yearlyGrid,
                    expandedGroups = uiState.yearlyGridExpandedGroups,
                    includeFees = uiState.yearlyGridIncludeFees,
                    onToggleGroup = onToggleGridGroup,
                    onToggleIncludeFees = onToggleGridIncludeFees
                )
            }
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

            val currentColor = MaterialTheme.colorScheme.primary
            val previousColor = MaterialTheme.colorScheme.outline
            val currentPoint = rememberShapeComponent(
                fill = fill(currentColor),
                shape = CorneredShape.Pill,
            )
            val previousPoint = rememberShapeComponent(
                fill = fill(previousColor),
                shape = CorneredShape.Pill,
            )
            val currentLine = LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(currentColor)),
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.point(component = currentPoint, size = 7.dp)
                ),
            )
            val previousLine = LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(previousColor)),
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.point(component = previousPoint, size = 7.dp)
                ),
            )
            val lineProvider = if (hasPreviousData) {
                LineCartesianLayer.LineProvider.series(currentLine, previousLine)
            } else {
                LineCartesianLayer.LineProvider.series(currentLine)
            }

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(lineProvider = lineProvider),
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
            val lineColor = MaterialTheme.colorScheme.primary
            val pointShape = rememberShapeComponent(
                fill = fill(lineColor),
                shape = CorneredShape.Pill,
            )
            val line = LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.point(component = pointShape, size = 7.dp)
                ),
            )
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(line)
                    ),
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

// ==================== Income vs Spend Chart (Phase 4) ====================

@Composable
fun IncomeVsSpendChart(data: List<IncomeSpendPoint>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val monthLabels = remember(data) {
        data.mapIndexed { index, p ->
            index to p.monthKey.takeLast(2).let { m ->
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
                // Series 0 = income (green), series 1 = spend (red).
                series(data.map { it.income })
                series(data.map { it.spend })
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Tiny legend so the two lines are identifiable.
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(color = androidx.compose.ui.graphics.Color(0xFF388E3C))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Income",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                LegendDot(color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Spend",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxSize()) {
                val incomeColor = androidx.compose.ui.graphics.Color(0xFF388E3C)
                val spendColor = MaterialTheme.colorScheme.error
                val incomeLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(incomeColor))
                )
                val spendLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(spendColor))
                )
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(incomeLine, spendLine)
                        ),
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
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
    )
}

// ==================== Category Breakdown ====================

@Composable
fun CategoryBreakdownChart(
    data: List<CategoryTotal>,
    totalForMonth: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp)
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
            }
            HorizontalDivider()

            data.forEachIndexed { index, category ->
                val pct = if (totalForMonth > 0) (category.total / totalForMonth) * 100.0 else 0.0
                val dotColor = category.categoryColor?.let {
                    try { getCategoryColor(it) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                } ?: MaterialTheme.colorScheme.primary

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = category.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = category.total.formatAsCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(96.dp)
                    )
                    Text(
                        text = String.format("%.0f%%", pct),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp)
                    )
                }
                if (index < data.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            // Total footer row
            val visibleTotal = data.sumOf { it.total }
            val totalPct = if (totalForMonth > 0) (visibleTotal / totalForMonth) * 100.0 else 0.0
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = visibleTotal.formatAsCurrency(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp)
                )
                Text(
                    text = String.format("%.0f%%", totalPct),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
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

// ==================== Yearly Grid (Category × Month pivot) ====================

/**
 * Segmented control shown at the top of the Yearly tab so the user can flip
 * between the existing Overview cards and the new Category × Month Grid.
 */
@Composable
private fun YearlyViewToggle(
    selected: YearlyView,
    onSelect: (YearlyView) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        SegmentedButton(
            selected = selected == YearlyView.OVERVIEW,
            onClick = { onSelect(YearlyView.OVERVIEW) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("Overview") }
        SegmentedButton(
            selected = selected == YearlyView.GRID,
            onClick = { onSelect(YearlyView.GRID) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("Grid") }
    }
}

@Composable
private fun EmptyGridCard(year: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No spending recorded for $year",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Import SMS or add expenses to see the monthly grid.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The Category × Month pivot table. First column (category label) is sticky
 * on the left; header row is sticky on the top of the scrollable area. Tap a
 * group row to expand its sub-categories. Cells with no data render as "—"
 * so an empty period is not confused with a zero.
 */
@Composable
private fun CategoryMonthGridCard(
    grid: CategoryMonthGrid,
    expandedGroups: Set<Long>,
    includeFees: Boolean,
    onToggleGroup: (Long) -> Unit,
    onToggleIncludeFees: () -> Unit
) {
    // Two horizontal scroll states kept in sync so the sticky first column
    // stays fixed while header + data cells scroll together.
    val headerScroll = rememberScrollState()
    val bodyScroll = rememberScrollState()
    LaunchedEffect(bodyScroll.value) { headerScroll.scrollTo(bodyScroll.value) }

    // Build the visible row list: groups first, then their children when
    // expanded. Rows already come ordered (group followed by its subs) from
    // the repository, so we filter — no re-sort needed.
    val visibleRows: List<GridRow> = remember(grid, expandedGroups) {
        grid.rows.filter { row ->
            row.depth == 0 || (row.parentId != null && expandedGroups.contains(row.parentId))
        }
    }

    val periodCount = grid.periodLabels.size

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            // Title + controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly grid • ${grid.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Row per group. Tap to expand sub-categories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onToggleIncludeFees) {
                    Text(if (includeFees) "Hide fees" else "Show fees")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Sticky column widths
            val categoryColWidth = 140.dp
            val cellWidth = 88.dp

            // Header row
            Row(modifier = Modifier.fillMaxWidth()) {
                GridHeaderCell(
                    text = "Category",
                    width = categoryColWidth,
                    isSticky = true
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(headerScroll)
                ) {
                    grid.periodLabels.forEachIndexed { i, label ->
                        val display = if (grid.partialPeriodIndexes.contains(i)) "$label*" else label
                        GridHeaderCell(text = display, width = cellWidth)
                    }
                    GridHeaderCell(text = "Total", width = cellWidth, emphasized = true)
                }
            }

            HorizontalDivider()

            // Body rows
            Column(modifier = Modifier.fillMaxWidth()) {
                visibleRows.forEach { row ->
                    val rowColor = row.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (row.isExpandable) Modifier.clickable { onToggleGroup(row.categoryId) }
                                else Modifier
                            )
                    ) {
                        GridCategoryCell(
                            row = row,
                            width = categoryColWidth,
                            expanded = expandedGroups.contains(row.categoryId),
                            color = rowColor
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(bodyScroll)
                        ) {
                            row.monthlyValues.forEach { v ->
                                GridAmountCell(
                                    amount = v,
                                    width = cellWidth,
                                    isGroup = row.depth == 0
                                )
                            }
                            GridAmountCell(
                                amount = row.yearTotal.takeIf { it != 0.0 || row.monthlyValues.any { it != null } },
                                width = cellWidth,
                                isGroup = row.depth == 0,
                                emphasized = true
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

                // Grand total row
                Row(modifier = Modifier.fillMaxWidth()) {
                    GridCategoryCell(
                        row = null,
                        width = categoryColWidth,
                        expanded = false,
                        color = null,
                        overrideLabel = "Total"
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(bodyScroll)
                    ) {
                        grid.periodTotals.forEach { v ->
                            GridAmountCell(
                                amount = v.takeIf { it > 0.0 },
                                width = cellWidth,
                                isGroup = true,
                                emphasized = true
                            )
                        }
                        GridAmountCell(
                            amount = grid.grandTotal.takeIf { it > 0.0 },
                            width = cellWidth,
                            isGroup = true,
                            emphasized = true
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (grid.partialPeriodIndexes.isNotEmpty()) {
                Text(
                    text = "* current period is still in progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            @Suppress("UNUSED_VARIABLE")
            val unused = periodCount // silence warning if not otherwise used
        }
    }
}

@Composable
private fun GridHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isSticky: Boolean = false,
    emphasized: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .background(
                if (isSticky) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = if (isSticky) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GridCategoryCell(
    row: GridRow?,
    width: androidx.compose.ui.unit.Dp,
    expanded: Boolean,
    color: Color?,
    overrideLabel: String? = null
) {
    Row(
        modifier = Modifier
            .width(width)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val indent = ((row?.depth ?: 0) * 8).dp
        Spacer(Modifier.width(indent))
        if (color != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = overrideLabel ?: (row?.label ?: ""),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if ((row?.depth ?: 0) == 0 || overrideLabel != null) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (row?.isExpandable == true) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GridAmountCell(
    amount: Double?,
    width: androidx.compose.ui.unit.Dp,
    isGroup: Boolean,
    emphasized: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = if (amount == null) "—" else amount.formatAsCurrency(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = when {
                emphasized -> FontWeight.Bold
                isGroup -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
            color = if (amount == null)
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


