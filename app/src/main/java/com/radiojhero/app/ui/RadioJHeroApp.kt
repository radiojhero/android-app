package com.radiojhero.app.ui

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.radiojhero.app.PlayerConnection
import com.radiojhero.app.R
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.services.MediaPlaybackService
import com.radiojhero.app.ui.interact.InteractScreen
import com.radiojhero.app.ui.now.NowScreen
import com.radiojhero.app.ui.posts.PostsScreen
import com.radiojhero.app.ui.settings.SettingsScreen
import com.radiojhero.app.ui.webpage.WebPageScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

object Routes {
    const val NOW = "now"
    const val INTERACT = "interact"
    const val ARTICLES = "articles"
    const val SETTINGS = "settings"
    const val WEBPAGE = "webpage?url={url}"

    fun webpage(url: String) = "webpage?url=${Uri.encode(url)}"
}

private data class TopLevelDestination(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val selectedIcon: Int,
    @param:DrawableRes val unselectedIcon: Int,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        Routes.NOW,
        R.string.title_now,
        R.drawable.ic_baseline_music_note_24,
        R.drawable.ic_outline_music_note_24,
    ),
    TopLevelDestination(
        Routes.INTERACT,
        R.string.title_interact,
        R.drawable.ic_baseline_mode_comment_24,
        R.drawable.ic_outline_mode_comment_24,
    ),
    TopLevelDestination(
        Routes.ARTICLES,
        R.string.title_articles,
        R.drawable.ic_baseline_article_24,
        R.drawable.ic_outline_article_24,
    ),
    TopLevelDestination(
        Routes.SETTINGS,
        R.string.title_settings,
        R.drawable.ic_baseline_settings_24,
        R.drawable.ic_outline_settings_24,
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioJHeroApp(
    player: PlayerConnection,
    pendingUrl: String?,
    onPendingUrlHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The tab stays selected while a web page opened from it is on top.
    var selectedTab by rememberSaveable { mutableStateOf(Routes.NOW) }
    LaunchedEffect(currentRoute) {
        if (topLevelDestinations.any { it.route == currentRoute }) {
            selectedTab = currentRoute!!
        }
    }

    // Tapping the current tab again scrolls its screen back to the top.
    val reselections = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }

    LaunchedEffect(pendingUrl) {
        if (pendingUrl != null) {
            navController.navigate(Routes.webpage(pendingUrl))
            onPendingUrlHandled()
        }
    }

    val onTabClick = { route: String ->
        when {
            currentRoute == route -> reselections.tryEmit(route)
            selectedTab == route -> navController.popBackStack(route, inclusive = false)
            else -> navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
        Unit
    }

    val useNavigationRail = LocalConfiguration.current.screenWidthDp >= 600

    Surface(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                NavigationRail(header = { PlayButton(player) }) {
                    Spacer(Modifier.weight(1f))
                    topLevelDestinations.forEach { destination ->
                        val selected = selectedTab == destination.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onTabClick(destination.route) },
                            icon = { TabIcon(destination, selected) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            Column(Modifier.weight(1f)) {
                AppNavHost(
                    navController = navController,
                    player = player,
                    reselections = reselections,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (useNavigationRail) Modifier.windowInsetsPadding(
                                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                            ) else Modifier
                        )
                        .imePadding(),
                )

                if (!useNavigationRail && !WindowInsets.isImeVisible) {
                    NavigationBar {
                        topLevelDestinations.forEachIndexed { index, destination ->
                            if (index == topLevelDestinations.size / 2) {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    PlayButton(player)
                                }
                            }

                            val selected = selectedTab == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onTabClick(destination.route) },
                                icon = { TabIcon(destination, selected) },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    player: PlayerConnection,
    reselections: Flow<String>,
    modifier: Modifier = Modifier,
) {
    val openWebPage = { url: String -> navController.navigate(Routes.webpage(url)) }
    val navigateUp: () -> Unit = { navController.navigateUp() }
    val isWebPage = { route: String? -> route == Routes.WEBPAGE }

    NavHost(
        navController = navController,
        startDestination = Routes.NOW,
        modifier = modifier,
        enterTransition = {
            if (isWebPage(targetState.destination.route)) slideInHorizontally { it } else EnterTransition.None
        },
        exitTransition = {
            if (isWebPage(targetState.destination.route)) slideOutHorizontally { -it } else ExitTransition.None
        },
        popEnterTransition = {
            if (isWebPage(initialState.destination.route)) slideInHorizontally { -it } else EnterTransition.None
        },
        popExitTransition = {
            if (isWebPage(initialState.destination.route)) slideOutHorizontally { it } else ExitTransition.None
        },
    ) {
        composable(Routes.NOW) {
            NowScreen(
                extras = player.extras,
                scrollToTop = reselections.of(Routes.NOW),
                onOpenSchedule = { openWebPage(ConfigFetcher.getConfig("scheduleUrl") ?: "") },
            )
        }

        composable(Routes.INTERACT) {
            InteractScreen(
                programType = player.extras?.getInt(
                    MediaPlaybackService.PROGRAM_TYPE, MediaPlaybackService.PROGRAM_TYPE_PLAYLIST
                ) ?: MediaPlaybackService.PROGRAM_TYPE_PLAYLIST,
                scrollToTop = reselections.of(Routes.INTERACT),
            )
        }

        composable(Routes.ARTICLES) {
            PostsScreen(
                scrollToTop = reselections.of(Routes.ARTICLES),
                onOpenPost = { openWebPage("https://${ConfigFetcher.getConfig("host")}$it") },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                scrollToTop = reselections.of(Routes.SETTINGS),
                onOpenAbout = { openWebPage(ConfigFetcher.getConfig("aboutUrl") ?: "") },
                onFormatChange = player::switchFormat,
            )
        }

        composable(
            Routes.WEBPAGE,
            arguments = listOf(navArgument("url") {
                type = NavType.StringType
                defaultValue = ""
            }),
        ) { entry ->
            WebPageScreen(
                url = entry.arguments?.getString("url") ?: "",
                onNavigateUp = navigateUp,
                onOpenWebPage = openWebPage,
            )
        }
    }
}

@Composable
private fun Flow<String>.of(route: String): Flow<Unit> =
    remember(this, route) { filter { it == route }.map { } }

@Composable
private fun TabIcon(destination: TopLevelDestination, selected: Boolean) {
    Icon(
        painterResource(if (selected) destination.selectedIcon else destination.unselectedIcon),
        contentDescription = null,
    )
}

@Composable
private fun PlayButton(player: PlayerConnection) {
    FloatingActionButton(
        onClick = player::togglePlayback,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Icon(
            painterResource(if (player.isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24),
            contentDescription = stringResource(if (player.isPlaying) R.string.pause else R.string.play),
        )
    }
}
