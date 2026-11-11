package com.rabbeat.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

/** Elevated surfaces go slightly translucent while a custom background image is visible. */
@Composable
fun adaptiveSurface(color: Color, alpha: Float = 0.92f): Color =
    if (LocalHasBackgroundImage.current) color.copy(alpha = alpha) else color

@Composable
fun groupedPageBackground(): Color =
    if (LocalHasBackgroundImage.current) Color.Transparent else LocalRabBeatPalette.current.groupedBackground

/** Flat surface; the default look is the iOS "elevated" material. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = adaptiveSurface(LocalRabBeatPalette.current.elevated),
    borderColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var base = modifier.clip(shape).background(color)
    if (borderColor != Color.Transparent) {
        base = base.border(androidx.compose.foundation.BorderStroke(0.5.dp, borderColor), shape)
    }
    if (onClick != null) base = base.clickable(onClick = onClick)
    Box(base, content = content)
}

/** iOS inset-grouped list container. */
@Composable
fun GlassGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = adaptiveSurface(LocalRabBeatPalette.current.card),
    ) {
        Column(content = content)
    }
}

/** Small caption header above an inset grouped list. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalRabBeatPalette.current.secondaryLabel,
        modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 24.dp, bottom = 6.dp),
    )
}

/** 0.5dp hairline inset separator used between grouped rows. */
@Composable
fun GroupDivider(startIndent: Dp = 60.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(0.5.dp)
            .background(LocalRabBeatPalette.current.separator),
    )
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    eyebrow: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) leading() else Spacer(Modifier.width(44.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalRabBeatPalette.current.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val accent = LocalAccentPalette.current
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = accent.onColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = accent.color,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .let {
                if (onAction != null) {
                    it.clickable(onClick = onAction).semantics {
                        if (actionLabel != null) contentDescription = actionLabel
                    }
                } else {
                    it
                }
            }
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onAction != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = actionLabel,
                tint = LocalRabBeatPalette.current.secondaryLabel,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** iOS segmented control; falls back to scrollable chips above four tabs. */
@Composable
fun PillTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    if (tabs.size <= 4) {
        BoxWithConstraints(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(palette.fill)
                .padding(2.dp),
        ) {
            val segmentWidth = maxWidth / tabs.size
            val thumbOffset by animateDpAsState(segmentWidth * selectedIndex, label = "seg-offset")
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(1.dp, RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (palette.isDark) Color(0xFF636366) else Color.White),
            )
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    Box(
                        Modifier
                            .width(segmentWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onSelected(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = palette.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (selected) palette.label else palette.fill)
                        .clickable { onSelected(index) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) palette.background else palette.label,
                    )
                }
            }
        }
    }
}

@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tonal: Boolean = false,
) {
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = when {
            !enabled -> palette.fill
            tonal -> palette.fill
            else -> accent.color
        },
        contentColor = when {
            !enabled -> palette.tertiaryLabel
            tonal -> accent.color
            else -> accent.onColor
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun GlassPill(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
) {
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    Row(
        modifier
            .clip(CircleShape)
            .background(if (active) accent.soft else palette.fill)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (active) accent.color else palette.secondaryLabel,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) accent.color else palette.secondaryLabel,
        )
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconColor: Color = LocalAccentPalette.current.color,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalRabBeatPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp)
            .heightIn(min = 44.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(29.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.tertiaryLabel,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Tiny animated bars shown next to the currently playing track. */
@Composable
fun EqualizerBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalAccentPalette.current.color,
    height: Dp = 14.dp,
    barWidth: Dp = 3.dp,
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val a by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a",
    )
    val b by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(680, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b",
    )
    val c by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(440, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "c",
    )
    Canvas(modifier.size(width = barWidth * 5, height = height)) {
        val width = barWidth.toPx()
        val gap = width
        val maxH = size.height
        val factors = if (playing) listOf(a, b, c) else listOf(0.28f, 0.5f, 0.34f)
        factors.forEachIndexed { index, factor ->
            val h = maxH * factor
            drawRoundRect(
                color = tint,
                topLeft = Offset(index * (width + gap), maxH - h),
                size = Size(width, h),
                cornerRadius = CornerRadius(width / 2f, width / 2f),
            )
        }
    }
}

@Composable
fun StatPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val palette = LocalRabBeatPalette.current
    Row(
        modifier
            .clip(CircleShape)
            .background(palette.fill)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = palette.secondaryLabel,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryLabel,
        )
    }
}
