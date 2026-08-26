package com.pesatrack.presentation.screens.home

import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.domain.models.BudgetProgress
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.presentation.screens.expenses.ExpenseWithCategory

/**
 * UI State for the Home screen
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val totalThisMonth: Double = 0.0,
    /** Total invested this month (Investment & Savings group 18) */
    val investmentThisMonth: Double = 0.0,
    /**
     * Human-readable label for the current budget-cycle period shown in the summary card.
     * Matches the label used by Budgets / Analytics — "July 2026" when
     * `monthStartDay = 1`, "Jun 25 – Jul 24, 2026" when the user has moved their cycle.
     */
    val currentMonthLabel: String = "",

    // ==================== Income (Phase 3) ====================

    /** Sum of detected inflow income for the current calendar month (excludes self-transfers). */
    val receivedThisMonth: Double = 0.0,
    /** Source of the income figure currently displayed — drives whether the received line shows. */
    val effectiveIncomeSource: EffectiveIncomeSource = EffectiveIncomeSource.NONE,
    /**
     * Share of received income deliberately set aside as savings / investment
     * this period, expressed as %. Formula: `investmentThisMonth / receivedThisMonth × 100`,
     * where `investmentThisMonth` is the sum of expenses categorised under the
     * Investment & Savings group (18).
     *
     * This is what the user actually saved — not `(received − spent) / received`,
     * which used to be labelled "% saved" and inflated to ~99% at the start of
     * a new period because `spent` was still near zero. See AGENTS.md "honest
     * numbers" principle.
     *
     * Null when received is 0 or income source is `NONE`/`MANUAL_OVERRIDE`.
     */
    val savingsRatePct: Double? = null,

    val recentExpenses: List<ExpenseWithCategory> = emptyList(),
    /** Up to 5 categories with the most recent activity in the current month. */
    val recentCategoryBreakdown: List<CategoryTotal> = emptyList(),
    val uncategorizedCount: Int = 0,
    val error: String? = null,
    /** Last 6 months spending trend for mini chart */
    val monthlyTrend: List<MonthlyTotal> = emptyList(),
    /** Month-over-month comparison for trend card */
    val monthComparison: MonthComparison? = null,

    // ==================== SMS Permission ====================

    /** Whether to show the SMS permission banner (permission missing + not permanently dismissed) */
    val showSmsPermissionBanner: Boolean = false,

    // ==================== Notification Permission ====================

    /**
     * Whether to show the notification permission banner (Android 13+ POST_NOTIFICATIONS
     * missing + not permanently dismissed).
     */
    val showNotificationPermissionBanner: Boolean = false,

    // ==================== Budget ====================

    /** Top budget progress items (sorted by % used, max 4) — shown when user has budgets */
    val budgetProgressList: List<BudgetProgress> = emptyList(),

    /** Whether to show the data-driven budget setup prompt (no budgets + ≥20 categorized expenses + not dismissed) */
    val showBudgetPrompt: Boolean = false,
    /** Top spending category name for the prompt (e.g. "Food & Dining") */
    val budgetPromptCategoryName: String? = null,
    /** Top spending amount for the prompt (e.g. 14200.0) */
    val budgetPromptAmount: Double? = null,
    /** Category group ID to pre-select when navigating to budget screen from prompt */
    val budgetPromptGroupId: Long? = null,

    // ==================== In-App Review ====================

    /** One-shot flag to trigger Google Play in-app review request. */
    val shouldShowReview: Boolean = false,

    // ==================== Stage 1D: Structured Feedback ====================

    /** Whether to show the value-based structured feedback prompt card. */
    val showStructuredFeedbackPrompt: Boolean = false,

    // ==================== Stage 1E: Low-Engagement Feedback ====================

    /** Whether to show the low-engagement friction feedback prompt card. */
    val showLowEngagementFeedbackPrompt: Boolean = false,

    /** One-shot draft body for launching editable feedback email. */
    val pendingFeedbackEmailBody: String? = null,

    /** One-shot draft subject for launching editable feedback email. */
    val pendingFeedbackEmailSubject: String? = null
)
