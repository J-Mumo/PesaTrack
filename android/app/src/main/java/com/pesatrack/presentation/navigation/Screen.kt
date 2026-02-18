package com.pesatrack.presentation.navigation

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Payment : Screen("payment?paymentType={paymentType}") {
        fun createRoute(paymentType: String) = "payment?paymentType=$paymentType"
    }
    object Expenses : Screen("expenses")
    object Categorize : Screen("categorize/{expenseId}") {
        fun createRoute(expenseId: Long) = "categorize/$expenseId"
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
    PAY(Screen.Payment.route, "Pay", "payment"),
    EXPENSES(Screen.Expenses.route, "Expenses", "receipt_long")
}
