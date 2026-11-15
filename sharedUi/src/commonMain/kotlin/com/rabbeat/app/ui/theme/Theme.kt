package com.rabbeat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.ui.SystemBarsEffect

val LocalRabBeatPalette = staticCompositionLocalOf { DarkRabBeatPalette }
val LocalAccentPalette = staticCompositionLocalOf { AccentTheme.ROSE.palette() }

val RabBeatShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private fun darkScheme(accent: AccentPalette, palette: RabBeatPalette) = darkColorScheme(
    primary = accent.color,
    onPrimary = accent.onColor,
    primaryContainer = accent.color.copy(alpha = 0.22f),
    onPrimaryContainer = accent.color,
    secondary = accent.color,
    onSecondary = accent.onColor,
    secondaryContainer = accent.soft,
    onSecondaryContainer = accent.color,
    tertiary = BurrowAmber,
    onTertiary = BurrowInk,
    background = palette.background,
    onBackground = palette.label,
    surface = palette.background,
    onSurface = palette.label,
    surfaceVariant = palette.elevated,
    onSurfaceVariant = palette.secondaryLabel,
    surfaceContainerLowest = palette.card,
    surfaceContainerLow = palette.elevated,
    surfaceContainer = palette.elevated,
    surfaceContainerHigh = palette.elevated,
    surfaceContainerHighest = palette.elevated,
    outline = palette.separator,
    outlineVariant = palette.separator,
    error = Color(0xFFFF453A),
    onError = Color.White,
)

private fun lightScheme(accent: AccentPalette, palette: RabBeatPalette) = lightColorScheme(
    primary = accent.color,
    onPrimary = accent.onColor,
    primaryContainer = accent.soft,
    onPrimaryContainer = accent.color,
    secondary = accent.color,
    onSecondary = Color.White,
    secondaryContainer = accent.soft,
    onSecondaryContainer = accent.color,
    tertiary = Color(0xFFC2410C),
    onTertiary = Color.White,
    background = palette.background,
    onBackground = palette.label,
    surface = palette.background,
    onSurface = palette.label,
    surfaceVariant = palette.elevated,
    onSurfaceVariant = palette.secondaryLabel,
    surfaceContainerLowest = palette.card,
    surfaceContainerLow = palette.elevated,
    surfaceContainer = palette.elevated,
    surfaceContainerHigh = palette.elevated,
    surfaceContainerHighest = palette.elevated,
    outline = palette.separator,
    outlineVariant = palette.separator,
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

@Composable
fun RabBeatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentTheme: AccentTheme = AccentTheme.ROSE,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val accent = accentTheme.palette(dark)
    val palette = if (dark) DarkRabBeatPalette else LightRabBeatPalette
    val colors = if (dark) darkScheme(accent, palette) else lightScheme(accent, palette)
    SystemBarsEffect(dark)
    CompositionLocalProvider(
        LocalRabBeatPalette provides palette,
        LocalAccentPalette provides accent,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = RabBeatTypography,
            shapes = RabBeatShapes,
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
                content()
            }
        }
    }
}
