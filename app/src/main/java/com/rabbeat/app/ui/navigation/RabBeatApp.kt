package com.rabbeat.app.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.AppearanceSettings
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.AppDrawer
import com.rabbeat.app.ui.components.BottomTab
import com.rabbeat.app.ui.components.DrawerSection
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.ui.components.MiniPlayerBar
import com.rabbeat.app.ui.components.RabBeatBackground
import com.rabbeat.app.ui.components.RabBeatBottomBar
import com.rabbeat.app.ui.screens.appearance.AppearanceScreen
import com.rabbeat.app.ui.screens.detail.AlbumDetailScreen
import com.rabbeat.app.ui.screens.detail.ArtistDetailScreen
import com.rabbeat.app.ui.screens.detail.SourceDetailScreen
import com.rabbeat.app.ui.screens.downloads.DownloadsScreen
import com.rabbeat.app.ui.screens.hidden.HiddenTracksScreen
import com.rabbeat.app.ui.screens.history.HistoryScreen
import com.rabbeat.app.ui.screens.recent.RecentlyAddedScreen
import com.rabbeat.app.ui.screens.home.HomeScreen
import com.rabbeat.app.ui.screens.library.LibraryScreen
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Library : Route
    @Serializable data object Search : Route
    @Serializable data object Profile : Route
    @Serializable data object Player : Route
    @Serializable data object Queue : Route
    @Serializable data object Downloads : Route
    @Serializable data object History : Route
    @Serializable data object HiddenTracks : Route
    @Serializable data object RecentlyAdded : Route
    @Serializable data object Settings : Route
    @Serializable data object Appearance : Route
    @Serializable data class AlbumDetail(val albumId: String) : Route
    @Serializable data class ArtistDetail(val artistId: String) : Route
    @Serializable data class SourceDetail(val sourceId: String) : Route
    @Serializable data object Playlists : Route
    @Serializable data class PlaylistDetail(val playlistId: String) : Route
}

// --- Navigation transitions -------------------------------------------------
// Tabs cross-fade with a tiny settle scale; pushed pages slide horizontally;
// immersive sheets (Player/Queue) slide vertically from the bottom.

private typealias Transition = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitTransition_ = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

// Direction-aware tab swipe: moving to a higher-index tab slides left, going
// back slides right. A quarter-width offset keeps it snappy without a full
// page-width travel. The slide only applies between real tabs — pushing a
// detail page fades the tab out (the pushed page owns its own slide-in),
// and popping back to a tab always enters from the left.
private fun NavDestination?.isTab(): Boolean =
    this?.let {
        it.hasRoute<Route.Home>() || it.hasRoute<Route.Library>() ||
            it.hasRoute<Route.Search>() || it.hasRoute<Route.Profile>()
    } == true

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabForward(): Boolean =
    initialState.destination.isTab() && targetState.destination.isTab() &&
        targetState.destination.tabIndex() >= initialState.destination.tabIndex()

private val tabEnter: Transition = {
    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) {
        if (initialState.destination.isTab()) {
            if (tabForward()) it / 4 else -it / 4
        } else {
            -it / 4
        }
    } + fadeIn(tween(200))
}
private val tabExit: ExitTransition_ = {
    if (targetState.destination.isTab()) {
        slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) {
            if (tabForward()) -it / 4 else it / 4
        } + fadeOut(tween(180))
    } else {
        fadeOut(tween(160))
    }
}

private val pushEnter: Transition = {
    slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220))
}
private val pushExit: ExitTransition_ = {
    slideOutHorizontally(tween(220)) { -it / 5 } + fadeOut(tween(160))
}
private val popEnter: Transition = {
    slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 5 } + fadeIn(tween(220))
}
private val popExit: ExitTransition_ = {
    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200))
}

private val sheetEnter: Transition = {
    slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180))
}
private val sheetExit: ExitTransition_ = { fadeOut(tween(140)) }
private val sheetPopEnter: Transition = { fadeIn(tween(140)) }
private val sheetPopExit: ExitTransition_ = {
    slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180))
}

