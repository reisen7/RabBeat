package com.rabbeat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rabbeat.app.domain.model.AppearanceSettings
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

/** True while a custom background image is behind the content. */
val LocalHasBackgroundImage = staticCompositionLocalOf { false }

/** How opaque cards are while a background image is visible (0 = clear, 1 = solid). */
val LocalCardOpacity = androidx.compose.runtime.compositionLocalOf { 0.3f }

@Composable
fun RabBeatBackground(
    appearance: AppearanceSettings,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    val uri = appearance.backgroundUri
    val hasImage = appearance.backgroundEnabled && !uri.isNullOrBlank()

    CompositionLocalProvider(
        LocalHasBackgroundImage provides hasImage,
        LocalCardOpacity provides appearance.cardOpacity,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(palette.background),
        ) {
            if (hasImage) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(
                            radius = appearance.backgroundBlur.dp,
                            edgeTreatment = BlurredEdgeTreatment.Rectangle,
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    palette.background.copy(alpha = appearance.backgroundDim * 0.72f),
                                    palette.background.copy(alpha = appearance.backgroundDim),
                                    palette.background.copy(alpha = (appearance.backgroundDim + 0.18f).coerceAtMost(0.96f)),
                                ),
                            ),
                        ),
                )
            }
            content()
        }
    }
}
