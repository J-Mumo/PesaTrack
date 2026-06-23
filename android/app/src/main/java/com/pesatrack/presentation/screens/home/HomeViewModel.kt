package com.pesatrack.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.BudgetRepository
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.ExpenseRepository
import com.pesatrack.data.repository.IncomeRepository
import com.pesatrack.domain.models.Category
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.presentation.screens.expenses.ExpenseWithCategory
import com.pesatrack.utils.UsageSummaryGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val incomeRepository: IncomeRepository,
    private val appPreferences: AppPreferences,
    private val usageSummaryGenerator: UsageSummaryGenerator
) : ViewModel() {

    companion object {
        private const val REVIEW_MIN_INSTALL_AGE_DAYS = 14L
        private const val REVIEW_MIN_CATEGORIZED_EXPENSES = 20
        private const val REVIEW_MIN_QUALIFIED_SESSIONS = 10
        private const val REVIEW_COOLDOWN_DAYS = 90L
        private const val REVIEW_MAX_PROMPT_COUNT = 2
        private const val STRUCTURED_MIN_QUALIFIED_SESSIONS = 5
        // Tightened thresholds: most uninstalls happen within the first 24h, so we need
        // to capture friction feedback while users are still in the app, not days later.
        private const val LOW_ENGAGEMENT_SMS_GRACE_MINUTES = 30L
        private const val LOW_ENGAGEMENT_FIRST_VALUE_GRACE_MINUTES = 15L
        private const val LOW_ENGAGEMENT_RETURN_CHECK_DAYS = 3L
    }
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var categoriesMap: Map<Long, Category> = emptyMap()
    private var lastKnownSmsPermissionGranted: Boolean = false
    
    init {
        initializeData()
        loadCategories()
        loadData()
        loadTrendData()
        loadBudgetData()
        loadIncomeData()
        loadSmsBannerState()
        checkReviewPromptEligibility()
        checkStructuredFeedbackPromptEligibility()
    }
    
    private fun initializeData() {
        viewModelScope.launch {
            // Initialize default categories
            categoryRepository.initializeDefaultCategories()
            // Ensure budget month start day is loaded from preferences
            budgetRepository.refreshMonthStartDay()
            incomeRepository.refreshMonthStartDay()
        }
    }
    
    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                categoriesMap = categories.associateBy { it.id }
                // Refresh expenses with updated category info
                refreshExpensesWithCategories()
            }
        }
    }
    
    private fun loadData() {
        // Load total for current month
        viewModelScope.launch {
            expenseRepository.getTotalForCurrentMonth().collect { total ->
                _uiState.update { it.copy(totalThisMonth = total) }
            }
        }

        // Load investment total for current month
        viewModelScope.launch {
            expenseRepository.getInvestmentTotalForCurrentMonth().collect { investmentTotal ->
                _uiState.update { it.copy(investmentThisMonth = investmentTotal) }
            }
        }

        // Load recent expenses with category info
        viewModelScope.launch {
            expenseRepository.getExpensesForCurrentMonth()
                .map { expenses ->
                    expenses.take(5).map { expense ->
                        val category = expense.categoryId?.let { categoriesMap[it] }
                        ExpenseWithCategory(
                            expense = expense,
                            categoryName = category?.name,
                            categoryColor = category?.color
                        )
                    }
                }
                .collect { expensesWithCategory ->
                    _uiState.update {
                        it.copy(
                            recentExpenses = expensesWithCategory,
                            isLoading = false
                        )
                    }
                }
        }

        // Load top 5 categories with the most recent activity this month.
        // Re-fetched whenever the month's expense set changes.
        viewModelScope.launch {
            expenseRepository.getExpensesForCurrentMonth().collect {
                val now = Calendar.getInstance()
                val year = now.get(Calendar.YEAR)
                val month = now.get(Calendar.MONTH) + 1
                val breakdown = expenseRepository
                    .getRecentlyActiveCategoryTotalsForMonth(year, month, 5)
                _uiState.update { state -> state.copy(recentCategoryBreakdown = breakdown) }
            }
        }
        
        // Load uncategorized count
        viewModelScope.launch {
            expenseRepository.getUncategorizedExpenses()
                .map { it.size }
                .collect { count ->
                    _uiState.update { it.copy(uncategorizedCount = count) }
                }
        }
    }

    /**
     * Load 6-month spending trend and month-over-month comparison
     */
    private fun loadTrendData() {
        viewModelScope.launch {
            try {
                val trend = expenseRepository.getMonthlyTotals(6)
                val filledTrend = fillMissingMonths(trend, 6)

                // Compute MoM comparison
                val now = Calendar.getInstance()
                val currentYear = now.get(Calendar.YEAR)
                val currentMonth = now.get(Calendar.MONTH) + 1
                val currentTotal = expenseRepository.getTotalForMonth(currentYear, currentMonth)

                val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                val prevYear = prevCal.get(Calendar.YEAR)
                val prevMonth = prevCal.get(Calendar.MONTH) + 1
                val prevTotal = expenseRepository.getTotalForMonth(prevYear, prevMonth)

                val pctChange = if (prevTotal > 0) {
                    ((currentTotal - prevTotal) / prevTotal) * 100.0
                } else if (currentTotal > 0) 100.0 else 0.0

                val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val comparison = MonthComparison(
                    currentMonthTotal = currentTotal,
                    previousMonthTotal = prevTotal,
                    percentageChange = pctChange,
                    currentMonthLabel = fmt.format(now.time),
                    previousMonthLabel = fmt.format(prevCal.time)
                )

                _uiState.update {
                    it.copy(monthlyTrend = filledTrend, monthComparison = comparison)
                }
            } catch (_: Exception) {
                // Silently fail — trend is non-critical
            }
        }
    }

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
     * Load detected income + savings rate for the current calendar month and
     * keep them in sync as expenses change.
     */
    private fun loadIncomeData() {
        viewModelScope.launch {
            expenseRepository.getExpensesForCurrentMonth().collect {
                try {
                    val (start, end) = incomeRepository.currentMonthBounds()
                    val effective = incomeRepository.effectiveIncomeForCurrentMonth()
                    val detected = effective.detectedAmount
                    val spent = expenseRepository.getSpendingInRange(start, end)
                    val rate: Double? = if (detected > 0) {
                        (((detected - spent) / detected) * 100.0).coerceIn(-100.0, 100.0)
                    } else null
                    _uiState.update { state ->
                        state.copy(
                            receivedThisMonth = detected,
                            effectiveIncomeSource = effective.source,
                            savingsRatePct = rate
                        )
                    }
                } catch (_: Exception) {
                    // Non-critical
                }
            }
        }
    }

    private fun refreshExpensesWithCategories() {
        val currentExpenses = _uiState.value.recentExpenses
        val updated = currentExpenses.map { ewc ->
            val category = ewc.expense.categoryId?.let { categoriesMap[it] }
            ewc.copy(
                categoryName = category?.name,
                categoryColor = category?.color
            )
        }
        _uiState.update { it.copy(recentExpenses = updated) }
    }
    
    /**
     * Load budget progress reactively. Collects from Room's Flow so that
     * additions/deletions on the Budget screen are reflected immediately on Home.
     */
    private fun loadBudgetData() {
        viewModelScope.launch {
            budgetRepository.getActiveBudgets().collectLatest { activeBudgets ->
                try {
                    if (activeBudgets.isNotEmpty()) {
                        // Recompute progress for all active budgets
                        val progressList = budgetRepository.getBudgetProgressList()
                        _uiState.update {
                            it.copy(
                                budgetProgressList = progressList
                                    .sortedByDescending { bp -> bp.percentage }
                                    .take(4),
                                showBudgetPrompt = false
                            )
                        }
                    } else {
                        // No budgets — clear and check prompt
                        _uiState.update {
                            it.copy(
                                budgetProgressList = emptyList()
                            )
                        }
                        checkBudgetPrompt()
                    }
                } catch (_: Exception) {
                    // Non-critical — silently fail
                }
            }
        }
    }

    /**
     * Check if the data-driven budget prompt should be shown.
     * Conditions:
     * 1. No active budgets exist
     * 2. ≥20 categorized expenses
     * 3. User hasn't dismissed the prompt
     */
    private suspend fun checkBudgetPrompt() {
        try {
            val dismissed = appPreferences.isBudgetPromptDismissed()
            if (dismissed) {
                _uiState.update { it.copy(showBudgetPrompt = false) }
                return
            }

            val categorizedCount = budgetRepository.getCategorizedExpenseCount()
            if (categorizedCount < 20) {
                _uiState.update { it.copy(showBudgetPrompt = false) }
                return
            }

            // Get top spending category from last month
            val topGroup = budgetRepository.getTopSpendingGroupLastMonth()
            _uiState.update {
                it.copy(
                    showBudgetPrompt = true,
                    budgetPromptGroupId = topGroup?.first,
                    budgetPromptCategoryName = topGroup?.second,
                    budgetPromptAmount = topGroup?.third
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(showBudgetPrompt = false) }
        }
    }

    /**
     * Dismiss the budget prompt permanently.
     */
    fun dismissBudgetPrompt() {
        viewModelScope.launch {
            appPreferences.dismissBudgetPrompt()
            _uiState.update { it.copy(showBudgetPrompt = false) }
        }
    }

    // ==================== SMS Permission Banner ====================

    /** Whether the banner was permanently dismissed — cached from DataStore. */
    private var smsBannerPermanentlyDismissed = false

    /**
     * Load the permanent dismiss state from DataStore.
     * The actual permission check happens in the composable via [updateSmsPermissionStatus].
     */
    private fun loadSmsBannerState() {
        viewModelScope.launch {
            smsBannerPermanentlyDismissed = appPreferences.isSmsBannerDismissed()
            // Don't show banner yet — wait for composable to call updateSmsPermissionStatus()
        }
    }

    /**
     * Called by the composable when it checks SMS permission on (re)composition.
     * If permission is granted OR banner was permanently dismissed → hide.
     */
    fun updateSmsPermissionStatus(hasPermission: Boolean) {
        lastKnownSmsPermissionGranted = hasPermission
        val shouldShow = !hasPermission && !smsBannerPermanentlyDismissed
        _uiState.update { it.copy(showSmsPermissionBanner = shouldShow) }
        evaluateLowEngagementPromptEligibility(hasPermission)
    }

    /**
     * Dismiss the SMS permission banner for this session only.
     */
    fun dismissSmsBannerSession() {
        _uiState.update { it.copy(showSmsPermissionBanner = false) }
    }

    /**
     * Permanently dismiss the SMS permission banner ("Don't ask again").
     * Respects manual-only users.
     */
    fun dismissSmsBannerPermanently() {
        viewModelScope.launch {
            appPreferences.dismissSmsBanner()
            smsBannerPermanentlyDismissed = true
            _uiState.update { it.copy(showSmsPermissionBanner = false) }
        }
    }

    // ==================== In-App Review ====================

    /**
     * Evaluate whether Stage 1B review conditions are met and expose a one-shot UI flag.
     */
    private fun checkReviewPromptEligibility() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val dayMs = 24 * 60 * 60 * 1000L

                val installTs = appPreferences.getInstallTimestamp()
                if (installTs == 0L) return@launch
                if ((now - installTs) < REVIEW_MIN_INSTALL_AGE_DAYS * dayMs) return@launch

                val categorizedCount = budgetRepository.getCategorizedExpenseCount()
                if (categorizedCount < REVIEW_MIN_CATEGORIZED_EXPENSES) return@launch

                val qualifiedSessions = appPreferences.getQualifiedSessionCount()
                if (qualifiedSessions < REVIEW_MIN_QUALIFIED_SESSIONS) return@launch

                val lastPromptTs = appPreferences.getLastReviewPromptTimestamp()
                if (lastPromptTs > 0L && (now - lastPromptTs) < REVIEW_COOLDOWN_DAYS * dayMs) {
                    return@launch
                }

                val promptCount = appPreferences.getReviewPromptCount()
                if (promptCount >= REVIEW_MAX_PROMPT_COUNT) return@launch

                _uiState.update { it.copy(shouldShowReview = true) }
            } catch (_: Exception) {
                // Non-critical flow; ignore failures.
            }
        }
    }

    /** Clear the one-shot review trigger so recomposition doesn't relaunch the flow. */
    fun onReviewPromptHandled() {
        _uiState.update { it.copy(shouldShowReview = false) }
    }

    /** Persist review prompt throttle markers after a successful review request task. */
    fun recordReviewPromptShown() {
        viewModelScope.launch {
            appPreferences.markReviewPromptShown()
            if (!appPreferences.isFeedbackPromptShown()) {
                _uiState.update { it.copy(showStructuredFeedbackPrompt = true) }
            }
        }
    }

    // ==================== Stage 1D: Structured Feedback ====================

    private fun checkStructuredFeedbackPromptEligibility() {
        viewModelScope.launch {
            try {
                val alreadyShown = appPreferences.isFeedbackPromptShown()
                if (alreadyShown) return@launch

                val categorizedCount = budgetRepository.getCategorizedExpenseCount()
                if (categorizedCount < REVIEW_MIN_CATEGORIZED_EXPENSES) return@launch

                val qualifiedSessions = appPreferences.getQualifiedSessionCount()
                if (qualifiedSessions < STRUCTURED_MIN_QUALIFIED_SESSIONS) return@launch

                _uiState.update { it.copy(showStructuredFeedbackPrompt = true) }
            } catch (_: Exception) {
                // Non-critical prompt.
            }
        }
    }

    fun dismissStructuredFeedbackPrompt() {
        viewModelScope.launch {
            appPreferences.markFeedbackPromptShown()
            _uiState.update { it.copy(showStructuredFeedbackPrompt = false) }
        }
    }

    fun submitStructuredFeedback(option: String, otherText: String?) {
        viewModelScope.launch {
            val trimmedOther = otherText?.trim().orEmpty()
            val normalizedOther = if (trimmedOther.isNotEmpty()) trimmedOther else null
            val response = if (normalizedOther != null) "$option | $normalizedOther" else option

            appPreferences.saveFeedbackResponse(response)
            appPreferences.markFeedbackPromptShown()

            val usageSummary = usageSummaryGenerator.generate()
            val body = buildString {
                appendLine("Structured feedback response:")
                appendLine(response)
                appendLine()
                appendLine("You can edit or remove the usage context below before sending.")
                appendLine()
                append(usageSummary)
            }

            _uiState.update {
                it.copy(
                    showStructuredFeedbackPrompt = false,
                    pendingFeedbackEmailSubject = "PesaTrack Feedback: What would make it more useful",
                    pendingFeedbackEmailBody = body
                )
            }
        }
    }

    // ==================== Stage 1E: Low-Engagement Feedback ====================

    private fun evaluateLowEngagementPromptEligibility(hasSmsPermission: Boolean) {
        viewModelScope.launch {
            try {
                val alreadyShown = appPreferences.isLowEngagementPromptShown()
                if (alreadyShown) return@launch

                val metrics = appPreferences.getUsageMetricsSnapshot()
                val installTs = metrics.installTimestamp
                if (installTs == 0L) return@launch

                val now = System.currentTimeMillis()
                val sinceInstall = now - installTs
                val minuteMs = 60 * 1000L
                val dayMs = 24 * 60 * 60 * 1000L

                val hasFirstValue = metrics.firstSmsParsed ||
                    metrics.firstImportCompleted ||
                    metrics.firstManualEntry ||
                    metrics.firstCategorization

                val onboardingCompleted = metrics.onboardingSmsGranted || metrics.onboardingSmsSkipped

                val conditionA = onboardingCompleted && !hasSmsPermission &&
                    sinceInstall >= LOW_ENGAGEMENT_SMS_GRACE_MINUTES * minuteMs

                val firstValueDeadlineChecked = appPreferences.isFirstValueDeadlineChecked()
                val conditionB = onboardingCompleted && !hasFirstValue &&
                    sinceInstall >= LOW_ENGAGEMENT_FIRST_VALUE_GRACE_MINUTES * minuteMs

                if (conditionB && !firstValueDeadlineChecked) {
                    appPreferences.markFirstValueDeadlineChecked()
                }

                // Since this prompt is only visible when user re-opens the app, we proxy
                // "no return by day 3" as low engagement: still only one qualified session after day 3.
                val conditionC = metrics.qualifiedSessions <= 1 && sinceInstall >= LOW_ENGAGEMENT_RETURN_CHECK_DAYS * dayMs

                if (conditionA || conditionB || conditionC) {
                    _uiState.update { it.copy(showLowEngagementFeedbackPrompt = true) }
                }
            } catch (_: Exception) {
                // Non-critical prompt.
            }
        }
    }

    fun dismissLowEngagementPrompt() {
        viewModelScope.launch {
            appPreferences.markLowEngagementPromptShown()
            _uiState.update { it.copy(showLowEngagementFeedbackPrompt = false) }
        }
    }

    fun submitLowEngagementFeedback(reason: String, otherText: String?) {
        viewModelScope.launch {
            val trimmedOther = otherText?.trim().orEmpty()
            val normalizedOther = if (trimmedOther.isNotEmpty()) trimmedOther else null
            val response = if (normalizedOther != null) "$reason | $normalizedOther" else reason

            appPreferences.saveLowEngagementReason(response)
            appPreferences.markLowEngagementPromptShown()

            val usageSummary = usageSummaryGenerator.generate()
            val body = buildString {
                appendLine("Low-engagement friction feedback:")
                appendLine(response)
                appendLine()
                appendLine("You can edit or remove the usage context below before sending.")
                appendLine()
                append(usageSummary)
            }

            _uiState.update {
                it.copy(
                    showLowEngagementFeedbackPrompt = false,
                    pendingFeedbackEmailSubject = "PesaTrack Feedback: Setup blockers",
                    pendingFeedbackEmailBody = body
                )
            }
        }
    }

    fun onFeedbackEmailHandled() {
        _uiState.update {
            it.copy(
                pendingFeedbackEmailSubject = null,
                pendingFeedbackEmailBody = null
            )
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
        // Budget data is reactively collected via Flow — no need to reload here.
        // The Flow from BudgetDao.getActiveBudgets() auto-emits on DB changes.
        checkStructuredFeedbackPromptEligibility()
        evaluateLowEngagementPromptEligibility(lastKnownSmsPermissionGranted)
    }
}
