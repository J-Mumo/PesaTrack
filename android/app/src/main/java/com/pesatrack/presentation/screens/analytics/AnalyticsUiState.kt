package com.pesatrack.presentation.screens.analytics

import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.database.dao.PaymentTypeTotal
import com.pesatrack.data.local.database.dao.TopSpender
import com.pesatrack.data.local.database.dao.YearMonthTotal
import com.pesatrack.domain.models.CategoryTrend
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.domain.models.RecurringExpense
import com.pesatrack.domain.models.YearComparison

/**
 * Top-level tab selection for the Analytics screen: Insights feed vs Charts
 */
enum class InsightsTab {
    INSIGHTS,
    CHARTS
}

/**
 * Sub-tab within the Charts section: Monthly or Yearly
 */
enum class AnalyticsTab {
    MONTHLY,
    YEARLY
}

/**
 * Data for the Pace insight card.
 * Shows projected month-end spending based on current daily run rate.
 */
data class PaceCardData(
    val dailyRunRate: Double,
    val projected: Double,
    val lastMonthTotal: Double,
    val delta: Double,
    val monthName: String,
    val prevMonthName: String
)

/**
 * Data for the Quiet Leak insight card.
 * Categories with many small transactions that add up.
 */
data class QuietLeakData(
    val categoryName: String,
    val transactionCount: Int,
    val total: Double,
    val categoryId: Int
)

/**
 * Data for the Savings Rate insight card (Income tracking Phase 4).
 *
 * Savings rate = (income - spend) / income. We only show this when we have
 * income data we can trust (detected SMS or user override) — never assumed.
 */
data class SavingsRateData(
    /** Current calendar-month savings rate as a percentage (-100..100). */
    val currentMonthPct: Double,
    /** Rolling average over the last three calendar months (inclusive of current). */
    val rollingThreeMonthPct: Double,
    /** Detected income used for the current month. */
    val currentMonthIncome: Double,
    /** Total spend used for the current month. */
    val currentMonthSpend: Double,
    /** Reconciliation source so the card can describe where income came from. */
    val effectiveIncomeSource: EffectiveIncomeSource
)

/**
 * One month of the Income vs Spend overlay chart (Phase 4).
 * `monthKey` is `YYYY-MM`. `income` may be zero when no income was detected.
 */
data class IncomeSpendPoint(
    val monthKey: String,
    val income: Double,
    val spend: Double
)

/**
 * UI State for the Analytics screen
 */
