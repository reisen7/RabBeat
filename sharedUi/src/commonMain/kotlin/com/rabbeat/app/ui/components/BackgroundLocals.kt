package com.rabbeat.app.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/** True while a custom background image is behind the content. */
val LocalHasBackgroundImage = staticCompositionLocalOf { false }

/** How opaque cards are while a background image is visible (0 = clear, 1 = solid). */
val LocalCardOpacity = compositionLocalOf { 0.3f }
