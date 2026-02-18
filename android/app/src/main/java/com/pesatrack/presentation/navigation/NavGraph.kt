package com.pesatrack.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pesatrack.presentation.screens.categorize.CategorizeScreen
import com.pesatrack.presentation.screens.expenses.ExpenseListScreen
import com.pesatrack.presentation.screens.home.HomeScreen
import com.pesatrack.presentation.screens.payment.PaymentScreen

/**
 * Main navigation graph for the app
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Home Screen
        composable(route = Screen.Home.route) {
            HomeScreen(
                onNavigateToPayment = { paymentType ->
                    navController.navigate(Screen.Payment.createRoute(paymentType))
                },
                onNavigateToExpenses = {
                    navController.navigate(Screen.Expenses.route)
                },
                onNavigateToCategorize = { expenseId ->
                    navController.navigate(Screen.Categorize.createRoute(expenseId))
                }
            )
        }
        
        // Payment Screen
        composable(
            route = Screen.Payment.route,
            arguments = listOf(navArgument("paymentType") { 
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val paymentType = backStackEntry.arguments?.getString("paymentType")
            PaymentScreen(
                paymentType = paymentType,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPaymentComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Expenses List Screen
        composable(route = Screen.Expenses.route) {
            ExpenseListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCategorize = { expenseId ->
                    navController.navigate(Screen.Categorize.createRoute(expenseId))
                }
            )
        }
        
        // Categorize Screen
        composable(
            route = Screen.Categorize.route,
            arguments = listOf(
                navArgument("expenseId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: 0L
            CategorizeScreen(
                expenseId = expenseId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
