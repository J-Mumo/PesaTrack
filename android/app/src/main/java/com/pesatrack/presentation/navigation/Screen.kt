package com.pesatrack.presentation.navigation

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Expenses : Screen("expenses")
    object Categorize : Screen("categorize/{expenseId}") {
        fun createRoute(expenseId: Long) = "categorize/$expenseId"
    }
    object ImportHistory : Screen("import_history")
    object ExcelImport : Screen("excel_import")
    object BatchCategorize : Screen("batch_categorize")
    object Settings : Screen("settings")
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
    EXPENSES(Screen.Expenses.route, "Expenses", "receipt_long")
}
