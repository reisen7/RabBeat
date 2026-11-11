package com.rabbeat.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.AppearanceSettings
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.AppDrawer
import com.rabbeat.app.ui.components.BottomTab
import com.rabbeat.app.ui.components.BrandMark
import com.rabbeat.app.ui.components.DrawerSection
import com.rabbeat.app.ui.components.MiniPlayerBar
import com.rabbeat.app.ui.components.RabBeatBackground
import com.rabbeat.app.ui.components.RabBeatBottomBar
import com.rabbeat.app.ui.screens.appearance.AppearanceScreen
import com.rabbeat.app.ui.screens.detail.AlbumDetailScreen
import com.rabbeat.app.ui.screens.detail.ArtistDetailScreen
import com.rabbeat.app.ui.screens.detail.SourceDetailScreen
import com.rabbeat.app.ui.screens.downloads.DownloadsScreen
import com.rabbeat.app.ui.screens.history.HistoryScreen
import com.rabbeat.app.ui.screens.home.HomeScreen
import com.rabbeat.app.ui.screens.library.LibraryScreen
import com.rabbeat.app.ui.screens.onboarding.OnboardingScreen
import com.rabbeat.app.ui.screens.player.PlayerScreen
import com.rabbeat.app.ui.screens.playlist.PlaylistDetailScreen
import com.rabbeat.app.ui.screens.playlist.PlaylistsScreen
import com.rabbeat.app.ui.screens.profile.ProfileScreen
import com.rabbeat.app.ui.screens.queue.QueueScreen
import com.rabbeat.app.ui.screens.search.SearchScreen
import com.rabbeat.app.ui.screens.settings.SettingsScreen
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import com.rabbeat.app.ui.theme.RabBeatTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable data object Onboarding : Route
    @Serializable data object Home : Route
    @Serializable data object Library : Route
    @Serializable data object Search : Route
    @Serializable data object Profile : Route
    @Serializable data object Player : Route
    @Serializable data object Queue : Route
    @Serializable data object Downloads : Route
    @Serializable data object History : Route
    @Serializable data object Settings : Route
    @Serializable data object Appearance : Route
    @Serializable data class AlbumDetail(val albumId: String) : Route
    @Serializable data class ArtistDetail(val artistId: String) : Route
    @Serializable data class SourceDetail(val sourceId: String) : Route
    @Serializable data object Playlists : Route
    @Serializable data class PlaylistDetail(val playlistId: String) : Route
}

@HiltViewModel
class AppShellViewModel @Inject constructor(
    settings: SettingsRepository,
    val player: PlayerController,
) : ViewModel() {
    val theme = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val appearance = settings.appearance.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppearanceSettings(),
    )
    val playback = player.state
    val onboardingDone = settings.onboardingDone.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
}

@Composable
fun RabBeatApp(vm: AppShellViewModel = hiltViewModel()) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    RabBeatTheme(themeMode = theme, accentTheme = appearance.accent) {
        RabBeatBackground(appearance) {
            val done = onboardingDone
            if (done == null) {
                RabBeatSplash()
            } else {
                val start = remember { if (done) Route.Home else Route.Onboarding }
                RabBeatScaffold(shell = vm, startDestination = start)
            }
        }
    }
}

@Composable
private fun RabBeatSplash() {
    Box(
        Modifier
            .fillMaxSize()
            .background(LocalRabBeatPalette.current.background),
        contentAlignment = Alignment.Center,
    ) {
        BrandMark(Modifier.size(96.dp))
    }
}

