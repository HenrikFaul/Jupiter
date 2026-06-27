package com.jupiter.filemanager.feature.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.feature.browser.FileBrowserScreen
import com.jupiter.filemanager.feature.favorites.FavoritesScreen
import com.jupiter.filemanager.feature.home.HomeScreen
import com.jupiter.filemanager.feature.more.MoreScreen
import com.jupiter.filemanager.feature.recent.RecentScreen

/**
 * Root container for the primary app experience. Hosts an inner [NavHost] driven by a bottom
 * [NavigationBar] with five tabs (Home / Files / Recent / Favorites / More). Advanced tools and
 * detail destinations are surfaced to the outer navigation graph via [onOpenRoute] / [onOpenFile]
 * / [onOpenPath].
 */
private sealed class MainTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Home : MainTab("tab_home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Files : MainTab("tab_files", "Files", Icons.Filled.Folder, Icons.Outlined.Folder)
    data object Recent : MainTab("tab_recent", "Recent", Icons.Filled.History, Icons.Outlined.History)
    data object Favorites :
        MainTab("tab_favorites", "Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder)

    data object More : MainTab("tab_more", "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
}

private val mainTabs = listOf(
    MainTab.Home,
    MainTab.Files,
    MainTab.Recent,
    MainTab.Favorites,
    MainTab.More,
)

@Composable
fun MainScreen(
    onOpenRoute: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onOpenPath: (String) -> Unit,
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            NavigationBar {
                mainTabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(MainTab.Home.route) {
                HomeScreen(
                    onOpenPath = onOpenPath,
                    onNavigate = onOpenRoute,
                )
            }
            composable(MainTab.Files.route) {
                FileBrowserScreen(
                    initialPath = null,
                    onOpenFile = onOpenFile,
                    onNavigateRoute = onOpenRoute,
                    onBack = {},
                )
            }
            composable(MainTab.Recent.route) {
                RecentScreen(
                    onOpenFile = onOpenFile,
                    onOpenRoute = onOpenRoute,
                )
            }
            composable(MainTab.Favorites.route) {
                FavoritesScreen(
                    onOpenFile = onOpenFile,
                    onOpenPath = onOpenPath,
                )
            }
            composable(MainTab.More.route) {
                MoreScreen(
                    onOpenRoute = onOpenRoute,
                )
            }
        }
    }
}
