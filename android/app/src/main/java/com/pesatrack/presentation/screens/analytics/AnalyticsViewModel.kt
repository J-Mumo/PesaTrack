package com.pesatrack.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.database.dao.YearMonthTotal
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.BudgetRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.IncomeRepository
import com.pesatrack.domain.models.BudgetPeriod
import com.pesatrack.domain.models.CategoryTrend
import com.pesatrack.domain.models.DEFAULT_VARIABLE_SPEND_CATEGORIES
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.domain.models.YearComparison
import com.pesatrack.services.RecurringExpenseService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.sqrt

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val incomeRepository: IncomeRepository,
    private val recurringExpenseService: RecurringExpenseService,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val calendar = Calendar.getInstance()

    init {
        // Start with current month/year
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1 // 1-based
        _uiState.update {
            it.copy(
                selectedYear = currentYear,
                selectedMonth = currentMonth,
                selectedYearForYearly = currentYear
            )
        }
        loadAllData()
        loadBudgetStatus()
        loadRecurringBreakdown()
        loadWeeklySnapshot()
        loadInsightCards()
        loadBudgetBurnDown()

        // Track analytics viewed milestone and counter (fire-and-forget)
        viewModelScope.launch {
            appPreferences.recordFirstAnalyticsViewed()
            appPreferences.incrementAnalyticsViewsCount()
        }
    }

    // ==================== Top-level Tab Management ====================

    /**
     * Switch between Insights and Charts top-level tabs.
     */
    fun selectInsightsTab(tab: InsightsTab) {
        _uiState.update { it.copy(selectedInsightsTab = tab) }
    }

    // ==================== Charts Sub-Tab Management ====================

    /**
     * Switch between Monthly and Yearly charts sub-tabs.
     * Yearly data is loaded lazily on first access.
     */
    fun selectTab(tab: AnalyticsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == AnalyticsTab.YEARLY && _uiState.value.yearComparison == null) {
            loadYearlyData()
        }
    }

    // ==================== Insight Cards ====================

    /**
     * Load all insight card data: pace, quiet leaks, uncategorized percentage,
     * savings rate (Phase 4).
     */
    private fun loadInsightCards() {
        viewModelScope.launch {
            loadPaceCard()
            loadQuietLeaks()
            loadUncategorizedPercentage()
            loadSavingsRateCard()
            loadIncomeVsSpendChart()
        }
    }

    /**
     * Pace Card: daily_run_rate = spend_so_far / days_elapsed,
     * projected = run_rate × days_in_month, compare vs last month total.
     * Only show after 7th of month.
     */
    private suspend fun loadPaceCard() {
        try {
            val now = Calendar.getInstance()
            val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)

            if (dayOfMonth < 7) {
                _uiState.update { it.copy(showPaceCard = false, paceData = null) }
                return
            }

            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1
            val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)

            val spendSoFar = expenseRepository.getTotalForMonth(year, month)
            val dailyRunRate = spendSoFar / dayOfMonth
            val projected = dailyRunRate * daysInMonth

            // Last month total
            val prevCal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -1)
            }
            val prevYear = prevCal.get(Calendar.YEAR)
            val prevMonth = prevCal.get(Calendar.MONTH) + 1
            val lastMonthTotal = expenseRepository.getTotalForMonth(prevYear, prevMonth)

            val delta = projected - lastMonthTotal

            val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(now.time)
            val prevMonthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(prevCal.time)

            val paceData = PaceCardData(
                dailyRunRate = dailyRunRate,
                projected = projected,
                lastMonthTotal = lastMonthTotal,
                delta = delta,
                monthName = monthName,
                prevMonthName = prevMonthName
            )

            _uiState.update {
                it.copy(
                    paceData = paceData,
                    showPaceCard = true
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(showPaceCard = false, paceData = null) }
        }
    }

    /**
     * Quiet Leak: Find categories with ≥8 transactions in current month
     * AND average transaction amount ≤ KES 300.
     */
    private suspend fun loadQuietLeaks() {
        try {
            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1

            val categoryTotals = expenseRepository.getCategoryTotalsForMonth(year, month)

            val leaks = categoryTotals
                .filter { it.transactionCount >= 8 }
                .filter { it.categoryId != null }
                .filter { (it.total / it.transactionCount) <= 300.0 }
                .map { cat ->
                    QuietLeakData(
                        categoryName = cat.categoryName,
                        transactionCount = cat.transactionCount,
                        total = cat.total,
                        categoryId = cat.categoryId?.toInt() ?: 0
                    )
                }
                .sortedByDescending { it.total }

            _uiState.update {
                it.copy(
                    quietLeaks = leaks,
                    showQuietLeakCard = leaks.isNotEmpty()
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(quietLeaks = emptyList(), showQuietLeakCard = false) }
        }
    }

    /**
     * Uncategorized %: Calculate percentage of total spend that is uncategorized.
     */
    private suspend fun loadUncategorizedPercentage() {
        try {
            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1

            val categoryTotals = expenseRepository.getCategoryTotalsForMonth(year, month)
            val totalSpend = categoryTotals.sumOf { it.total }

            if (totalSpend <= 0) {
                _uiState.update { it.copy(uncategorizedPercentage = 0.0, showCategorizationNudge = false) }
                return
            }

            // Uncategorized = categoryId is null OR categoryName contains "Uncategorized"
            val uncategorizedTotal = categoryTotals
                .filter { it.categoryId == null || it.categoryName.contains("Uncategorized", ignoreCase = true) }
                .sumOf { it.total }

            val pct = (uncategorizedTotal / totalSpend) * 100.0

            _uiState.update {
                it.copy(
                    uncategorizedPercentage = pct,
                    showCategorizationNudge = pct > 15.0
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(uncategorizedPercentage = 0.0, showCategorizationNudge = false) }
        }
    }

    /**
     * Savings rate card (Phase 4): shows current month + 3-month rolling
     * average. Only surfaced when we have honest income data (detected or
     * user-set), never inferred.
     */
    private suspend fun loadSavingsRateCard() {
        try {
            val now = Calendar.getInstance()
            val ratesByMonth = (0..2).map { offset ->
                val cal = (now.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
                computeSavingsRate(cal)
            }
            val current = ratesByMonth.first()
            if (current == null) {
                _uiState.update { it.copy(savingsRate = null, showSavingsRateCard = false) }
                return
            }
            val validRates = ratesByMonth.mapNotNull { it?.ratePct }
            val rolling = if (validRates.isNotEmpty()) validRates.average() else current.ratePct
            val data = SavingsRateData(
                currentMonthPct = current.ratePct,
                rollingThreeMonthPct = rolling,
                currentMonthIncome = current.income,
                currentMonthSpend = current.spend,
                effectiveIncomeSource = current.source
            )
            _uiState.update { it.copy(savingsRate = data, showSavingsRateCard = true) }
        } catch (_: Exception) {
            _uiState.update { it.copy(savingsRate = null, showSavingsRateCard = false) }
        }
    }

    private data class SavingsRateRow(
        val ratePct: Double,
        val income: Double,
        val spend: Double,
        val source: EffectiveIncomeSource,
    )

    private suspend fun computeSavingsRate(cal: Calendar): SavingsRateRow? {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val key = String.format(Locale.US, "%04d-%02d", year, month)
        val effective = incomeRepository.effectiveMonthlyIncome(key)
        val income = effective.value ?: return null
        if (income <= 0.0) return null
        val spend = expenseRepository.getTotalForMonth(year, month)
        val rate = (((income - spend) / income) * 100.0).coerceIn(-100.0, 100.0)
        return SavingsRateRow(rate, income, spend, effective.source)
    }

    /**
     * 12-month income-vs-spend overlay chart (Phase 4).
     * Only published when at least one month has detected income — otherwise
     * the chart would just be a duplicate of the existing monthly spend trend.
     */
    private suspend fun loadIncomeVsSpendChart() {
        try {
            val now = Calendar.getInstance()
            val points = (0..11).reversed().map { offset ->
                val cal = (now.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val key = String.format(Locale.US, "%04d-%02d", year, month)
                val (startMs, endMs) = monthBounds(cal)
                val income = incomeRepository.sumForRange(startMs, endMs, includeTransfers = false)
                val spend = expenseRepository.getTotalForMonth(year, month)
                IncomeSpendPoint(monthKey = key, income = income, spend = spend)
            }
            val anyIncome = points.any { it.income > 0.0 }
            _uiState.update {
                it.copy(incomeVsSpend = if (anyIncome) points else emptyList())
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(incomeVsSpend = emptyList()) }
        }
    }

    /** Inclusive-exclusive epoch bounds for the calendar's month. */
    private fun monthBounds(cal: Calendar): Pair<Long, Long> {
        val start = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return start.timeInMillis to end.timeInMillis
    }

    // ==================== Monthly Navigation ====================

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
                isLoading = true,
                recipientSearchQuery = "",
                recipientSearchResults = null,
                recipientSearchTotal = 0.0,
                recipientSearchLoading = false
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
                isLoading = true,
                recipientSearchQuery = "",
                recipientSearchResults = null,
                recipientSearchTotal = 0.0,
                recipientSearchLoading = false
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

    // ==================== Yearly Navigation ====================

    /**
     * Navigate to the previous year
     */
    fun previousYear() {
        _uiState.update {
            it.copy(
                selectedYearForYearly = it.selectedYearForYearly - 1,
                yearlyIsLoading = true,
                yearComparison = null, // Force reload
                recipientSearchQuery = "",
                yearlyRecipientSearchResults = null,
                yearlyRecipientSearchTotal = 0.0,
                recipientSearchLoading = false
            )
        }
        loadYearlyData()
    }

    /**
     * Navigate to the next year (capped at current year)
     */
    fun nextYear() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (_uiState.value.selectedYearForYearly >= currentYear) return
        _uiState.update {
            it.copy(
                selectedYearForYearly = it.selectedYearForYearly + 1,
                yearlyIsLoading = true,
                yearComparison = null, // Force reload
                recipientSearchQuery = "",
                yearlyRecipientSearchResults = null,
                yearlyRecipientSearchTotal = 0.0,
                recipientSearchLoading = false
            )
        }
        loadYearlyData()
    }

    /**
     * Check if we can navigate to next year (not past current year)
     */
    fun canGoNextYear(): Boolean {
        return _uiState.value.selectedYearForYearly < Calendar.getInstance().get(Calendar.YEAR)
    }

    // ==================== Monthly Data Loading ====================

    /**
     * Load all analytics data (trend + month-specific + category trends)
     */
    private fun loadAllData() {
        loadMonthlyTrend()
        loadMonthData()
        loadCategoryTrends()
    }

    /**
     * Load weekly snapshot data: this week total, last week total, WoW change,
     * top category this week. Only relevant for the current month view.
     */
    private fun loadWeeklySnapshot() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val dayMs = 24L * 60 * 60 * 1000

                // "This week" = last 7 days (today inclusive, rolling window)
                val thisWeekStart = now - (7 * dayMs)
                val thisWeekEnd = now

                // "Last week" = 7 days before that (days 8–14 ago)
                val lastWeekStart = now - (14 * dayMs)
                val lastWeekEnd = thisWeekStart

                val thisWeekTotal = expenseRepository.getTotalInRange(thisWeekStart, thisWeekEnd)
                val lastWeekTotal = expenseRepository.getTotalInRange(lastWeekStart, lastWeekEnd)

                val wowChange = if (lastWeekTotal > 0) {
                    ((thisWeekTotal - lastWeekTotal) / lastWeekTotal) * 100.0
                } else if (thisWeekTotal > 0) 100.0 else 0.0

                val topCategory = expenseRepository.getTopCategoryInRange(thisWeekStart, thisWeekEnd)

                // Build date label (e.g. "Apr 25 – May 1")
                val dateFmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                val startLabel = dateFmt.format(java.util.Date(thisWeekStart))
                val endLabel = dateFmt.format(java.util.Date(thisWeekEnd))
                val weekLabel = "$startLabel – $endLabel"

                _uiState.update {
                    it.copy(
                        weeklyTotal = thisWeekTotal,
                        previousWeekTotal = lastWeekTotal,
                        weekOverWeekChange = wowChange,
                        topCategoryThisWeek = topCategory?.categoryName,
                        topCategoryThisWeekAmount = topCategory?.total ?: 0.0,
                        weekDateLabel = weekLabel
                    )
                }
            } catch (_: Exception) {
                // Non-critical — leave defaults
            }
        }
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

    // ==================== Yearly Data Loading ====================

    /**
     * Load all data for the yearly analytics tab.
     */
    private fun loadYearlyData() {
        val year = _uiState.value.selectedYearForYearly

        _uiState.update { it.copy(yearlyIsLoading = true) }

        viewModelScope.launch {
            try {
                val annualTotal = expenseRepository.getAnnualTotal(year)
                val prevTotal = expenseRepository.getAnnualTotal(year - 1)
                val currentMonths = expenseRepository.getMonthlyTotalsForYear(year)
                val prevMonths = expenseRepository.getMonthlyTotalsForYear(year - 1)
                val categories = expenseRepository.getCategoryTotalsForYear(year)
                val topSpenders = expenseRepository.getTopSpendersForYear(year, 10)
                val paymentTypes = expenseRepository.getPaymentTypeBreakdownForYear(year)

                // YoY percentage change
                val pctChange = if (prevTotal > 0) {
                    ((annualTotal - prevTotal) / prevTotal) * 100.0
                } else if (annualTotal > 0) 100.0 else 0.0

                val yearComparison = YearComparison(
                    currentYearTotal = annualTotal,
                    previousYearTotal = prevTotal,
                    percentageChange = pctChange,
                    currentYearLabel = year.toString(),
                    previousYearLabel = (year - 1).toString()
                )

                val txCount = categories.sumOf { it.transactionCount }

                // Months elapsed: if current year, use current month count; otherwise 12
                val now = Calendar.getInstance()
                val monthsElapsed = if (year == now.get(Calendar.YEAR)) {
                    now.get(Calendar.MONTH) + 1 // 1-based
                } else 12
                val avgMonthly = if (monthsElapsed > 0) annualTotal / monthsElapsed else 0.0

                // Fill missing months (1-12) with 0 for both years
                val filledCurrent = fillYearMonths(currentMonths)
                val filledPrev = fillYearMonths(prevMonths)

                _uiState.update {
                    it.copy(
                        yearlyIsLoading = false,
                        yearComparison = yearComparison,
                        yearlyTotalForYear = annualTotal,
                        yearlyTransactionCount = txCount,
                        yearlyAvgMonthlySpend = avgMonthly,
                        currentYearMonthlyTotals = filledCurrent,
                        previousYearMonthlyTotals = filledPrev,
                        yearlyCategoryBreakdown = categories,
                        yearlyTopSpenders = topSpenders,
                        yearlyPaymentTypeBreakdown = paymentTypes,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        yearlyIsLoading = false,
                        error = "Failed to load yearly analytics: ${e.message}"
                    )
                }
            }
        }
    }

    // ==================== Recipient Search ====================

    /**
     * Update the recipient search query and trigger a search.
     * Debounced via the UI layer (300ms delay recommended).
     */
    fun searchRecipient(query: String) {
        _uiState.update { it.copy(recipientSearchQuery = query) }

        if (query.isBlank()) {
            // Clear search results — revert to default top-10 view
            _uiState.update {
                it.copy(
                    recipientSearchResults = null,
                    recipientSearchTotal = 0.0,
                    yearlyRecipientSearchResults = null,
                    yearlyRecipientSearchTotal = 0.0,
                    recipientSearchLoading = false
                )
            }
            return
        }

        _uiState.update { it.copy(recipientSearchLoading = true) }

        viewModelScope.launch {
            try {
                val state = _uiState.value

                // Search for the active tab
                when (state.selectedTab) {
                    AnalyticsTab.MONTHLY -> {
                        val results = expenseRepository.searchRecipientSpendingForMonth(
                            query, state.selectedYear, state.selectedMonth
                        )
                        val total = results.sumOf { it.total }
                        _uiState.update {
                            it.copy(
                                recipientSearchResults = results,
                                recipientSearchTotal = total,
                                recipientSearchLoading = false
                            )
                        }
                    }
                    AnalyticsTab.YEARLY -> {
                        val results = expenseRepository.searchRecipientSpendingForYear(
                            query, state.selectedYearForYearly
                        )
                        val total = results.sumOf { it.total }
                        _uiState.update {
                            it.copy(
                                yearlyRecipientSearchResults = results,
                                yearlyRecipientSearchTotal = total,
                                recipientSearchLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        recipientSearchResults = emptyList(),
                        recipientSearchTotal = 0.0,
                        yearlyRecipientSearchResults = emptyList(),
                        yearlyRecipientSearchTotal = 0.0,
                        recipientSearchLoading = false
                    )
                }
            }
        }
    }

    /**
     * Clear the recipient search (called when closing the search bar)
     */
    fun clearRecipientSearch() {
        _uiState.update {
            it.copy(
                recipientSearchQuery = "",
                recipientSearchResults = null,
                recipientSearchTotal = 0.0,
                yearlyRecipientSearchResults = null,
                yearlyRecipientSearchTotal = 0.0,
                recipientSearchLoading = false
            )
        }
    }

    // ==================== Recurring Expense Detection ====================

    /**
     * Load recurring vs one-time spending breakdown for the selected month.
     * Uses [RecurringExpenseService] to detect patterns and compute the split.
     */
    private fun loadRecurringBreakdown() {
        viewModelScope.launch {
            try {
                val summary = recurringExpenseService.getRecurringExpenses()

                if (summary.recurringExpenses.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            recurringTotal = 0.0,
                            oneTimeTotal = 0.0,
                            topRecurringNames = "",
                            hasRecurringData = false
                        )
                    }
                    return@launch
                }

                // Sum monthly-equivalent recurring amounts
                val recurringMonthly = summary.totalMonthlyRecurring

                // One-time = total for month - recurring
                val state = _uiState.value
                val totalForMonth = state.totalForMonth
                val oneTime = (totalForMonth - recurringMonthly).coerceAtLeast(0.0)

                // Top 3 recurring expense names
                val topNames = summary.recurringExpenses
                    .take(3)
                    .joinToString(", ") { it.recipientDisplayName }

                _uiState.update {
                    it.copy(
                        recurringTotal = recurringMonthly,
                        oneTimeTotal = oneTime,
                        topRecurringNames = topNames,
                        hasRecurringData = true
                    )
                }
            } catch (_: Exception) {
                // Non-critical — leave default (hidden)
                _uiState.update { it.copy(hasRecurringData = false) }
            }
        }
    }

    // ==================== Budget Integration ====================

    /**
     * Check whether the user has any active budgets.
     * Used to display a "Set up budgets" banner when no budgets exist.
     */
    private fun loadBudgetStatus() {
        viewModelScope.launch {
            try {
                val hasBudgets = budgetRepository.hasActiveBudgets()
                _uiState.update { it.copy(hasActiveBudgets = hasBudgets) }
            } catch (_: Exception) {
                // Non-critical — leave default (false)
            }
        }
    }

    // ==================== Budget Burn-Down (v1.3) ====================

    /**
     * Compute which budget categories will exhaust ≥3 days before month end
     * based on current daily spend rate.
     */
    private fun loadBudgetBurnDown() {
        viewModelScope.launch {
            try {
                val monthStartDay = appPreferences.monthStartDay.first()
                val now = Calendar.getInstance()
                val today = now.get(Calendar.DAY_OF_MONTH)

                // Calculate budget period start and end based on monthStartDay
                val periodStart = Calendar.getInstance().apply {
                    if (today >= monthStartDay) {
                        // Period started this calendar month
                        set(Calendar.DAY_OF_MONTH, monthStartDay)
                    } else {
                        // Period started last calendar month
                        add(Calendar.MONTH, -1)
                        set(Calendar.DAY_OF_MONTH, monthStartDay)
                    }
                }
                val periodEnd = (periodStart.clone() as Calendar).apply {
                    add(Calendar.MONTH, 1)
                }

                val totalDaysInPeriod = ((periodEnd.timeInMillis - periodStart.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
                val daysElapsed = ((now.timeInMillis - periodStart.timeInMillis) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
                val daysRemaining = totalDaysInPeriod - daysElapsed

                if (daysElapsed < 5) {
                    _uiState.update { it.copy(budgetBurnDowns = emptyList(), showBudgetBurnDown = false) }
                    return@launch
                }

                val budgetProgressList = budgetRepository.getBudgetProgressList()
                if (budgetProgressList.isEmpty()) {
                    _uiState.update { it.copy(budgetBurnDowns = emptyList(), showBudgetBurnDown = false) }
                    return@launch
                }

                val burnDowns = budgetProgressList.mapNotNull { progress ->
                    val budget = progress.budget
                    val spent = progress.spent
                    if (budget.amount <= 0 || spent <= 0) return@mapNotNull null

                    val dailyRate = spent.toDouble() / daysElapsed
                    if (dailyRate <= 0) return@mapNotNull null

                    // How many days from period start until budget is exhausted
                    val daysUntilExhaustion = (budget.amount / dailyRate).toInt()
                    val daysEarly = totalDaysInPeriod - daysUntilExhaustion

                    // Only show if ≥3 days early
                    if (daysEarly < 3) return@mapNotNull null

                    // Calculate the calendar date of exhaustion for display
                    val exhaustionCal = (periodStart.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_MONTH, daysUntilExhaustion)
                    }
                    val exhaustionDay = exhaustionCal.get(Calendar.DAY_OF_MONTH)

                    BudgetBurnDownData(
                        categoryName = budget.categoryName ?: "Total Spending",
                        exhaustionDay = exhaustionDay,
                        daysEarly = daysEarly,
                        categoryId = budget.categoryId?.toInt() ?: 0
                    )
                }.sortedByDescending { it.daysEarly }

                _uiState.update {
                    it.copy(
                        budgetBurnDowns = burnDowns,
                        showBudgetBurnDown = burnDowns.isNotEmpty()
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(budgetBurnDowns = emptyList(), showBudgetBurnDown = false) }
            }
        }
    }

    // ==================== Helper Functions ====================

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
     * Ensure all 12 months (1-12) are represented for yearly chart.
     * Gap-fills missing months with 0.
     */
    private fun fillYearMonths(data: List<YearMonthTotal>): List<YearMonthTotal> {
        val map = data.associateBy { it.monthNumber }
        return (1..12).map { m -> map[m] ?: YearMonthTotal(monthNumber = m, total = 0.0) }
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
