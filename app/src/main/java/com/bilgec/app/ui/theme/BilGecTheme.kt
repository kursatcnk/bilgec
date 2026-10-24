package com.bilgec.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bilgec.app.settings.DarkMode

val BilGecRed = Color(0xFFF04438)
val BilGecInk = Color(0xFF171513)
val BilGecCream = Color(0xFFFAF7F2)
val BilGecMist = Color(0xFFECE6DF)
val BilGecSuccess = Color(0xFF14805A)
val BilGecMidnight = Color(0xFF21142E)
val BilGecPeach = Color(0xFFFFD8C7)

private val LightColors = lightColorScheme(
    primary = BilGecRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0D9),
    onPrimaryContainer = Color(0xFF5A120C),
    secondary = BilGecMidnight,
    onSecondary = Color.White,
    background = BilGecCream,
    onBackground = BilGecInk,
    surface = Color(0xFFFFFCF9),
    onSurface = BilGecInk,
    surfaceVariant = Color(0xFFF0EAE4),
    onSurfaceVariant = Color(0xFF665F59),
    outline = Color(0xFFC8BDB5),
    outlineVariant = Color(0xFFE7DED6),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000B),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFF1EAE4),
    onSecondary = BilGecInk,
    background = Color(0xFF100D13),
    onBackground = Color(0xFFF2ECE6),
    surface = Color(0xFF1C181F),
    onSurface = Color(0xFFF2ECE6),
    surfaceVariant = Color(0xFF2B252D),
    onSurfaceVariant = Color(0xFFD1C8C0),
    outline = Color(0xFF958D86),
    outlineVariant = Color(0xFF49443F),
    error = Color(0xFFFFB4AB),
)

private val BilGecTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.45).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.7.sp,
    ),
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
        typography = BilGecTypography,
        content = content,
    )
}
