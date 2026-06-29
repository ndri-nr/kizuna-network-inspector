package com.kni.ui.compose

import androidx.compose.runtime.Composable
import com.kni.ui.compose.navigation.KniNavigation
import com.kni.ui.compose.theme.KniTheme

@Composable
fun MainScreen() {
    KniTheme {
        KniNavigation()
    }
}
