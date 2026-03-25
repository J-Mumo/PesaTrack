package com.pesatrack.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pesatrack.presentation.screens.analytics.AnalyticsScreen
import com.pesatrack.presentation.screens.batch_categorize.BatchCategorizeScreen
import com.pesatrack.presentation.screens.categorize.CategorizeScreen
import com.pesatrack.presentation.screens.expenses.ExpenseListScreen
import com.pesatrack.presentation.screens.home.HomeScreen
import com.pesatrack.presentation.screens.excel_import.ExcelImportScreen
import com.pesatrack.presentation.screens.import_history.ImportScreen
import com.pesatrack.presentation.screens.budget.BudgetScreen
import com.pesatrack.presentation.screens.category_management.CategoryManagementScreen
import com.pesatrack.presentation.screens.manual_entry.ManualEntryScreen
import com.pesatrack.presentation.screens.about.AboutScreen
import com.pesatrack.presentation.screens.pin.PinSetupScreen
import com.pesatrack.presentation.screens.settings.SettingsScreen

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
                },
                onNavigateToImport = {
                    navController.navigate(Screen.ImportHistory.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToBatchCategorize = {
                    navController.navigate(Screen.BatchCategorize.route)
                },
                onNavigateToManualEntry = {
                    navController.navigate(Screen.ManualEntry.route)
                },
                onNavigateToAnalytics = {
                    navController.navigate(Screen.Analytics.route)
                },
                onNavigateToBudget = {
                    navController.navigate(Screen.Budget.route)
                }
            )
        }

        // Analytics Screen
        composable(route = Screen.Analytics.route) {
            AnalyticsScreen(
                onNavigateToBudget = {
                    navController.navigate(Screen.Budget.route)
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
                },
                onNavigateToManualEntry = {
                    navController.navigate(Screen.ManualEntry.route)
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
        
        // Import History Screen
        composable(route = Screen.ImportHistory.route) {
            ImportScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBatchCategorize = {
                    navController.navigate(Screen.BatchCategorize.route)
                },
                onNavigateToExcelImport = {
                    navController.navigate(Screen.ExcelImport.route)
                }
            )
        }

        // Excel Import Screen
        composable(route = Screen.ExcelImport.route) {
            ExcelImportScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBatchCategorize = {
                    navController.navigate(Screen.BatchCategorize.route)
                }
            )
        }
        
        // Batch Categorize Screen
        composable(route = Screen.BatchCategorize.route) {
            BatchCategorizeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Settings Screen
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBudget = {
                    navController.navigate(Screen.Budget.route)
                },
                onNavigateToCategoryManagement = {
                    navController.navigate(Screen.CategoryManagement.route)
                },
                onNavigateToPinSetup = { mode ->
                    navController.navigate(Screen.PinSetup.createRoute(mode))
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                }
            )
        }

        // Manual Entry Screen
        composable(route = Screen.ManualEntry.route) {
            ManualEntryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Budget Screen
        composable(route = Screen.Budget.route) {
            BudgetScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Category Management Screen
        composable(route = Screen.CategoryManagement.route) {
            CategoryManagementScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // PIN Setup Screen (setup / change / disable)
        composable(
            route = Screen.PinSetup.route,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "setup"
            PinSetupScreen(
                isChangeMode = mode == "change",
                isDisableMode = mode == "disable",
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSetupComplete = {
                    navController.popBackStack()
                }
            )
        }

        // About Screen
        composable(route = Screen.About.route) {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
