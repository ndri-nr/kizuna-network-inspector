package com.kni.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = KniAccent,
    background = KniBgPrimary,
    surface = KniBgSurface,
    onPrimary = KniTextPrimary,
    onBackground = KniTextPrimary,
    onSurface = KniTextPrimary,
    secondary = KniTextSecondary,
    error = KniError
)

private val LightColorScheme = lightColorScheme(
    primary = KniAccent,
    background = KniTextPrimary,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = KniBgPrimary,
    onSurface = KniBgPrimary,
    secondary = KniTextSecondary,
    error = KniError
)

@Composable
fun KniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
