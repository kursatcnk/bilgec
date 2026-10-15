package com.bilgec.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BD8),
    secondary = Color(0xFF6750A4),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC6BFFF),
    secondary = Color(0xFFCBC2DB),
    background = Color(0xFF131318),
    surface = Color(0xFF1B1B21),
)

@Composable
fun BilGecTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

