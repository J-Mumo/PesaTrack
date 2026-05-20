package com.pesatrack.presentation.navigation

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Analytics : Screen("analytics")
    object Expenses : Screen("expenses")
    object Categorize : Screen("categorize/{expenseId}") {
        fun createRoute(expenseId: Long) = "categorize/$expenseId"
    }
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
    ANALYTICS(Screen.Analytics.route, "Analytics", "bar_chart"),
    EXPENSES(Screen.Expenses.route, "Expenses", "receipt_long")
}
