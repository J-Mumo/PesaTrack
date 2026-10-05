package com.pesatrack.presentation.navigation

/** One immutable delivery, including a token so repeated taps of the same target are reactive. */
data class NotificationNavigation(
    val token: Long,
    val target: String,
    val expenseId: Long? = null,
    val incomeId: Long? = null,
    val snapshotId: Long? = null,
    val year: Int? = null
) {
    fun acknowledge(handled: NotificationNavigation): NotificationNavigation? =
        if (this == handled) null else this

    fun route(): String? = when (target) {
        "categorize" -> expenseId?.takeIf { it > 0 }?.let(Screen.Categorize::createRoute)
        "categorize_income" -> incomeId?.takeIf { it > 0 }?.let(Screen.CategorizeIncome::createRoute)
        "budget" -> Screen.Budget.route
        "weekly_review" -> Screen.WeeklyReview.createRoute(snapshotId)
        "monthly_review" -> Screen.MonthlyReview.createRoute(snapshotId)
        "quarterly_review" -> Screen.QuarterlyReview.createRoute(snapshotId)
        "year_in_review" -> Screen.YearInReview.createRoute(year)
        else -> null
    }

    fun routeWhenReady(onboardingComplete: Boolean, lockReady: Boolean, locked: Boolean,
                       resumed: Boolean, navReady: Boolean): String? =
        if (onboardingComplete && lockReady && !locked && resumed && navReady) route() else null
}