package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = KalensariGreenLightDark,
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFF86F8A8),
    secondary = Color(0xFF86D6A8),
    onSecondary = Color(0xFF00381F),
    background = KalensariDarkBg,
    surface = KalensariDarkSurface,
    surfaceVariant = KalensariDarkCard,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    tertiary = KalensariOrange
)

private val LightColorScheme = lightColorScheme(
    primary = KalensariGreen,
    onPrimary = Color.White,
    primaryContainer = KalensariLightGreen,
    onPrimaryContainer = KalensariDarkGreen,
    secondary = KalensariDarkGreen,
    onSecondary = Color.White,
    background = KalensariSurfaceLight,
    surface = KalensariCardLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = KalensariTextPrimary,
    onSurface = KalensariTextPrimary,
    tertiary = KalensariOrange
)

@Composable
fun KalensariStoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep Kalensari Store brand green vibrant
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
