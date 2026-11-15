package com.rabbeat.app.desktop.ui

import androidx.compose.ui.graphics.Color
import com.rabbeat.app.ui.theme.DarkRabBeatPalette

/** Slightly blue-tinted charcoal, lighter than the shared pure-black dark palette. */
val DesktopDarkPalette = DarkRabBeatPalette.copy(
    background = Color(0xFF1A1A20),
    groupedBackground = Color(0xFF1A1A20),
    card = Color(0xFF24242B),
    elevated = Color(0xFF2A2A32),
    bar = Color(0xF01F1F25),
    separator = Color(0x1FFFFFFF),
)
