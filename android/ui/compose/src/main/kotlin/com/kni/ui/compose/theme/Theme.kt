package com.kni.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A single light, green-forward scheme regardless of the system dark-mode setting,
// so the app has a consistent WhatsApp/Gojek look.
private val KniColorScheme = lightColorScheme(
    primary = KniAccent,
    onPrimary = Color.White,
    secondary = KniHeader,
    onSecondary = Color.White,
    background = KniBgPrimary,
    onBackground = KniTextPrimary,
    surface = KniBgSurface,
    onSurface = KniTextPrimary,
    surfaceVariant = KniBgPrimary,
    error = KniError,
    onError = Color.White,
)

@Composable
fun KniTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KniColorScheme,
        content = content
    )
}
