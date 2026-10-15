package com.bilgec.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bilgec.app.settings.DarkMode

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
fun BilGecTheme(
    darkMode: DarkMode = DarkMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDarkColors = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkColors) DarkColors else LightColors,
        content = content,
    )
}