@Composable
private fun RabBeatScaffold(shell: AppShellViewModel, startDestination: Route) {
    val navController = rememberNavController()
    val playback by shell.playback.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val immersive = destination.isImmersive()
    val showMini = !immersive && playback.currentTrack != null
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val palette = LocalRabBeatPalette.current

    val tabs = listOf(
        BottomTab(stringResource(R.string.nav_home), Icons.Filled.Home),
        BottomTab(stringResource(R.string.nav_library), Icons.Filled.LibraryMusic),
        BottomTab(stringResource(R.string.nav_search), Icons.Filled.Search),
        BottomTab(stringResource(R.string.nav_profile), Icons.Filled.Person),
    )
    val selected = destination.tabIndex()

    fun openTab(route: Route) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openPage(route: Route) {
        navController.navigate(route) { launchSingleTop = true }
    }

    fun onDrawerNavigate(section: DrawerSection) {
        scope.launch { drawerState.close() }
        when (section) {
            DrawerSection.Home -> openTab(Route.Home)
            DrawerSection.Library -> openTab(Route.Library)
            DrawerSection.Search -> openTab(Route.Search)
            DrawerSection.Profile -> openTab(Route.Profile)
            DrawerSection.History -> openPage(Route.History)
            DrawerSection.Downloads -> openPage(Route.Downloads)
            DrawerSection.Appearance -> openPage(Route.Appearance)
            DrawerSection.Settings -> openPage(Route.Settings)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !immersive,
        scrimColor = if (palette.isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.30f),
        drawerContent = {
            AppDrawer(selected = destination.drawerSection(), onNavigate = { onDrawerNavigate(it) })
        },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (!immersive) {
                    Column {
                        if (showMini) {
                            MiniPlayerBar(
                                state = playback,
                                onOpen = { navController.navigate(Route.Player) },
                                onToggle = shell.player::toggle,
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                                onNext = shell.player::next,
                            )
                        }
                        RabBeatBottomBar(
                            tabs = tabs,
                            selectedIndex = selected,
                            onSelected = { index ->
                                val route = when (index) {
                                    0 -> Route.Home
                                    1 -> Route.Library
                                    2 -> Route.Search
                                    else -> Route.Profile
                                }
                                openTab(route)
                            },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = if (immersive) Modifier else Modifier.padding(padding),
            ) {
                composable<Route.Onboarding> {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate(Route.Home) {
                                popUpTo<Route.Onboarding> { inclusive = true }
                            }
                        },
                    )
                }
                composable<Route.Home> {
                    HomeScreen(
                        onMenu = { scope.launch { drawerState.open() } },
                        onOpenPlayer = { navController.navigate(Route.Player) },
                        onImport = { navController.navigate(Route.Settings) },
                        onHistory = { navController.navigate(Route.History) },
                    )
                }
                composable<Route.Library> {
                    LibraryScreen(
                        onMenu = { scope.launch { drawerState.open() } },
                        onAlbum = { navController.navigate(Route.AlbumDetail(it)) },
                        onArtist = { navController.navigate(Route.ArtistDetail(it)) },
                    )
                }
                composable<Route.Search> {
                    SearchScreen(
                        onMenu = { scope.launch { drawerState.open() } },
                        onAlbum = { navController.navigate(Route.AlbumDetail(it)) },
                        onArtist = { navController.navigate(Route.ArtistDetail(it)) },
                    )
                }
                composable<Route.Profile> {
                    ProfileScreen(
                        onMenu = { scope.launch { drawerState.open() } },
                        onPlaylist = { navController.navigate(Route.PlaylistDetail(it)) },
                        onPlaylists = { navController.navigate(Route.Playlists) },
                        onHistory = { navController.navigate(Route.History) },
                        onSettings = { navController.navigate(Route.Settings) },
                    )
                }
                composable<Route.Playlists> {
                    PlaylistsScreen(
                        onBack = { navController.popBackStack() },
                        onPlaylist = { navController.navigate(Route.PlaylistDetail(it)) },
                    )
                }
                composable<Route.Settings> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onDownloads = { navController.navigate(Route.Downloads) },
                        onHistory = { navController.navigate(Route.History) },
                        onAppearance = { navController.navigate(Route.Appearance) },
                    )
                }
                composable<Route.Appearance> {
                    AppearanceScreen(onBack = { navController.popBackStack() })
                }
                composable<Route.Player> {
                    PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onQueue = { navController.navigate(Route.Queue) },
                    )
                }
                composable<Route.Queue> { QueueScreen(onBack = { navController.popBackStack() }) }
                composable<Route.Downloads> { DownloadsScreen(onBack = { navController.popBackStack() }) }
                composable<Route.History> { HistoryScreen(onBack = { navController.popBackStack() }) }
                composable<Route.AlbumDetail> { backStack ->
                    AlbumDetailScreen(
                        albumId = backStack.toRoute<Route.AlbumDetail>().albumId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Route.ArtistDetail> { backStack ->
                    val route = backStack.toRoute<Route.ArtistDetail>()
                    ArtistDetailScreen(
                        artistId = route.artistId,
                        onBack = { navController.popBackStack() },
                        onAlbum = { navController.navigate(Route.AlbumDetail(it)) },
                    )
                }
                composable<Route.SourceDetail> { backStack ->
                    SourceDetailScreen(
                        sourceId = backStack.toRoute<Route.SourceDetail>().sourceId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Route.PlaylistDetail> { backStack ->
                    PlaylistDetailScreen(
                        playlistId = backStack.toRoute<Route.PlaylistDetail>().playlistId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun NavDestination?.tabIndex(): Int = when {
    this == null -> 0
    hasRoute<Route.Home>() -> 0
    hasRoute<Route.Library>() ||
        hasRoute<Route.AlbumDetail>() ||
        hasRoute<Route.ArtistDetail>() -> 1
    hasRoute<Route.Search>() -> 2
    hasRoute<Route.Profile>() ||
        hasRoute<Route.PlaylistDetail>() ||
        hasRoute<Route.Settings>() ||
        hasRoute<Route.Downloads>() ||
        hasRoute<Route.History>() ||
        hasRoute<Route.Appearance>() -> 3
    else -> 0
}

private fun NavDestination?.drawerSection(): DrawerSection? = when {
    this == null -> null
    hasRoute<Route.Home>() -> DrawerSection.Home
    hasRoute<Route.Library>() ||
        hasRoute<Route.AlbumDetail>() ||
        hasRoute<Route.ArtistDetail>() -> DrawerSection.Library
    hasRoute<Route.Search>() -> DrawerSection.Search
    hasRoute<Route.Profile>() || hasRoute<Route.PlaylistDetail>() -> DrawerSection.Profile
    hasRoute<Route.History>() -> DrawerSection.History
    hasRoute<Route.Downloads>() -> DrawerSection.Downloads
    hasRoute<Route.Appearance>() -> DrawerSection.Appearance
    hasRoute<Route.Settings>() -> DrawerSection.Settings
    else -> null
}

private fun NavDestination?.isImmersive(): Boolean {
    if (this == null) return false
    return hasRoute<Route.Player>() || hasRoute<Route.Queue>() || hasRoute<Route.Onboarding>()
}
