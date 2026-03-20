package com.pesatrack.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.domain.models.CategoryTrend
import com.pesatrack.domain.models.DEFAULT_VARIABLE_SPEND_CATEGORIES
import com.pesatrack.domain.models.MonthComparison
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val calendar = Calendar.getInstance()

    init {
        // Start with current month
        _uiState.update {
            it.copy(
                selectedYear = calendar.get(Calendar.YEAR),
                selectedMonth = calendar.get(Calendar.MONTH) + 1 // 1-based
            )
        }
        loadAllData()
    }

    /**
     * Navigate to the previous month
     */
    fun previousMonth() {
        _uiState.update { state ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, state.selectedYear)
                set(Calendar.MONTH, state.selectedMonth - 1) // 0-based
                add(Calendar.MONTH, -1)
            }
            state.copy(
                selectedYear = cal.get(Calendar.YEAR),
                selectedMonth = cal.get(Calendar.MONTH) + 1,
                isLoading = true
            )
        }
        loadMonthData()
    }

    /**
     * Navigate to the next month (capped at current month)
     */
    fun nextMonth() {
        val now = Calendar.getInstance()
        val state = _uiState.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, state.selectedYear)
            set(Calendar.MONTH, state.selectedMonth - 1)
            add(Calendar.MONTH, 1)
        }
        // Don't go past current month
        if (cal.get(Calendar.YEAR) > now.get(Calendar.YEAR) ||
            (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    cal.get(Calendar.MONTH) > now.get(Calendar.MONTH))
        ) {
            return
        }
        _uiState.update {
            it.copy(
                selectedYear = cal.get(Calendar.YEAR),
                selectedMonth = cal.get(Calendar.MONTH) + 1,
                isLoading = true
            )
        }
        loadMonthData()
    }

    /**
     * Check if we can navigate to next month (not past current month)
     */
    fun canGoNext(): Boolean {
        val now = Calendar.getInstance()
        val state = _uiState.value
        return !(state.selectedYear == now.get(Calendar.YEAR) &&
                state.selectedMonth == now.get(Calendar.MONTH) + 1)
    }

    /**
     * Load all analytics data (trend + month-specific + category trends)
     */
    private fun loadAllData() {
        loadMonthlyTrend()
        loadMonthData()
        loadCategoryTrends()
    }

    /**
     * Load the 6-month trend (doesn't change with month selection)
     */
    private fun loadMonthlyTrend() {
        viewModelScope.launch {
            try {
                val trend = expenseRepository.getMonthlyTotals(6)
                // Fill in missing months with zero values
                val filledTrend = fillMissingMonths(trend, 6)
                _uiState.update { it.copy(monthlyTrend = filledTrend) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load trend: ${e.message}") }
            }
        }
    }

    /**
     * Load data specific to the selected month
     */
    private fun loadMonthData() {
        val state = _uiState.value
        val year = state.selectedYear
        val month = state.selectedMonth
        val monthLabel = formatMonthLabel(year, month)

        _uiState.update { it.copy(selectedMonthLabel = monthLabel) }

        viewModelScope.launch {
            try {
                // Load all month data in parallel
                val categoryTotals = expenseRepository.getCategoryTotalsForMonth(year, month)
                val dailyTotals = expenseRepository.getDailyTotalsForMonth(year, month)
                val topSpenders = expenseRepository.getTopSpendersForMonth(year, month, 10)
                val paymentTypes = expenseRepository.getPaymentTypeBreakdownForMonth(year, month)
                val totalForMonth = expenseRepository.getTotalForMonth(year, month)

                // Compute month-over-month comparison
                val prevCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1) // 0-based
                    add(Calendar.MONTH, -1)
                }
                val prevYear = prevCal.get(Calendar.YEAR)
                val prevMonth = prevCal.get(Calendar.MONTH) + 1
                val prevTotal = expenseRepository.getTotalForMonth(prevYear, prevMonth)

                val percentChange = if (prevTotal > 0) {
                    ((totalForMonth - prevTotal) / prevTotal) * 100.0
                } else if (totalForMonth > 0) {
                    100.0 // went from 0 to something
                } else {
                    0.0
                }

                val monthComparison = MonthComparison(
                    currentMonthTotal = totalForMonth,
                    previousMonthTotal = prevTotal,
                    percentageChange = percentChange,
                    currentMonthLabel = monthLabel,
                    previousMonthLabel = formatMonthLabel(prevYear, prevMonth)
                )

                // Transaction count = sum of all category transaction counts
                val txCount = categoryTotals.sumOf { it.transactionCount }

                // Days in month for average
                val daysInMonth = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                }.getActualMaximum(Calendar.DAY_OF_MONTH)

                // If current month and not complete, use days elapsed
                val now = Calendar.getInstance()
                val daysForAvg = if (year == now.get(Calendar.YEAR) &&
                    month == now.get(Calendar.MONTH) + 1
                ) {
                    now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
                } else {
                    daysInMonth
                }

                val avgDaily = if (daysForAvg > 0) totalForMonth / daysForAvg else 0.0

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categoryBreakdown = categoryTotals,
                        dailySpending = dailyTotals,
                        topSpenders = topSpenders,
                        paymentTypeBreakdown = paymentTypes,
                        monthComparison = monthComparison,
                        totalForMonth = totalForMonth,
                        transactionCountForMonth = txCount,
                        avgDailySpend = avgDaily,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load analytics: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Load per-category monthly trends, compute CV, and filter to volatile categories.
     * Uses data-driven CV detection with a fallback to DEFAULT_VARIABLE_SPEND_CATEGORIES.
     */
    private fun loadCategoryTrends() {
        viewModelScope.launch {
            try {
                val monthsBack = 6
                val rawData = expenseRepository.getCategoryMonthlyTrend(monthsBack)

                // Group by categoryId
                val grouped = rawData.groupBy { it.categoryId }

                // Build month keys for all N months (for gap-filling)
                val allMonthKeys = buildMonthKeys(monthsBack)

                val trends = grouped.mapNotNull { (categoryId, entries) ->
                    val name = entries.first().categoryName
                    val color = entries.first().categoryColor

                    // Build a map of monthKey -> total for this category
                    val monthMap = entries.associate { it.monthKey to it.total }

                    // Fill missing months with 0
                    val filledData = allMonthKeys.map { key ->
                        MonthlyTotal(monthKey = key, total = monthMap[key] ?: 0.0)
                    }

                    // Count months with actual data (non-zero)
                    val activeMonths = filledData.count { it.total > 0 }

                    // Need at least 3 months with data for meaningful analysis.
                    // One-off or rare expenses (1-2 months) are NOT volatile — they're just sparse.
                    if (activeMonths < 3) {
                        null
                    } else {
                        buildCategoryTrend(categoryId, name, color, filledData)
                    }
                }

                // Sort by CV descending, take top 8
                val topTrends = trends
                    .sortedByDescending { it.coefficientOfVariation }
                    .take(8)

                _uiState.update { it.copy(categoryTrends = topTrends) }
            } catch (e: Exception) {
                // Don't fail the whole screen for this optional section
                _uiState.update { it.copy(categoryTrends = emptyList()) }
            }
        }
    }

    /**
     * Build a CategoryTrend from filled monthly data.
     * Returns null if the category doesn't qualify (CV too low and not in default list).
     */
    private fun buildCategoryTrend(
        categoryId: Long,
        name: String,
        color: String?,
        filledData: List<MonthlyTotal>
    ): CategoryTrend? {
        val allTotals = filledData.map { it.total }
        val overallMean = allTotals.average()

        // Minimum mean threshold: if average monthly spend is under KES 100,
        // the category isn't significant enough to track as a trend.
        val minimumMeanThreshold = 100.0
        if (overallMean < minimumMeanThreshold) return null

        // Compute CV only from non-zero months to avoid diluting variance with zeros.
        // This prevents sparse-but-consistent categories from appearing volatile.
        val nonZeroTotals = allTotals.filter { it > 0 }
        val activeMean = nonZeroTotals.average()
        val activeVariance = nonZeroTotals.map { (it - activeMean) * (it - activeMean) }.average()
        val activeStdDev = sqrt(activeVariance)
        val cv = if (activeMean > 0) (activeStdDev / activeMean) * 100.0 else 0.0

        val currentMonthTotal = filledData.lastOrNull()?.total ?: 0.0
        val isOverspending = activeMean > 0 && currentMonthTotal > activeMean + activeStdDev
        val overspendPct = if (activeMean > 0) ((currentMonthTotal - activeMean) / activeMean) * 100.0 else 0.0

        // Include if CV > 30% OR in default list with meaningful data
        val cvThreshold = 30.0
        val isInDefaultList = categoryId in DEFAULT_VARIABLE_SPEND_CATEGORIES
        val qualifies = cv > cvThreshold || (isInDefaultList && activeMean > 0)

        if (!qualifies) return null

        return CategoryTrend(
            categoryId = categoryId,
            categoryName = name,
            categoryColor = color,
            monthlyData = filledData,
            mean = activeMean,
            standardDeviation = activeStdDev,
            coefficientOfVariation = cv,
            currentMonthTotal = currentMonthTotal,
            isOverspending = isOverspending,
            overspendPercentage = overspendPct
        )
    }

    /**
     * Build a list of month keys (e.g. "2025-10", "2025-11", ...) for the last N months.
     */
    private fun buildMonthKeys(count: Int): List<String> {
        val keys = mutableListOf<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -(count - 1))
        val fmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        repeat(count) {
            keys.add(fmt.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        return keys
    }

    /**
     * Fill missing months with zero values so the chart has continuous data points.
     */
    private fun fillMissingMonths(data: List<MonthlyTotal>, count: Int): List<MonthlyTotal> {
        val result = mutableListOf<MonthlyTotal>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -(count - 1))
        val existing = data.associateBy { it.monthKey }
        val fmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        repeat(count) {
            val key = fmt.format(cal.time)
            result.add(existing[key] ?: MonthlyTotal(monthKey = key, total = 0.0))
            cal.add(Calendar.MONTH, 1)
        }
        return result
    }

    /**
     * Format a year/month pair as a human-readable label.
     * e.g. (2026, 3) -> "March 2026"
     */
    private fun formatMonthLabel(year: Int, month: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
        }
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }
}
