package com.rabbeat.app.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import com.rabbeat.app.ui.theme.RabBeatTypography
import java.awt.GraphicsEnvironment

/**
 * Desktop Skia draws the default face with grayscale edges, and CJK falls
 * through Segoe UI onto an unhinted fallback. Microsoft YaHei UI is the
 * Windows UI face, and subpixel smoothing matches ClearType.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun DesktopTypeTheme(content: @Composable () -> Unit) {
    val family = remember {
        val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val name = listOf("Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI")
            .firstOrNull { it in installed }
            ?: "Segoe UI"
        FontFamily(name)
    }
    val tuned = remember(family) {
        val raster = PlatformTextStyle(
            spanStyle = null,
            paragraphStyle = PlatformParagraphStyle(
                FontRasterizationSettings(
                    smoothing = FontSmoothing.SubpixelAntiAlias,
                    hinting = FontHinting.Normal,
                    subpixelPositioning = true,
                    autoHintingForced = false,
                ),
            ),
        )
        fun TextStyle.tune() = copy(
            fontFamily = family,
            fontSynthesis = FontSynthesis.None,
            platformStyle = raster,
        )
        val base = RabBeatTypography
        Typography(
            displayLarge = base.displayLarge.tune(),
            displayMedium = base.displayMedium.tune(),
            displaySmall = base.displaySmall.tune(),
            headlineLarge = base.headlineLarge.tune(),
            headlineMedium = base.headlineMedium.tune(),
            headlineSmall = base.headlineSmall.tune(),
            titleLarge = base.titleLarge.tune(),
            titleMedium = base.titleMedium.tune(),
            titleSmall = base.titleSmall.tune(),
            bodyLarge = base.bodyLarge.tune(),
            bodyMedium = base.bodyMedium.tune(),
            bodySmall = base.bodySmall.tune(),
            labelLarge = base.labelLarge.tune(),
            labelMedium = base.labelMedium.tune(),
            labelSmall = base.labelSmall.tune(),
        )
    }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = tuned,
        content = content,
    )
}
