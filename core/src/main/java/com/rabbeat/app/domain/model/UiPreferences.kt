package com.rabbeat.app.domain.model

/** User selectable highlight colour. */
enum class AccentTheme { VIOLET, AMBER, OCEAN, MINT, ROSE }

/** Everything the user can tweak about the look of the app. */
data class AppearanceSettings(
    val accent: AccentTheme = AccentTheme.ROSE,
    val backgroundUri: String? = null,
    val backgroundEnabled: Boolean = true,
    val backgroundBlur: Float = 22f,
    val backgroundDim: Float = 0.55f,
    val cardOpacity: Float = 0.3f,
)
