package com.kni.ui.compose.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kni.ui.compose.screens.*

@Composable
fun KniNavigation(
    isCapturing: Boolean,
    searchQuery: String,
    transactions: List<LogItemData>,
    selectedMethods: Set<String>,
    selectedHosts: Set<String>,
    onSearchQueryChanged: (String) -> Unit,
    onSelectedMethodsChanged: (Set<String>) -> Unit,
    onSelectedHostsChanged: (Set<String>) -> Unit,
    onResetFilters: () -> Unit,
    onDeleteTransactions: (List<String>) -> Unit,
    onClearAllTransactions: () -> Unit,
    onToggleCapture: () -> Unit,
    hooks: ScreenHooks
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "feed") {
        composable("feed") {
            FeedScreen(
                isCapturing = isCapturing,
                searchQuery = searchQuery,
                transactions = transactions,
                selectedMethods = selectedMethods,
                selectedHosts = selectedHosts,
                onSearchQueryChanged = onSearchQueryChanged,
                onSelectedMethodsChanged = onSelectedMethodsChanged,
                onSelectedHostsChanged = onSelectedHostsChanged,
                onResetFilters = onResetFilters,
                onDeleteTransactions = onDeleteTransactions,
                onClearAllTransactions = onClearAllTransactions,
                onToggleCapture = onToggleCapture,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToDiagnostics = { navController.navigate("diagnostics") },
                onNavigateToDetail = { id -> navController.navigate("detail/$id") }
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() }, hooks = hooks.settings)
        }
        composable("diagnostics") {
            DiagnosticsScreen(
                onBack = { navController.popBackStack() },
                loadDiagnostics = hooks.loadDiagnostics
            )
        }
        composable(
            route = "detail/{exchangeId}",
            arguments = listOf(navArgument("exchangeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val exchangeId = backStackEntry.arguments?.getString("exchangeId") ?: ""
            DetailScreen(
                exchangeId = exchangeId,
                onBack = { navController.popBackStack() },
                loadDetail = hooks.loadDetail
            )
        }
    }
}
