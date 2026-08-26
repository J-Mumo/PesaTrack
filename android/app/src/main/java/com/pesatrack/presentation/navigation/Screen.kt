package com.pesatrack.presentation.navigation

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")

    /**
     * Analytics tab. Accepts an optional `section` query argument used to
     * deep-link callers (e.g. the Home screen's "By Category → View All"
     * link) into a specific section of the Charts → Monthly tab.
     */
    object Analytics : Screen("analytics?section={section}") {
        const val ARG_SECTION = "section"
        /** Deep-link target: Charts → Monthly → By Category. */
        const val SECTION_BY_CATEGORY = "byCategory"
        /**
         * Deep-link target: Charts → Yearly → Grid (Category × Month pivot).
         * Used by the Home "Trend by group → View all" link so users land
         * directly on the full-year table.
         */
        const val SECTION_YEARLY_GRID = "yearlyGrid"

        /** Base route used by the bottom navigation tab (no arguments). */
        const val BASE_ROUTE = "analytics"

        fun createRoute(section: String? = null): String =
            if (section == null) BASE_ROUTE else "$BASE_ROUTE?section=$section"
    }
    object Expenses : Screen("expenses")
    object Categorize : Screen("categorize/{expenseId}") {
        fun createRoute(expenseId: Long) = "categorize/$expenseId"
    }

    /**
     * Income categorization screen (Income tracking Phase 2).
     *
     * Lets the user pick an [com.pesatrack.domain.models.IncomeSource] for a
     * newly detected income transaction, mark it as excluded from analytics,
     * or add a note. Reached from the income notification or the income list.
     */
    object CategorizeIncome : Screen("categorize_income/{incomeId}") {
        fun createRoute(incomeId: Long) = "categorize_income/$incomeId"
    }

    /**
     * Income list screen (Income tracking Phase 3).
     *
     * Shows month-to-date detected income, per-source breakdown, and a list
     * of individual income transactions. Reached from Home's "received"
     * secondary line and Budget's detected-income card.
     */
    object Income : Screen("income")
    object ImportHistory : Screen("import_history")
    object ExcelImport : Screen("excel_import")
    object StatementImport : Screen("statement_import")
    object BatchCategorize : Screen("batch_categorize")
    object Settings : Screen("settings")
    object ManualEntry : Screen("manual_entry")
    object Budget : Screen("budget")
    object CategoryManagement : Screen("category_management")
    object PinSetup : Screen("pin_setup/{mode}") {
        fun createRoute(mode: String) = "pin_setup/$mode"
    }
    object About : Screen("about")

    /**
     * Weekly Review screen (Insights & Reports v1.0).
     *
     * Accepts an optional snapshot id so notifications can deep-link to the
     * exact report they advertised. When called without an id, the screen
     * falls back to the most recent stored snapshot.
     */
    object WeeklyReview : Screen("weekly_review?snapshotId={snapshotId}") {
        const val ARG_SNAPSHOT_ID = "snapshotId"
        fun createRoute(snapshotId: Long? = null): String =
            if (snapshotId == null) "weekly_review" else "weekly_review?snapshotId=$snapshotId"
    }

    /**
     * Monthly Review screen (Insights & Reports v1.1).
     *
     * Accepts an optional snapshot id so notifications can deep-link to the
     * exact report they advertised.
     */
    object MonthlyReview : Screen("monthly_review?snapshotId={snapshotId}") {
        const val ARG_SNAPSHOT_ID = "snapshotId"
        fun createRoute(snapshotId: Long? = null): String =
            if (snapshotId == null) "monthly_review" else "monthly_review?snapshotId=$snapshotId"
    }

    /**
     * Quarterly Review screen (Insights & Reports v1.3).
     */
    object QuarterlyReview : Screen("quarterly_review?snapshotId={snapshotId}") {
        const val ARG_SNAPSHOT_ID = "snapshotId"
        fun createRoute(snapshotId: Long? = null): String =
            if (snapshotId == null) "quarterly_review" else "quarterly_review?snapshotId=$snapshotId"
    }

    /**
     * Year-in-Review screen (Insights & Reports v1.4).
     */
    object YearInReview : Screen("year_in_review?year={year}") {
        const val ARG_YEAR = "year"
        fun createRoute(year: Int? = null): String =
            if (year == null) "year_in_review" else "year_in_review?year=$year"
    }
}

/**
 * Bottom navigation items
 */
enum class BottomNavItem(
    val route: String,
    val title: String,
    val icon: String
) {
    HOME(Screen.Home.route, "Home", "home"),
    ANALYTICS(Screen.Analytics.BASE_ROUTE, "Analytics", "bar_chart"),
    EXPENSES(Screen.Expenses.route, "Expenses", "receipt_long")
}
