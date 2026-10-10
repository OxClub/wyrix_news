package com.wyrix.news.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wyrix.news.R
import com.wyrix.news.domain.model.IndiaRegions
import com.wyrix.news.domain.model.WorldCountries
import com.wyrix.news.presentation.article.ArticleScreen
import com.wyrix.news.presentation.components.ArticleActions
import com.wyrix.news.presentation.components.ExternalActions
import com.wyrix.news.presentation.components.NoInsets
import com.wyrix.news.presentation.components.containerViewModel
import com.wyrix.news.presentation.explore.CountryScreen
import com.wyrix.news.presentation.explore.ExploreScreen
import com.wyrix.news.presentation.explore.FeedScreen
import com.wyrix.news.presentation.explore.IndiaScreen
import com.wyrix.news.presentation.explore.StateScreen
import com.wyrix.news.presentation.explore.WorldScreen
import com.wyrix.news.presentation.home.HomeScreen
import com.wyrix.news.presentation.saved.BookmarksViewModel
import com.wyrix.news.presentation.saved.SavedScreen
import com.wyrix.news.presentation.search.SearchScreen
import com.wyrix.news.presentation.settings.FavoritesScreen
import com.wyrix.news.presentation.settings.InfoScreen
import com.wyrix.news.presentation.settings.SettingsScreen

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevel(Routes.EXPLORE, R.string.nav_explore, Icons.Filled.Menu),
    TopLevel(Routes.INDIA, R.string.nav_india, Icons.Filled.LocationOn),
    TopLevel(Routes.WORLD, R.string.nav_world, Icons.Filled.Place),
    TopLevel(Routes.SAVED, R.string.nav_saved, Icons.Filled.Star),
)

@Composable
fun WyrixApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val bookmarksVm = containerViewModel<BookmarksViewModel> { BookmarksViewModel(it.bookmarks) }
    val bookmarkedIds by bookmarksVm.ids.collectAsStateWithLifecycle()

    val actions = remember(navController, bookmarksVm, context) {
        ArticleActions(
            onOpen = { navController.navigate(Routes.article(it.id)) },
            onToggleBookmark = { bookmarksVm.toggle(it) },
            onShare = { ExternalActions.share(context, it) },
        )
    }
    val navigate: (String) -> Unit = { route -> navController.navigate(route) }
    val back: () -> Unit = { navController.popBackStack() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = topLevelDestinations.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = NoInsets,
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevelDestinations.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(bookmarkedIds, actions, navigate) }
            composable(Routes.EXPLORE) { ExploreScreen(navigate) }
            composable(Routes.INDIA) { IndiaScreen(navigate) }
            composable(Routes.WORLD) { WorldScreen(navigate) }
            composable(Routes.SAVED) { SavedScreen(bookmarkedIds, actions) }
            composable(Routes.SEARCH) { SearchScreen(bookmarkedIds, actions, back) }
            composable(Routes.SETTINGS) { SettingsScreen(back, navigate) }
            composable(Routes.FAVORITES) { FavoritesScreen(back) }
            composable(
                Routes.INFO,
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
            ) { entry ->
                InfoScreen(entry.arguments?.getString("type").orEmpty(), back)
            }
            composable(
                Routes.ARTICLE,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                ArticleScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    bookmarkedIds = bookmarkedIds,
                    onToggleBookmark = actions.onToggleBookmark,
                    onBack = back,
                )
            }
            composable(
                Routes.FEED,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("arg") { type = NavType.StringType },
                ),
            ) { entry ->
                FeedScreen(
                    kind = entry.arguments?.getString("kind").orEmpty(),
                    arg = entry.arguments?.getString("arg").orEmpty(),
                    bookmarkedIds = bookmarkedIds,
                    actions = actions,
                    onBack = back,
                )
            }
            composable(
                Routes.STATE,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val region = IndiaRegions.byId(entry.arguments?.getString("id").orEmpty())
                if (region != null) StateScreen(region, bookmarkedIds, actions, back)
            }
            composable(
                Routes.COUNTRY,
                arguments = listOf(navArgument("iso") { type = NavType.StringType }),
            ) { entry ->
                val country = WorldCountries.byIso(entry.arguments?.getString("iso").orEmpty())
                if (country != null) CountryScreen(country, bookmarkedIds, actions, back)
            }
        }
    }
}