private inline fun <reified T : Route> NavGraphBuilder.tabPage(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T>(
    enterTransition = tabEnter,
    exitTransition = tabExit,
    popEnterTransition = tabEnter,
    popExitTransition = tabExit,
    content = content,
)

private inline fun <reified T : Route> NavGraphBuilder.pushPage(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T>(
    enterTransition = pushEnter,
    exitTransition = pushExit,
    popEnterTransition = popEnter,
    popExitTransition = popExit,
    content = content,
)

private inline fun <reified T : Route> NavGraphBuilder.sheetPage(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T>(
    enterTransition = sheetEnter,
    exitTransition = sheetExit,
    popEnterTransition = sheetPopEnter,
    popExitTransition = sheetPopExit,
    content = content,
)

@HiltViewModel
class AppShellViewModel @Inject constructor(
    settings: SettingsRepository,
    val player: PlaybackController,
) : ViewModel() {
    // null = still loading; the splash stays up until the first real emission
    // so the screen never flashes default theme/black before user settings arrive.
    val theme = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as ThemeMode?)
    val appearance = settings.appearance.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null as AppearanceSettings?,
    )
    val playback = player.state
}

@Composable
fun RabBeatApp(vm: AppShellViewModel = hiltViewModel()) {
    // Defer player init until the splash and first content frame are on screen;
    // ExoPlayer + MediaSession construction costs a few hundred ms on main.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        vm.player.warmUp()
    }
    val theme by vm.theme.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    // Reveal the UI only once appearance is loaded and — when a background image
    // is configured — its first decoded frame is on screen, so there's no
    // black-then-image flash. Timeout keeps a broken/slow image from trapping us.
    var backdropReady by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(appearance) {
        delay(1500)
        timedOut = true
    }
    val loaded = appearance != null && theme != null
    val needsBackdrop = appearance?.let { it.backgroundEnabled && !it.backgroundUri.isNullOrBlank() } == true
    val ready = loaded && (!needsBackdrop || backdropReady || timedOut)
    RabBeatTheme(themeMode = theme ?: ThemeMode.SYSTEM, accentTheme = appearance?.accent ?: AccentTheme.ROSE) {
        RabBeatBackground(appearance ?: AppearanceSettings(), onBackdropReady = { backdropReady = true }) {
            if (ready) {
                RabBeatScaffold(shell = vm, startDestination = Route.Home)
            } else {
                LoadingState(Modifier.fillMaxSize())
            }
        }
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
                                onPrev = shell.player::previous,
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
                tabPage<Route.Home> {
                    HomeScreen(
                        onMenu = { scope.launch { drawerState.open() } },
                        onOpenPlayer = { navController.navigate(Route.Player) },
                        onImport = { navController.navigate(Route.Settings) },
                        onHistory = { navController.navigate(Route.History) },
                        onRecent = { navController.navigate(Route.RecentlyAdded) },
                    )
                }
                tabPage<Route.Library> {
                    LibraryScreen(
                        onMenu = { scope.launch { drawerState.open() } },
                        onAlbum = { navController.navigate(Route.AlbumDetail(it)) },
                        onArtist = { navController.navigate(Route.ArtistDetail(it)) },
                    )
                }
                tabPage<Route.Search> {
                    SearchScreen(
                        onMenu = { scope.launch { drawerState.open() } },
                        onAlbum = { navController.navigate(Route.AlbumDetail(it)) },
                        onArtist = { navController.navigate(Route.ArtistDetail(it)) },
                    )
                }
                tabPage<Route.Profile> {
                    ProfileScreen(
                        onMenu = { scope.launch { drawerState.open() } },
                        onPlaylist = { navController.navigate(Route.PlaylistDetail(it)) },
                        onPlaylists = { navController.navigate(Route.Playlists) },
                        onHistory = { navController.navigate(Route.History) },
                        onSettings = { navController.navigate(Route.Settings) },
                    )
                }
                pushPage<Route.Playlists> {
                    PlaylistsScreen(
                        onBack = { navController.popBackStack() },
                        onPlaylist = { navController.navigate(Route.PlaylistDetail(it)) },
                    )
                }
                pushPage<Route.Settings> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onDownloads = { navController.navigate(Route.Downloads) },
                        onHistory = { navController.navigate(Route.History) },
                        onAppearance = { navController.navigate(Route.Appearance) },
                        onHidden = { navController.navigate(Route.HiddenTracks) },
                    )
                }
                pushPage<Route.HiddenTracks> { HiddenTracksScreen(onBack = { navController.popBackStack() }) }
                pushPage<Route.RecentlyAdded> { RecentlyAddedScreen(onBack = { navController.popBackStack() }) }
                pushPage<Route.Appearance> {
                    AppearanceScreen(onBack = { navController.popBackStack() })
                }
                sheetPage<Route.Player> {
                    PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onQueue = { navController.navigate(Route.Queue) },
                    )
                }
                sheetPage<Route.Queue> { QueueScreen(onBack = { navController.popBackStack() }) }
                pushPage<Route.Downloads> { DownloadsScreen(onBack = { navController.popBackStack() }) }
                pushPage<Route.History> { HistoryScreen(onBack = { navController.popBackStack() }) }
                pushPage<Route.AlbumDetail> { backStack ->
                    AlbumDetailScreen(
                        albumId = backStack.toRoute<Route.AlbumDetail>().albumId,
                        onBack = { navController.popBackStack() },
                    )
                }
                pushPage<Route.ArtistDetail> { backStack ->
                    val route = backStack.toRoute<Route.ArtistDetail>()
                    ArtistDetailScreen(
                        artistId = route.artistId,
                        onBack = { navController.popBackStack() },
                        onAlbum = { navController.navigate(Route.AlbumDetail(it)) },
                    )
                }
                pushPage<Route.SourceDetail> { backStack ->
                    SourceDetailScreen(
                        sourceId = backStack.toRoute<Route.SourceDetail>().sourceId,
                        onBack = { navController.popBackStack() },
                    )
                }
                pushPage<Route.PlaylistDetail> { backStack ->
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
        hasRoute<Route.HiddenTracks>() ||
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
    hasRoute<Route.Settings>() || hasRoute<Route.HiddenTracks>() -> DrawerSection.Settings
    else -> null
}

private fun NavDestination?.isImmersive(): Boolean {
    if (this == null) return false
    return hasRoute<Route.Player>() || hasRoute<Route.Queue>()
}
