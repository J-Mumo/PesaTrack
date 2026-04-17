package com.pesatrack.presentation.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.domain.models.BudgetForecast
import com.pesatrack.domain.models.BudgetProgress
import com.pesatrack.domain.models.BudgetStatus
import com.pesatrack.presentation.components.ExpenseCard
import com.pesatrack.utils.formatAsCurrency
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToExpenses: () -> Unit,
    onNavigateToCategorize: (Long) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToBatchCategorize: () -> Unit = {},
    onNavigateToManualEntry: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check SMS permission status on every resume (e.g. returning from App Settings)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val hasPermission = hasSmsPermission(context)
            viewModel.updateSmsPermissionStatus(hasPermission)
        }
    }

    // Permission launcher for SMS — used by the banner "Enable" button
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        viewModel.updateSmsPermissionStatus(granted)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToManualEntry,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add expense",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Settings button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PesaTrack",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // SMS Permission Banner (shown when permission missing + not permanently dismissed)
        if (uiState.showSmsPermissionBanner) {
            item {
                SmsPermissionBanner(
                    onEnable = {
                        // Check if we should show rationale or go to App Settings
                        smsPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                            )
                        )
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    onDismissSession = { viewModel.dismissSmsBannerSession() },
                    onDismissPermanently = { viewModel.dismissSmsBannerPermanently() }
                )
            }
        }
        
        // Monthly Summary Card
        item {
            MonthlySummaryCard(
                total = uiState.totalThisMonth,
                investmentTotal = uiState.investmentThisMonth
            )
        }

        // Budget Summary Card (when user has budgets)
        if (uiState.budgetProgressList.isNotEmpty()) {
            item {
                BudgetSummaryCard(
                    progressList = uiState.budgetProgressList,
                    onViewBudgets = onNavigateToBudget
                )
            }
        }

        // Forecast Card (when ≥5 days into period and budgets exist)
        if (uiState.showForecastCard) {
            item {
                ForecastCard(
                    forecasts = uiState.budgetForecasts,
                    onViewBudgets = onNavigateToBudget
                )
            }
        }

        // Budget Prompt Card (when user has no budgets but enough data)
        if (uiState.showBudgetPrompt) {
            item {
                BudgetPromptCard(
                    categoryName = uiState.budgetPromptCategoryName,
                    amount = uiState.budgetPromptAmount,
                    onSetBudget = onNavigateToBudget,
                    onDismiss = { viewModel.dismissBudgetPrompt() }
                )
            }
        }

        // Spending Trend Card (mini chart)
        if (uiState.monthlyTrend.isNotEmpty()) {
            item {
                SpendingTrendCard(
                    trendData = uiState.monthlyTrend,
                    comparison = uiState.monthComparison,
                    onViewAnalytics = onNavigateToAnalytics
                )
            }
        }
        
        // Import History Card
        item {
            ImportHistoryCard(onImport = onNavigateToImport)
        }
        
        // Uncategorized Alert
        if (uiState.uncategorizedCount > 0) {
            item {
                UncategorizedAlert(
                    count = uiState.uncategorizedCount,
                    onCategorize = {
                        // Navigate to batch categorize screen to review all uncategorized
                        onNavigateToBatchCategorize()
                    }
                )
            }
        }
        
        // Recent Expenses Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Expenses",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onNavigateToExpenses) {
                    Text("View All")
                }
            }
        }
        
        // Recent Expenses List
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (uiState.recentExpenses.isEmpty()) {
            item {
                EmptyExpensesCard()
            }
        } else {
            items(uiState.recentExpenses) { ewc ->
                ExpenseCard(
                    expense = ewc.expense,
                    categoryName = ewc.categoryName,
                    categoryColor = ewc.categoryColor,
                    onClick = {
                        onNavigateToCategorize(ewc.expense.id)
                    }
                )
            }
        }
    }
    }
}

