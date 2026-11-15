package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val NeonDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = MidnightBgTop,
    secondary = NeonMagenta,
    onSecondary = TextPrimary,
    tertiary = SolarGold,
    onTertiary = MidnightBgTop,
    background = MidnightBgTop,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    error = CrimsonDanger,
    onError = TextPrimary,
    outline = SurfaceBorder
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun NeonCatchTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonDarkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
