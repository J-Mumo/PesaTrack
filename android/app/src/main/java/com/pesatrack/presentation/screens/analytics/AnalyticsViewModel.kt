package com.pesatrack.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.pesatrack.data.export.CategoryMonthGridCsvExporter
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
import com.pesatrack.utils.MonthPeriod
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

    init {
        viewModelScope.launch {
            // Load monthStartDay first so "Monthly" tab buckets align with the
            // user's budget cycle (e.g. salary on the 25th) instead of the
            // calendar 1st-to-last.
            incomeRepository.refreshMonthStartDay()

            val (currentStart, _) = MonthPeriod.currentRange(incomeRepository.monthStartDay)
            val anchor = Calendar.getInstance().apply { timeInMillis = currentStart }
            val currentYear = anchor.get(Calendar.YEAR)
            val currentMonth = anchor.get(Calendar.MONTH) + 1 // period named after its start
            val yearForYearly = Calendar.getInstance().get(Calendar.YEAR)
            _uiState.update {
                it.copy(
                    selectedYear = currentYear,
                    selectedMonth = currentMonth,
                    selectedYearForYearly = yearForYearly
                )
            }
            loadAllData()
            loadBudgetStatus()
            loadRecurringBreakdown()
            loadWeeklySnapshot()
            loadInsightCards()
            loadBudgetBurnDown()

            // Track analytics viewed milestone and counter
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

    // ==================== Yearly → Grid Sub-View ====================

    /**
     * Switch the Yearly tab between the existing "Overview" cards and the
     * new Category × Month "Grid". Grid data is loaded lazily on first
     * open and again whenever the year changes.
     */
    fun selectYearlyView(view: YearlyView) {
        _uiState.update { it.copy(yearlySelectedView = view) }
        if (view == YearlyView.GRID && _uiState.value.yearlyGrid == null) {
            loadYearlyGrid()
        }
    }

    /**
     * Expand or collapse a group row in the Grid so its sub-categories show
     * beneath it. Purely a UI-state toggle — no reload needed.
     */
    fun toggleYearlyGridGroup(groupId: Long) {
        _uiState.update { state ->
            val next = state.yearlyGridExpandedGroups.toMutableSet().apply {
                if (contains(groupId)) remove(groupId) else add(groupId)
            }
            state.copy(yearlyGridExpandedGroups = next)
        }
    }

    /**
     * Toggle the "Include fees (606)" filter on the Grid and reload. Off by
     * default so the grid reflects "money the user chose to spend" per the
     * AGENTS.md honest-numbers principle.
     */
    fun toggleYearlyGridIncludeFees() {
        _uiState.update { it.copy(yearlyGridIncludeFees = !it.yearlyGridIncludeFees) }
        loadYearlyGrid()
    }

    /**
     * Write the currently-loaded Grid as a CSV to the app cache dir under
     * `exports/` and publish the resulting File to state so the Screen can
     * fire the share sheet. Silently no-ops when the grid is null or empty.
     */
    fun exportYearlyGridAsCsv(context: Context) {
        val grid = _uiState.value.yearlyGrid
        if (grid == null || grid.rows.isEmpty()) return
        viewModelScope.launch {
            try {
                val csv = CategoryMonthGridCsvExporter.buildCsv(grid)
                val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                val file = java.io.File(dir, "pesatrack-category-grid-${grid.year}.csv")
                file.writeText(csv, Charsets.UTF_8)
                _uiState.update {
                    it.copy(
                        pendingGridExportFile = file,
                        yearlyGridExportError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(yearlyGridExportError = "Export failed: ${e.message}")
                }
            }
        }
    }

    /** Called by the Screen after the share sheet has been launched. */
    fun consumeGridExport() {
        _uiState.update {
            it.copy(pendingGridExportFile = null, yearlyGridExportError = null)
        }
    }

    private fun loadYearlyGrid() {
        // Guard against a race with `init { … selectedYearForYearly = currentYear }`:
        // Home's "Trend by group → View all" fires this via `selectYearlyView(GRID)`
        // through a LaunchedEffect immediately on composition, which can beat the
        // async init coroutine and read the default `0`. That produced an empty
        // grid on first navigation; a year-change round-trip masked it because
        // the next-year math ran against the (by-then-populated) real year.
        val stateYear = _uiState.value.selectedYearForYearly
        val year = if (stateYear > 0) stateYear else Calendar.getInstance().get(Calendar.YEAR)
        if (stateYear == 0) {
            _uiState.update { it.copy(selectedYearForYearly = year) }
        }
        val includeFees = _uiState.value.yearlyGridIncludeFees
        _uiState.update { it.copy(yearlyGridLoading = true) }
        viewModelScope.launch {
            try {
                val grid = expenseRepository.getCategoryMonthGridForYear(year, includeFees)
                _uiState.update {
                    it.copy(
                        yearlyGridLoading = false,
                        yearlyGrid = grid,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        yearlyGridLoading = false,
                        error = "Failed to load yearly grid: ${e.message}"
                    )
                }
            }
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
     *
     * Iterates by **budget period** (honouring `monthStartDay`) so the rate
     * matches what the user sees on the Budget and Income screens.
     */
    private suspend fun loadSavingsRateCard() {
        try {
            incomeRepository.refreshMonthStartDay()
            val (currentStart, _) = incomeRepository.currentMonthBounds()
            val anchor = Calendar.getInstance().apply { timeInMillis = currentStart }
            val ratesByMonth = (0..2).map { offset ->
                val cal = (anchor.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
                computeSavingsRate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
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
                currentMonthSavings = current.savings,
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
        val savings: Double,
        val source: EffectiveIncomeSource,
    )

    private suspend fun computeSavingsRate(year: Int, month1Based: Int): SavingsRateRow? {
        val effective = incomeRepository.effectiveIncomeForMonth(year, month1Based)
        val income = effective.value ?: return null
        if (income <= 0.0) return null
        val (start, end) = com.pesatrack.utils.MonthPeriod.rangeForPeriodStart(
            year, month1Based, incomeRepository.monthStartDay
        )
        // Savings = money deliberately moved into the Investment & Savings
        // group (18), not (income - spend). See SavingsRateData KDoc.
        val savings = expenseRepository.getInvestmentInRange(start, end)
        val rate = ((savings / income) * 100.0).coerceIn(0.0, 100.0)
        return SavingsRateRow(rate, income, savings, effective.source)
    }

    /**
     * 12-month income-vs-spend overlay chart (Phase 4).
     * Only published when at least one month has detected income — otherwise
     * the chart would just be a duplicate of the existing monthly spend trend.
     *
     * Buckets follow the user's `monthStartDay` so they line up with the
     * savings-rate card and the Income / Budget screens.
     */
    private suspend fun loadIncomeVsSpendChart() {
        try {
            incomeRepository.refreshMonthStartDay()
            val (currentStart, _) = incomeRepository.currentMonthBounds()
            val anchor = Calendar.getInstance().apply { timeInMillis = currentStart }
            val points = (0..11).reversed().map { offset ->
                val cal = (anchor.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val key = String.format(Locale.US, "%04d-%02d", year, month)
                val (startMs, endMs) = com.pesatrack.utils.MonthPeriod.rangeForPeriodStart(
                    year, month, incomeRepository.monthStartDay
                )
                val income = incomeRepository.sumForRange(startMs, endMs, includeTransfers = false)
                val spend = expenseRepository.getSpendingInRange(startMs, endMs)
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

    // ==================== Monthly Navigation ====================

    /**
     * Navigate to the previous budget-month period.
     *
     * Periods are named after their start year/month, so stepping back one
     * calendar month always lands on the prior period — e.g. with
     * `monthStartDay = 25`, "Mar 25 – Apr 24" → "Feb 25 – Mar 24".
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
     * Navigate to the next budget-month period (capped at the period containing "now").
     */
    fun nextMonth() {
        val (currentStart, _) = MonthPeriod.currentRange(incomeRepository.monthStartDay)
        val curCal = Calendar.getInstance().apply { timeInMillis = currentStart }
        val state = _uiState.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, state.selectedYear)
            set(Calendar.MONTH, state.selectedMonth - 1)
            add(Calendar.MONTH, 1)
        }
        // Don't go past the current period
        if (cal.get(Calendar.YEAR) > curCal.get(Calendar.YEAR) ||
            (cal.get(Calendar.YEAR) == curCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.MONTH) > curCal.get(Calendar.MONTH))
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
     * Check if we can navigate to the next period (not past the current one).
     */
    fun canGoNext(): Boolean {
        val (currentStart, _) = MonthPeriod.currentRange(incomeRepository.monthStartDay)
        val cur = Calendar.getInstance().apply { timeInMillis = currentStart }
        val state = _uiState.value
        return !(state.selectedYear == cur.get(Calendar.YEAR) &&
                state.selectedMonth == cur.get(Calendar.MONTH) + 1)
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
                yearlyGrid = null,      // Force grid reload for the new year
                yearlyGridExpandedGroups = emptySet(),
                recipientSearchQuery = "",
                yearlyRecipientSearchResults = null,
                yearlyRecipientSearchTotal = 0.0,
                recipientSearchLoading = false
            )
        }
        loadYearlyData()
        if (_uiState.value.yearlySelectedView == YearlyView.GRID) {
            loadYearlyGrid()
        }
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
                yearlyGrid = null,      // Force grid reload for the new year
                yearlyGridExpandedGroups = emptySet(),
                recipientSearchQuery = "",
                yearlyRecipientSearchResults = null,
                yearlyRecipientSearchTotal = 0.0,
                recipientSearchLoading = false
            )
        }
        loadYearlyData()
        if (_uiState.value.yearlySelectedView == YearlyView.GRID) {
            loadYearlyGrid()
        }
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
     * Load the 6-month trend.
     *
     * Buckets follow the user's `monthStartDay` so each point lines up with
     * the corresponding Budget / Income period rather than a calendar month.
     * Period keys remain `"yyyy-MM"` (named after the period's start year/month)
     * so the chart's X-axis label code is unchanged.
     */
    private fun loadMonthlyTrend() {
        viewModelScope.launch {
            try {
                val msd = incomeRepository.monthStartDay
                val (currentStart, _) = MonthPeriod.currentRange(msd)
                val anchor = Calendar.getInstance().apply { timeInMillis = currentStart }
                val trend = (5 downTo 0).map { offset ->
                    val cal = (anchor.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH) + 1
                    val key = String.format(Locale.US, "%04d-%02d", year, month)
                    val (s, e) = MonthPeriod.rangeForPeriodStart(year, month, msd)
                    MonthlyTotal(monthKey = key, total = expenseRepository.getSpendingInRange(s, e))
                }
                _uiState.update { it.copy(monthlyTrend = trend) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load trend: ${e.message}") }
            }
        }
    }

    /**
     * Load data specific to the selected period (uses `monthStartDay`-aware bounds).
     */
    private fun loadMonthData() {
        val state = _uiState.value
        val year = state.selectedYear
        val month = state.selectedMonth
        val msd = incomeRepository.monthStartDay
        val (start, end) = MonthPeriod.rangeForPeriodStart(year, month, msd)
        val monthLabel = MonthPeriod.labelForRange(start, end, msd)

        _uiState.update { it.copy(selectedMonthLabel = monthLabel) }

        viewModelScope.launch {
            try {
                // Load all period data in parallel-ish (sequential calls — each is fast)
                val categoryTotals = expenseRepository.getCategoryTotalsInRange(start, end)
                val topSpenders = expenseRepository.getTopSpendersInRange(start, end, 10)
                val paymentTypes = expenseRepository.getPaymentTypeBreakdownInRange(start, end)
                val totalForMonth = expenseRepository.getSpendingInRange(start, end)

                // Previous period (period named after start year/month — step back 1 calendar month)
                val prevCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1) // 0-based
                    add(Calendar.MONTH, -1)
                }
                val prevYear = prevCal.get(Calendar.YEAR)
                val prevMonth = prevCal.get(Calendar.MONTH) + 1
                val (prevStart, prevEnd) = MonthPeriod.rangeForPeriodStart(prevYear, prevMonth, msd)
                val prevTotal = expenseRepository.getSpendingInRange(prevStart, prevEnd)
                val prevLabel = MonthPeriod.labelForRange(prevStart, prevEnd, msd)

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
                    previousMonthLabel = prevLabel
                )

                // Transaction count = sum of all category transaction counts
                val txCount = categoryTotals.sumOf { it.transactionCount }

                // Days in / days elapsed for the average — anchored on period bounds.
                val dayMs = 24L * 60 * 60 * 1000
                val totalDaysInPeriod = ((end - start) / dayMs).toInt().coerceAtLeast(1)
                val nowMs = System.currentTimeMillis()
                val daysForAvg = if (nowMs in start until end) {
                    (((nowMs - start) / dayMs).toInt() + 1).coerceAtLeast(1)
                } else {
                    totalDaysInPeriod
                }
                val avgDaily = totalForMonth / daysForAvg

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
     * Load per-category period trends, compute CV, and filter to volatile categories.
     *
     * Iterates the last 6 budget-month periods so the bucket boundaries match
     * the Monthly tab's selector and trend chart instead of running on calendar
     * months.
     */
    private fun loadCategoryTrends() {
        viewModelScope.launch {
            try {
                val msd = incomeRepository.monthStartDay
                val monthsBack = 6
                val (currentStart, _) = MonthPeriod.currentRange(msd)
                val anchor = Calendar.getInstance().apply { timeInMillis = currentStart }

                // Build (key, start, end) for each of the last N periods, oldest first.
                val periods = (monthsBack - 1 downTo 0).map { offset ->
                    val cal = (anchor.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH) + 1
                    val key = String.format(Locale.US, "%04d-%02d", year, month)
                    val (s, e) = MonthPeriod.rangeForPeriodStart(year, month, msd)
                    Triple(key, s, e)
                }
                val allMonthKeys = periods.map { it.first }

                // Flatten into per-(category, period) totals; mirrors the shape the
                // old SQL `GROUP BY categoryId, monthKey` query returned.
                val rawData = mutableListOf<com.pesatrack.data.local.database.dao.CategoryMonthlyTotal>()
                for ((key, s, e) in periods) {
                    val cats = expenseRepository.getCategoryTotalsInRange(s, e)
                    for (c in cats) {
                        val catId = c.categoryId ?: continue
                        rawData.add(
                            com.pesatrack.data.local.database.dao.CategoryMonthlyTotal(
                                categoryId = catId,
                                categoryName = c.categoryName,
                                categoryColor = c.categoryColor,
                                monthKey = key,
                                total = c.total
                            )
                        )
                    }
                }

                val grouped = rawData.groupBy { it.categoryId }

                val trends = grouped.mapNotNull { (categoryId, entries) ->
                    val name = entries.first().categoryName
                    val color = entries.first().categoryColor

                    val monthMap = entries.associate { it.monthKey to it.total }
                    val filledData = allMonthKeys.map { key ->
                        MonthlyTotal(monthKey = key, total = monthMap[key] ?: 0.0)
                    }

                    val activeMonths = filledData.count { it.total > 0 }
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
            } catch (_: Exception) {
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
        // Same init-race guard as loadYearlyGrid — deep-linking straight to
        // Yearly (e.g. from Home) can beat the async init that writes
        // `selectedYearForYearly = currentYear`.
        val stateYear = _uiState.value.selectedYearForYearly
        val year = if (stateYear > 0) stateYear else Calendar.getInstance().get(Calendar.YEAR)
        if (stateYear == 0) {
            _uiState.update { it.copy(selectedYearForYearly = year) }
        }

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
                        val msd = incomeRepository.monthStartDay
                        val (start, end) = MonthPeriod.rangeForPeriodStart(
                            state.selectedYear, state.selectedMonth, msd
                        )
                        val results = expenseRepository.searchRecipientSpendingInRange(
                            query, start, end
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
     * Ensure all 12 months (1-12) are represented for yearly chart.
     * Gap-fills missing months with 0.
     */
    private fun fillYearMonths(data: List<YearMonthTotal>): List<YearMonthTotal> {
        val map = data.associateBy { it.monthNumber }
        return (1..12).map { m -> map[m] ?: YearMonthTotal(monthNumber = m, total = 0.0) }
    }
}
