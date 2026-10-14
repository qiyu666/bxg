package com.qiyu.safe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F0E6),
    onPrimaryContainer = Color(0xFF00332C),
    secondary = Color(0xFF4A5A56),
    secondaryContainer = Color(0xFFD3E5DF),
    background = Color(0xFFF6F9F8),
    surface = Color.White,
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FDCCB),
    onPrimary = Color(0xFF003630),
    secondary = Color(0xFFB0C4BE)
)

@Composable
fun VaultTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
