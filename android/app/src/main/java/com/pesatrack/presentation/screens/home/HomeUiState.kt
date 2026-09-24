package com.pesatrack.presentation.screens.home

import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.domain.models.BudgetProgress
import com.pesatrack.domain.models.EffectiveIncomeSource
import com.pesatrack.domain.models.GroupTrendPreview
import com.pesatrack.domain.models.MonthComparison
import com.pesatrack.presentation.screens.expenses.ExpenseWithCategory
import com.pesatrack.services.ai.CoachInsight

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
    /**
     * Compact 3-period × top-N group preview shown on Home under "By Category".
     * Null when we have fewer than two periods with any spend (nothing to compare
     * against yet). Full-year drill-down lives in Analytics → Yearly → Grid.
     */
    val groupTrendPreview: GroupTrendPreview? = null,
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
    val pendingFeedbackEmailSubject: String? = null,

    // ==================== AI Coach Insight (Phase 2 / v1.7.0) ====================

    /**
     * The AI-generated Coach Insight for today, produced by
     * [com.pesatrack.services.ai.CoachInsightRepository.getForToday].
     *
     * Null in every case where the card should NOT render: the user isn't
     * Pro-entitled, the `pro_ai_enabled` ship-gate is off, the backend
     * returned a fallback envelope, or a network / cache path all failed
     * with no yesterday-cached fallback available. In every one of those
     * cases the existing Home content renders unchanged — no error card,
     * no "AI unavailable" copy. See plans/ai-pro-phase2-spec.md §8.
     *
     * Non-null means the model produced (or the cache retained) a valid
     * insight that has already been recipient-rehydrated on-device — the
     * title/body already contain real merchant names, not `rN` ids.
     */
    val coachInsight: CoachInsight? = null,

    /**
     * Whether the user currently has a valid Pro entitlement.
     *
     * Drives the FAB routing on Home — Pro users tapping the AI FAB go
     * to [com.pesatrack.presentation.navigation.Screen.AskYourMoney];
     * free users go to the [com.pesatrack.presentation.navigation.Screen.Pro]
     * upsell surface. See plans/ai-pro-phase3-spec.md §3.1.
     *
     * Sourced from the same reactive observer that drives
     * [coachInsight] — see [HomeViewModel.loadCoachInsight].
     */
    val isProEntitled: Boolean = false,
)
