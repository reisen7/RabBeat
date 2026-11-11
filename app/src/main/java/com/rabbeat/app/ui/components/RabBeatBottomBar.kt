package com.rabbeat.app.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
