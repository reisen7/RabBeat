package com.rabbeat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/** Applies light/dark system bar icons for the current theme. No-op on desktop. */
@Composable
expect fun SystemBarsEffect(dark: Boolean)

/** Resolves an `android.resource://…` artwork URI to a painter, or null when unsupported. */
@Composable
expect fun resourceArtworkPainter(artworkUri: String): Painter?
