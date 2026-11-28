package com.ryda.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val LightColorScheme = lightColorScheme(
    primary = MotoAmberLight,
    onPrimary = ChalkWhite,
    primaryContainer = SlateCardLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = TelemetryBlueLight,
    onSecondary = ChalkWhite,
    tertiary = ForestGreen,
    background = ChalkWhite,
    onBackground = TextPrimaryLight,
    surface = SlateCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSubtleLight,
    error = CrimsonRed
)

@Composable
fun RydaTheme(
    darkTheme: Boolean = true, // Default to dark motorcycle dashboard styling
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
