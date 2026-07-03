package com.kni.ui.compose

import androidx.compose.runtime.Composable
import com.kni.ui.compose.navigation.KniNavigation
import com.kni.ui.compose.screens.LogItemData
import com.kni.ui.compose.screens.ScreenHooks
import com.kni.ui.compose.theme.KniTheme

@Composable
fun MainScreen(
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
    KniTheme {
        KniNavigation(
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
            hooks = hooks
        )
    }
}