data class AnalyticsUiState(
    val isLoading: Boolean = true,

    // ==================== Top-level Tab (Insights vs Charts) ====================

    /** Selected top-level tab: Insights feed or Charts */
    val selectedInsightsTab: InsightsTab = InsightsTab.INSIGHTS,

    // ==================== Insight Cards ====================

    /** Pace card data — projected month-end spending */
    val paceData: PaceCardData? = null,
    /** Whether to show the pace card (day of month >= 7) */
    val showPaceCard: Boolean = false,

    /** Quiet leak categories (≥8 txns, avg ≤ KES 300) */
    val quietLeaks: List<QuietLeakData> = emptyList(),
    /** Whether to show the quiet leak card */
    val showQuietLeakCard: Boolean = false,

    /** Percentage of total spend that is uncategorized */
    val uncategorizedPercentage: Double = 0.0,
    /** Whether to show the categorization nudge (>15%) */
    val showCategorizationNudge: Boolean = false,

    // ── Savings Rate (Phase 4) ──
    /** Savings rate card data. Null when we don't have trustworthy income. */
    val savingsRate: SavingsRateData? = null,
    /** Whether to show the savings rate insight card on the Insights feed. */
    val showSavingsRateCard: Boolean = false,

    // ==================== Charts Sub-Tab ====================

    /** Selected charts sub-tab: Monthly or Yearly */
    val selectedTab: AnalyticsTab = AnalyticsTab.MONTHLY,

    // ==================== Monthly Tab ====================

    /** Selected month (1-based) and year for filtering */
    val selectedYear: Int = 0,
    val selectedMonth: Int = 0,
    val selectedMonthLabel: String = "",

    // Chart data
    val monthlyTrend: List<MonthlyTotal> = emptyList(),
    val categoryBreakdown: List<CategoryTotal> = emptyList(),
    val topSpenders: List<TopSpender> = emptyList(),
    val paymentTypeBreakdown: List<PaymentTypeTotal> = emptyList(),

    /**
     * 12-month income-vs-spend overlay (Phase 4).
     * Empty when there is no detected income across the period.
     */
    val incomeVsSpend: List<IncomeSpendPoint> = emptyList(),

    // Variable-spend category trends (CV-detected)
    val categoryTrends: List<CategoryTrend> = emptyList(),

    // Month-over-month comparison
    val monthComparison: MonthComparison? = null,

    // Weekly snapshot (last 7 days vs previous 7 days)
    /** Total spending in the last 7 days */
    val weeklyTotal: Double = 0.0,
    /** Total spending in the 7 days before that (days 8–14 ago) */
    val previousWeekTotal: Double = 0.0,
    /** Week-over-week percentage change */
    val weekOverWeekChange: Double = 0.0,
    /** Top spending category name this week */
    val topCategoryThisWeek: String? = null,
    /** Top spending category amount this week */
    val topCategoryThisWeekAmount: Double = 0.0,
    /** Date range label for the current week (e.g. "Apr 25 – May 1") */
    val weekDateLabel: String = "",

    // Summary stats for selected month
    val totalForMonth: Double = 0.0,
    val transactionCountForMonth: Int = 0,
    val avgDailySpend: Double = 0.0,

    // ==================== Yearly Tab ====================

    /** Selected year for yearly analytics */
    val selectedYearForYearly: Int = 0,
    val yearlyIsLoading: Boolean = false,

    // YoY comparison
    val yearComparison: YearComparison? = null,

    // Yearly summary stats
    val yearlyTotalForYear: Double = 0.0,
    val yearlyTransactionCount: Int = 0,
    val yearlyAvgMonthlySpend: Double = 0.0,

    // 12-month overlay chart data (current year vs previous year)
    val currentYearMonthlyTotals: List<YearMonthTotal> = emptyList(),
    val previousYearMonthlyTotals: List<YearMonthTotal> = emptyList(),

    // Yearly breakdowns (reuse same types as monthly)
    val yearlyCategoryBreakdown: List<CategoryTotal> = emptyList(),
    val yearlyTopSpenders: List<TopSpender> = emptyList(),
    val yearlyPaymentTypeBreakdown: List<PaymentTypeTotal> = emptyList(),

    // ==================== Recipient Search ====================

    /** Current search query for recipient lookup (shared between monthly/yearly tabs) */
    val recipientSearchQuery: String = "",

    /** Search results for the monthly tab (null = not searching, empty = no matches) */
    val recipientSearchResults: List<TopSpender>? = null,

    /** Aggregate total across all monthly search results */
    val recipientSearchTotal: Double = 0.0,

    /** Search results for the yearly tab (null = not searching, empty = no matches) */
    val yearlyRecipientSearchResults: List<TopSpender>? = null,

    /** Aggregate total across all yearly search results */
    val yearlyRecipientSearchTotal: Double = 0.0,

    /** Whether a search is currently loading */
    val recipientSearchLoading: Boolean = false,

    // ==================== Recurring Expense Detection ====================

    /** Total recurring spending for the selected month (KES) */
    val recurringTotal: Double = 0.0,
    /** Total one-time (non-recurring) spending for the selected month (KES) */
    val oneTimeTotal: Double = 0.0,
    /** Top recurring expense names for display (e.g. "Rent, SACCO, WiFi") */
    val topRecurringNames: String = "",
    /** Whether recurring data has been loaded (hides the card until ready) */
    val hasRecurringData: Boolean = false,

    // ==================== Budget Integration ====================

    /** Whether the user has any active budgets (used to show/hide budget banner) */
    val hasActiveBudgets: Boolean = false,

    // ==================== Budget Burn-Down (v1.3) ====================

    /** Categories projected to exhaust budget ≥3 days before month end */
    val budgetBurnDowns: List<BudgetBurnDownData> = emptyList(),
    /** Whether to show the burn-down card */
    val showBudgetBurnDown: Boolean = false,

    val error: String? = null
)

/**
 * Data for a single budget category projected to run out early.
 */
data class BudgetBurnDownData(
    val categoryName: String,
    val exhaustionDay: Int,
    val daysEarly: Int,
    val categoryId: Int
)
