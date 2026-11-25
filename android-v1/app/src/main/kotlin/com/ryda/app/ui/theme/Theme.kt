package com.ryda.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SafetyAmber,
    onPrimary = AsphaltBlack,
    primaryContainer = SurfaceCard,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricCyan,
    onSecondary = AsphaltBlack,
    tertiary = HighVisLime,
    background = AsphaltBlack,
    onBackground = TextPrimary,
    surface = CarbonDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = CrimsonRed
)

@Composable
fun RydaTheme(
    darkTheme: Boolean = true, // Default to dark motorcycle dashboard styling
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
