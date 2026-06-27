package com.jupiter.filemanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jupiter.filemanager.feature.browser.FileBrowserScreen
import com.jupiter.filemanager.feature.cleanup.CleanupScreen
import com.jupiter.filemanager.feature.home.HomeScreen
import com.jupiter.filemanager.feature.permission.PermissionScreen
import com.jupiter.filemanager.feature.preview.PreviewScreen
import com.jupiter.filemanager.feature.search.SearchScreen
import com.jupiter.filemanager.feature.settings.SettingsScreen
import com.jupiter.filemanager.feature.transfer.TransferScreen
import com.jupiter.filemanager.feature.vault.VaultScreen

/**
 * Central navigation graph for the Jupiter file manager.
 *
 * Wires every [Destination] to its feature screen. Screens obtain their own
 * [androidx.lifecycle.ViewModel] instances internally via
 * `hiltViewModel()`, so this host only supplies navigation lambdas and the
 * route arguments (the optional `path` argument for Browser and Preview).
 */
@Composable
fun JupiterNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(route = Destination.Permission.route) {
            PermissionScreen(
                onGranted = {
                    navController.navigate(Destination.Home.route) {
                        popUpTo(Destination.Permission.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = Destination.Home.route) {
            HomeScreen(
                onOpenPath = { path ->
                    navController.navigate(Destination.Browser.create(path))
                },
                onNavigate = { route ->
                    navController.navigate(route)
                },
            )
        }

        composable(
            route = Destination.Browser.route,
            arguments = listOf(
                navArgument(Destination.Browser.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val initialPath = backStackEntry.arguments?.getString(Destination.Browser.ARG_PATH)
            FileBrowserScreen(
                initialPath = initialPath,
                onOpenFile = { item ->
                    navController.navigate(Destination.Preview.create(item.path))
                },
                onNavigateRoute = { route ->
                    navController.navigate(route)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Search.route) {
            SearchScreen(
                onOpenFile = { item ->
                    navController.navigate(Destination.Preview.create(item.path))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Cleanup.route) {
            CleanupScreen(
                onOpenFile = { item ->
                    navController.navigate(Destination.Preview.create(item.path))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Vault.route) {
            VaultScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Transfer.route) {
            TransferScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.Preview.route,
            arguments = listOf(
                navArgument(Destination.Preview.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString(Destination.Preview.ARG_PATH).orEmpty()
            PreviewScreen(
                path = path,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
