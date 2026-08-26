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
import com.pesatrack.presentation.screens.statement_import.StatementImportScreen
import com.pesatrack.presentation.screens.budget.BudgetScreen
import com.pesatrack.presentation.screens.category_management.CategoryManagementScreen
import com.pesatrack.presentation.screens.manual_entry.ManualEntryScreen
import com.pesatrack.presentation.screens.about.AboutScreen
import com.pesatrack.presentation.screens.pin.PinSetupScreen
import com.pesatrack.presentation.screens.monthly_review.MonthlyReviewScreen
import com.pesatrack.presentation.screens.quarterly_review.QuarterlyReviewScreen
import com.pesatrack.presentation.screens.settings.SettingsScreen
import com.pesatrack.presentation.screens.weekly_review.WeeklyReviewScreen
import com.pesatrack.presentation.screens.year_in_review.YearInReviewScreen

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
                    navController.navigate(Screen.Analytics.createRoute())
                },
                onNavigateToAnalyticsByCategory = {
                    navController.navigate(
                        Screen.Analytics.createRoute(Screen.Analytics.SECTION_BY_CATEGORY)
                    )
                },
                onNavigateToYearlyGrid = {
                    navController.navigate(
                        Screen.Analytics.createRoute(Screen.Analytics.SECTION_YEARLY_GRID)
                    )
                },
                onNavigateToBudget = {
                    navController.navigate(Screen.Budget.route)
                },
                onNavigateToIncome = {
                    navController.navigate(Screen.Income.route)
                }
            )
        }

        // Analytics Screen
        composable(
            route = Screen.Analytics.route,
            arguments = listOf(
                navArgument(Screen.Analytics.ARG_SECTION) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val section = backStackEntry.arguments?.getString(Screen.Analytics.ARG_SECTION)
            AnalyticsScreen(
                initialSection = section,
                onNavigateToBudget = {
                    navController.navigate(Screen.Budget.route)
                },
                onNavigateToWeeklyReview = {
                    navController.navigate(Screen.WeeklyReview.createRoute())
                },
                onNavigateToMonthlyReview = {
                    navController.navigate(Screen.MonthlyReview.createRoute())
                },
                onNavigateToQuarterlyReview = {
                    navController.navigate(Screen.QuarterlyReview.createRoute())
                },
                onNavigateToYearInReview = {
                    navController.navigate(Screen.YearInReview.createRoute())
                },
                onNavigateToExpenseList = { /* categoryId filter — navigate to expenses */ _ ->
                    navController.navigate(Screen.Expenses.route)
                },
                onNavigateToCategorize = {
                    navController.navigate(Screen.BatchCategorize.route)
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
                },
                onNavigateToMerchants = {
                    navController.navigate(Screen.Merchants.route)
                }
            )
        }

        // Merchants (re-categorization) Screen
        composable(route = Screen.Merchants.route) {
            com.pesatrack.presentation.screens.merchants.MerchantsScreen(
                onNavigateBack = { navController.popBackStack() }
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

        // Categorize Income Screen (Income tracking Phase 2)
        composable(
            route = Screen.CategorizeIncome.route,
            arguments = listOf(
                navArgument("incomeId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val incomeId = backStackEntry.arguments?.getLong("incomeId") ?: 0L
            com.pesatrack.presentation.screens.categorize_income.CategorizeIncomeScreen(
                incomeId = incomeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Income List Screen (Income tracking Phase 3)
        composable(route = Screen.Income.route) {
            com.pesatrack.presentation.screens.income.IncomeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCategorizeIncome = { incomeId ->
                    navController.navigate(Screen.CategorizeIncome.createRoute(incomeId))
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
                },
                onNavigateToStatementImport = {
                    navController.navigate(Screen.StatementImport.route)
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

        // M-PESA Statement Import Screen
        composable(route = Screen.StatementImport.route) {
            StatementImportScreen(
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

        // Weekly Review (Insights & Reports v1.0)
        composable(
            route = Screen.WeeklyReview.route,
            arguments = listOf(
                navArgument(Screen.WeeklyReview.ARG_SNAPSHOT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getLong(Screen.WeeklyReview.ARG_SNAPSHOT_ID) ?: -1L
            WeeklyReviewScreen(
                snapshotId = if (rawId > 0L) rawId else null,
                onBack = { navController.popBackStack() }
            )
        }

        // Monthly Review (Insights & Reports v1.1)
        composable(
            route = Screen.MonthlyReview.route,
            arguments = listOf(
                navArgument(Screen.MonthlyReview.ARG_SNAPSHOT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getLong(Screen.MonthlyReview.ARG_SNAPSHOT_ID) ?: -1L
            MonthlyReviewScreen(
                snapshotId = if (rawId > 0L) rawId else null,
                onBack = { navController.popBackStack() }
            )
        }

        // Quarterly Review (Insights & Reports v1.3)
        composable(
            route = Screen.QuarterlyReview.route,
            arguments = listOf(
                navArgument(Screen.QuarterlyReview.ARG_SNAPSHOT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getLong(Screen.QuarterlyReview.ARG_SNAPSHOT_ID) ?: -1L
            QuarterlyReviewScreen(
                snapshotId = if (rawId > 0L) rawId else null,
                onBack = { navController.popBackStack() }
            )
        }

        // Year-in-Review (Insights & Reports v1.4)
        composable(
            route = Screen.YearInReview.route,
            arguments = listOf(
                navArgument(Screen.YearInReview.ARG_YEAR) {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val rawYear = backStackEntry.arguments?.getInt(Screen.YearInReview.ARG_YEAR) ?: -1
            YearInReviewScreen(
                year = if (rawYear > 0) rawYear else null,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
