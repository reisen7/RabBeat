package com.rabbeat.app.ui.components

import android.content.Intent
import com.rabbeat.app.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rabbeat.app.R
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

/** Destinations exposed by the left drawer. */
enum class DrawerSection { Home, Library, Search, Profile, History, Downloads, Appearance, Settings }

/**
 * Left side sheet reachable from the hamburger button on the home header.
 * Mirrors the usual "sections + footer" music app layout.
 */
@Composable
fun AppDrawer(
    selected: DrawerSection?,
    onNavigate: (DrawerSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    val context = LocalContext.current
    var about by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = modifier.fillMaxWidth(0.86f),
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        drawerContainerColor = if (LocalHasBackgroundImage.current) {
            palette.elevated.copy(alpha = 0.96f)
        } else {
            palette.background
        },
        drawerTonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 24.dp, bottom = 8.dp),
            ) {
                DrawerGroup(stringResource(R.string.drawer_browse)) {
                    DrawerItem(DrawerSection.Home, Icons.Filled.Home, stringResource(R.string.nav_home), selected, onNavigate)
                    DrawerItem(DrawerSection.Library, Icons.Filled.LibraryMusic, stringResource(R.string.nav_library), selected, onNavigate)
                    DrawerItem(DrawerSection.Search, Icons.Filled.Search, stringResource(R.string.nav_search), selected, onNavigate)
                }
                DrawerGroup(stringResource(R.string.drawer_mine)) {
                    DrawerItem(DrawerSection.Profile, Icons.Filled.Person, stringResource(R.string.nav_profile), selected, onNavigate)
                    DrawerItem(DrawerSection.History, Icons.Filled.History, stringResource(R.string.home_history), selected, onNavigate)
                    DrawerItem(DrawerSection.Downloads, Icons.Filled.Download, stringResource(R.string.settings_downloads), selected, onNavigate)
                }
                DrawerGroup(stringResource(R.string.drawer_settings_group)) {
                    DrawerItem(DrawerSection.Appearance, Icons.Filled.Palette, stringResource(R.string.settings_personalize_row), selected, onNavigate)
                    DrawerItem(DrawerSection.Settings, Icons.Filled.Settings, stringResource(R.string.nav_settings), selected, onNavigate)
                }
            }

            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.separator))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = palette.secondaryLabel,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.drawer_go_home),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryLabel,
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.drawer_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.tertiaryLabel,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable { about = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = accent.color,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            stringResource(R.string.drawer_about),
                            style = MaterialTheme.typography.labelMedium,
                            color = accent.color,
                        )
                    }
                }
            }
        }
    }

    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text(stringResource(R.string.drawer_about)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.drawer_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.drawer_about_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { about = false }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun DrawerGroup(title: String, content: @Composable () -> Unit) {
    val palette = LocalRabBeatPalette.current
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryLabel,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun DrawerItem(
    section: DrawerSection,
    icon: ImageVector,
    label: String,
    selected: DrawerSection?,
    onNavigate: (DrawerSection) -> Unit,
) {
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val active = selected == section
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) palette.fill else Color.Transparent)
            .clickable { onNavigate(section) }
            .padding(horizontal = 10.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent.color,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.label,
            modifier = Modifier.weight(1f),
        )
    }
}
