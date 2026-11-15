package com.rabbeat.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.rabbeat.app.domain.model.AccentTheme

// Brand palette -------------------------------------------------------------

val BurrowPurple = Color(0xFFAF52DE)
val BurrowAmber = Color(0xFFFF9500)
val BurrowInk = Color(0xFF000000)
val BurrowSurface = Color(0xFF1C1C1E)
val BurrowSurfaceHigh = Color(0xFF2C2C2E)
val BurrowText = Color(0xFFFFFFFF)
val BurrowMuted = Color(0x99EBEBF5)
val BurrowMist = Color(0xFFF2F2F7)
val BurrowInkSoft = Color(0xFF000000)

/** iOS-style semantic palette used by every surface in the app. */
data class RabBeatPalette(
    val isDark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val card: Color,
    val elevated: Color,
    val fill: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val bar: Color,
)

val DarkRabBeatPalette = RabBeatPalette(
    isDark = true,
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    elevated = Color(0xFF1C1C1E),
    fill = Color(0x3D767680),
    separator = Color(0x99545458),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    bar = Color(0xF0161616),
)

val LightRabBeatPalette = RabBeatPalette(
    isDark = false,
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    elevated = Color(0xFFF2F2F7),
    fill = Color(0x1F767680),
    separator = Color(0x4A3C3C43),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    bar = Color(0xF0F9F9F9),
)

/** Accent data for a user selectable highlight colour. */
data class AccentPalette(
    val color: Color,
    val soft: Color,
    val glow: Color,
    val onColor: Color,
)

private fun accent(color: Color) = AccentPalette(
    color = color,
    soft = color.copy(alpha = 0.18f),
    glow = color.copy(alpha = 0.40f),
    onColor = Color.White,
)

fun AccentTheme.palette(dark: Boolean = false): AccentPalette = when (this) {
    AccentTheme.VIOLET -> accent(Color(0xFFAF52DE))
    AccentTheme.AMBER -> accent(Color(0xFFFF9500))
    AccentTheme.OCEAN -> accent(if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF))
    AccentTheme.MINT -> accent(if (dark) Color(0xFF30D158) else Color(0xFF34C759))
    AccentTheme.ROSE -> accent(Color(0xFFFA2D48))
}