@Composable
fun MonthlySummaryCard(
    total: Double,
    investmentTotal: Double = 0.0
) {
    val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    val investmentPct = if (total > 0) (investmentTotal / total) * 100.0 else 0.0
    
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
                text = currentMonth,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = total.formatAsCurrency(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Total expenses this month",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            // Investment breakdown — shown only when there's investment data
            if (investmentTotal > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📈 ",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${investmentTotal.formatAsCurrency()} (${String.format("%.0f", investmentPct)}%) invested",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun UncategorizedAlert(
    count: Int,
    onCategorize: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCategorize() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$count uncategorized expense${if (count > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Tap to categorize",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null
            )
        }
    }
}

@Composable
fun EmptyExpensesCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No expenses yet",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your expenses will appear here when PesaTrack detects M-PESA SMS messages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ImportHistoryCard(onImport: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onImport() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Import SMS History",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Import past M-PESA transactions from your SMS inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun SpendingTrendCard(
    trendData: List<MonthlyTotal>,
    comparison: MonthComparison?,
    onViewAnalytics: () -> Unit
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(trendData) {
        if (trendData.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(trendData.map { it.total })
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewAnalytics() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spending Trend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (comparison != null && comparison.previousMonthTotal > 0) {
                        val isIncrease = comparison.percentageChange > 0
                        val arrow = if (isIncrease) "↑" else "↓"
                        val color = if (isIncrease) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color(0xFF2E7D32)
                        }
                        Text(
                            text = "$arrow${String.format("%.0f", comparison.percentageChange.absoluteValue)}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "View →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mini line chart — no axes, just the trend line
            if (trendData.isNotEmpty()) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }
        }
    }
}

@Composable
fun BudgetSummaryCard(
    progressList: List<BudgetProgress>,
    onViewBudgets: () -> Unit
) {
    val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewBudgets() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 $currentMonth Budget",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "View →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            for (progress in progressList) {
                BudgetMiniProgress(progress)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BudgetMiniProgress(progress: BudgetProgress) {
    val name = progress.budget.categoryName ?: "Total"
    val barColor = when (progress.status) {
        BudgetStatus.UNDER -> MaterialTheme.colorScheme.primary
        BudgetStatus.WARNING -> Color(0xFFFF9800)
        BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.error
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val categoryColor = progress.budget.categoryColor?.let {
                    try { Color(it.toColorInt()) } catch (_: Exception) { null }
                }
                if (categoryColor != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress.status == BudgetStatus.WARNING) {
                    Text("⚠️ ", style = MaterialTheme.typography.labelSmall)
                } else if (progress.status == BudgetStatus.EXCEEDED) {
                    Text("🚨 ", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    text = "${String.format("%.0f", progress.percentage)}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { (progress.percentage / 100.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
fun BudgetPromptCard(
    categoryName: String?,
    amount: Double?,
    onSetBudget: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "💡 Set a spending budget?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (categoryName != null && amount != null) {
                Text(
                    text = "You spent ${amount.formatAsCurrency()} on $categoryName last month. Set a budget to stay on track.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = "Create budgets to track your spending and get alerts when you're close to your limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSetBudget,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("Set Budget")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Maybe Later")
                }
            }
        }
    }
}

// ==================== SMS Permission Banner ====================

/**
 * Check if both SMS permissions are granted.
 */
private fun hasSmsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Banner shown on the Home screen when SMS permission is not granted.
 * Offers three actions:
 * - Enable: Request the permission (or open App Settings if permanently denied)
 * - Not now: Hide for this session
 * - Don't ask again: Permanently dismiss (respects manual-only users)
 */
@Composable
fun SmsPermissionBanner(
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSession: () -> Unit,
    onDismissPermanently: () -> Unit
) {
    var showDismissMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sms,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable automatic M-PESA tracking?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Box {
                    IconButton(
                        onClick = { showDismissMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = showDismissMenu,
                        onDismissRequest = { showDismissMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Not now") },
                            onClick = {
                                showDismissMenu = false
                                onDismissSession()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Don't ask again") },
                            onClick = {
                                showDismissMenu = false
                                onDismissPermanently()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Grant SMS permission to auto-capture M-PESA transactions from your messages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onEnable,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Enable")
                }
                OutlinedButton(
                    onClick = onDismissSession,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Not now")
                }
            }
        }
    }
}

// ==================== Forecast Card ====================

@Composable
fun ForecastCard(
    forecasts: List<BudgetForecast>,
    onViewBudgets: () -> Unit
) {
    val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewBudgets() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔮 $currentMonth Forecast",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "View →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Find the "Total" budget forecast (categoryId == null) if it exists
            val totalForecast = forecasts.find { it.budget.categoryId == null }
            if (totalForecast != null) {
                ForecastTotalRow(totalForecast)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Safe daily budget (from total or first forecast)
            val primaryForecast = totalForecast ?: forecasts.firstOrNull()
            if (primaryForecast != null && primaryForecast.daysRemaining > 0) {
                val safeDailyFormatted = String.format("KES %,.0f", primaryForecast.safeDailyBudget)
                val safeDailyColor = if (primaryForecast.safeDailyBudget < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                }
                Text(
                    text = "Safe daily spend: $safeDailyFormatted/day • ${primaryForecast.daysRemaining} days left",
                    style = MaterialTheme.typography.bodySmall,
                    color = safeDailyColor
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Per-category forecast rows (exclude "Total", max 4)
            val categoryForecasts = forecasts
                .filter { it.budget.categoryId != null }
                .take(4)

            for (forecast in categoryForecasts) {
                ForecastCategoryRow(forecast)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ForecastTotalRow(forecast: BudgetForecast) {
    val projectedFormatted = String.format("KES %,.0f", forecast.projectedTotal)
    val budgetFormatted = String.format("KES %,.0f", forecast.budget.amount)
    val pctFormatted = String.format("%.0f%%", forecast.projectedPercentage)
    val overUnder = forecast.projectedTotal - forecast.budget.amount

    val statusColor = when {
        forecast.projectedPercentage >= 100 -> MaterialTheme.colorScheme.error
        forecast.projectedPercentage >= 80 -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Projected: $projectedFormatted",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = pctFormatted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
        Text(
            text = "Budget: $budgetFormatted" + if (overUnder > 0) {
                " • ⚠️ ~${String.format("KES %,.0f", overUnder)} over"
            } else {
                " • ✅ on track"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ForecastCategoryRow(forecast: BudgetForecast) {
    val name = forecast.budget.categoryName ?: "Unknown"

    val (icon, description, textColor) = when {
        forecast.exhaustionDate != null -> {
            val exhaustionDateStr = SimpleDateFormat("MMM d", Locale.getDefault())
                .format(Date(forecast.exhaustionDate))
            Triple(
                "🔴",
                "$name — runs out ~$exhaustionDateStr",
                MaterialTheme.colorScheme.error
            )
        }
        forecast.projectedPercentage >= 80 -> {
            Triple(
                "🟡",
                "$name — on track at ${String.format("%.0f", forecast.projectedPercentage)}%",
                Color(0xFFFF9800)
            )
        }
        else -> {
            Triple(
                "🟢",
                "$name — comfortable at ${String.format("%.0f", forecast.projectedPercentage)}%",
                MaterialTheme.colorScheme.primary
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}
