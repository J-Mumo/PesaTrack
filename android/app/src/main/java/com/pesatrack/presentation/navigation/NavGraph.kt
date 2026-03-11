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
                onNavigateToExpenses = {
                    navController.navigate(Screen.Expenses.route)
                },
                onNavigateToCategorize = { expenseId ->
                    navController.navigate(Screen.Categorize.createRoute(expenseId))
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
