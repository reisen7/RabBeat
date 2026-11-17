package com.rabbeat.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

data class BottomTab(val label: String, val icon: ImageVector)

@Composable
fun RabBeatBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val inactive = if (palette.isDark) Color(0xFF8E8E93) else Color(0xFF999999)
    Column(modifier.fillMaxWidth().background(palette.bar)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.separator),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(49.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) accent.color else inactive,
                    label = "tab-tint",
                )
                val pillAlpha by animateFloatAsState(
                    if (selected) 1f else 0f,
                    animationSpec = tween(200),
                    label = "tab-pill",
                )
                // Slight overshoot on select; springs back to resting size.
                val iconScale by animateFloatAsState(
                    if (selected) 1.14f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "tab-icon",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .height(49.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .width(46.dp)
                                .height(26.dp)
                                .graphicsLayer { alpha = pillAlpha }
                                .clip(CircleShape)
                                .background(accent.soft),
                        )
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                    }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
