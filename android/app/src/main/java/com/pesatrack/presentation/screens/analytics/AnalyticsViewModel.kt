package com.pesatrack.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.repository.ExpenseRepository
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
     * Load all analytics data (trend + month-specific)
     */
    private fun loadAllData() {
        loadMonthlyTrend()
        loadMonthData()
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
