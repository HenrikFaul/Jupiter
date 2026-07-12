package com.jupiter.filemanager.feature.main

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.jupiter.filemanager.ui.components.JupiterFloatingBottomNavigation
import com.jupiter.filemanager.ui.components.JupiterMainTab

/**
 * Root container for the primary app experience. Hosts an inner [NavHost] driven by a bottom
 * [NavigationBar] with five tabs (Home / Files / Recent / Favorites / More). Advanced tools and
 * detail destinations are surfaced to the outer navigation graph via [onOpenRoute] / [onOpenFile]
 * / [onOpenPath].
 */
@Composable
fun MainScreen(
    onOpenRoute: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onOpenPath: (String) -> Unit,
    initialTab: JupiterMainTab = JupiterMainTab.HOME,
) {
    val navController = rememberNavController()

    Scaffold(
        // Each tab screen owns its own safe-drawing inset. Applying system bars here as well
        // shifted the whole reference layout down by a second status-bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val selectedTab = JupiterMainTab.entries.firstOrNull { tab ->
                currentDestination?.hierarchy?.any { it.route == tab.route } == true
            } ?: initialTab
            JupiterFloatingBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialTab.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(JupiterMainTab.HOME.route) {
                HomeScreen(
                    onOpenFile = onOpenFile,
                    onOpenPath = onOpenPath,
                    onNavigate = onOpenRoute,
                )
            }
            composable(JupiterMainTab.FILES.route) {
                FileBrowserScreen(
                    initialPath = null,
                    onOpenFile = onOpenFile,
                    onNavigateRoute = onOpenRoute,
                    onBack = {},
                )
            }
            composable(JupiterMainTab.RECENT.route) {
                RecentScreen(
                    onOpenFile = onOpenFile,
                    onOpenRoute = onOpenRoute,
                )
            }
            composable(JupiterMainTab.FAVORITES.route) {
                FavoritesScreen(
                    onOpenFile = onOpenFile,
                    onOpenPath = onOpenPath,
                )
            }
            composable(JupiterMainTab.MORE.route) {
                MoreScreen(
                    onOpenRoute = onOpenRoute,
                )
            }
        }
    }
}
