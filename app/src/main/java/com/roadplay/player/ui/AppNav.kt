package com.roadplay.player.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import com.roadplay.player.AppContainer
import com.roadplay.player.PlaybackRequest
import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.ui.screens.HomeScreen
import com.roadplay.player.ui.screens.LibraryScreen
import com.roadplay.player.ui.screens.PlayerScreen
import com.roadplay.player.ui.screens.PlaylistScreen
import com.roadplay.player.ui.screens.SafetyScreen
import com.roadplay.player.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val SAFETY = "safety"
    const val PLAYLIST = "playlist/{id}"
    fun playlist(id: Long) = "playlist/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.LIBRARY, "Library", Icons.Rounded.VideoLibrary),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun RoadPlayNav(container: AppContainer, libraryVm: LibraryViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()

    LaunchedEffect(libraryState.message) {
        libraryState.message?.let { snackbar.showSnackbar(it); libraryVm.dismissMessage() }
    }

    fun play(list: List<VideoEntity>, video: VideoEntity) {
        container.pendingRequest = PlaybackRequest(list.map { it.id }, list.indexOfFirst { it.id == video.id }.coerceAtLeast(0))
        nav.navigate(Routes.PLAYER) { launchSingleTop = true }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route in TABS.map { it.route }) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(libraryVm, inner, ::play) { nav.navigate(Routes.playlist(it)) }
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(libraryVm, inner, ::play) { nav.navigate(Routes.playlist(it)) }
            }
            composable(
                Routes.PLAYLIST,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { backStack ->
                val id = backStack.arguments?.getLong("id") ?: return@composable
                PlaylistScreen(id, libraryVm, inner, onBack = { nav.popBackStack() }, onPlay = ::play)
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
                    initializer { SettingsViewModel(appContext, container.settings, container.library) }
                })
                SettingsScreen(
                    vm, inner,
                    onOpenSafety = { nav.navigate(Routes.SAFETY) },
                    onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                )
            }
            composable(Routes.SAFETY) {
                SafetyScreen(container.carMonitor, inner) { nav.popBackStack() }
            }
            composable(Routes.PLAYER) {
                val vm: PlayerViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        PlayerViewModel(
                            appContext, container.playbackConnection, container.database.videoDao(),
                            container.library, container.settings, container.carMonitor, container.pendingRequest,
                        )
                    }
                })
                PlayerScreen(vm, libraryVm.settings.value.subtitleScale) { nav.popBackStack() }
            }
        }
    }
}
