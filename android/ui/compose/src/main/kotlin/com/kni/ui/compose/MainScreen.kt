package com.kni.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kni.ui.compose.navigation.KniNavigation
import com.kni.ui.compose.screens.LogItemData
import com.kni.ui.compose.theme.KniTheme

@Composable
fun MainScreen(
    isCapturing: Boolean,
    searchQuery: String,
    transactions: List<LogItemData>,
    onSearchQueryChanged: (String) -> Unit,
    onToggleCapture: () -> Unit
) {
    KniTheme {
        KniNavigation(
            isCapturing = isCapturing,
            searchQuery = searchQuery,
            transactions = transactions,
            onSearchQueryChanged = onSearchQueryChanged,
            onToggleCapture = onToggleCapture
        )
    }
}
