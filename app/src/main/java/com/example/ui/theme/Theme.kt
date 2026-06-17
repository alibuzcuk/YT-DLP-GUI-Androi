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

private val ImmersiveDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFFE8DEF8),
    onSecondary = Color(0xFF4A4458),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF2B2930),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF4A4458),
    onSurfaceVariant = Color(0xFF938F99),
    outline = Color(0xFF4A4458)
)

private val ImmersiveLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFF7F2FA),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true for premium immersive dark experience
    dynamicColor: Boolean = false, // Set to false to preserve exact design colors
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ImmersiveDarkColorScheme else ImmersiveLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

