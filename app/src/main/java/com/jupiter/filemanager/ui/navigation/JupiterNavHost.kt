package com.jupiter.filemanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.feature.ai.AiAssistantScreen
import com.jupiter.filemanager.feature.analytics.StorageAnalyticsScreen
import com.jupiter.filemanager.feature.archive.ArchiveManagerScreen
import com.jupiter.filemanager.feature.automation.AutomationScreen
import com.jupiter.filemanager.feature.automation.RuleBuilderScreen
import com.jupiter.filemanager.feature.billing.PaywallScreen
import com.jupiter.filemanager.feature.categories.CategoryBrowseScreen
import com.jupiter.filemanager.feature.browser.DualPaneScreen
import com.jupiter.filemanager.feature.browser.FileBrowserScreen
import com.jupiter.filemanager.feature.cleanup.CleanupScreen
import com.jupiter.filemanager.feature.cleanup.DuplicatesScreen
import com.jupiter.filemanager.feature.cleanup.SmartMergeScreen
import com.jupiter.filemanager.feature.cloud.CloudHubScreen
import com.jupiter.filemanager.feature.cloud.NasConnectionsScreen
import com.jupiter.filemanager.feature.details.FileDetailsScreen
import com.jupiter.filemanager.feature.downloads.DownloadsScreen
import com.jupiter.filemanager.feature.editor.TextEditorScreen
import com.jupiter.filemanager.feature.main.MainScreen
import com.jupiter.filemanager.feature.onboarding.OnboardingScreen
import com.jupiter.filemanager.feature.permission.PermissionScreen
import com.jupiter.filemanager.feature.preview.ImageGalleryScreen
import com.jupiter.filemanager.feature.preview.MusicPlayerScreen
import com.jupiter.filemanager.feature.preview.PdfViewerScreen
import com.jupiter.filemanager.feature.preview.PreviewScreen
import com.jupiter.filemanager.feature.preview.VideoPlayerScreen
import com.jupiter.filemanager.feature.privacy.DataTransparencyScreen
import com.jupiter.filemanager.feature.compress.CompressScreen
import com.jupiter.filemanager.feature.albums.AlbumsScreen
import com.jupiter.filemanager.feature.privacy.PrivacyDashboardScreen
import com.jupiter.filemanager.feature.remote.RemoteBrowserScreen
import com.jupiter.filemanager.feature.search.SearchScreen
import com.jupiter.filemanager.feature.settings.SettingsScreen
import com.jupiter.filemanager.feature.splash.SplashScreen
import com.jupiter.filemanager.feature.sync.SyncConflictsScreen
import com.jupiter.filemanager.feature.tags.TagsScreen
import com.jupiter.filemanager.feature.transfer.NearbyTransferScreen
import com.jupiter.filemanager.feature.transfer.TransferCenterScreen
import com.jupiter.filemanager.feature.transfer.TransferScreen
import com.jupiter.filemanager.feature.transfer.WifiTransferScreen
import com.jupiter.filemanager.feature.trash.TrashScreen
import com.jupiter.filemanager.feature.vault.VaultScreen
import com.jupiter.filemanager.feature.version.VersionHistoryScreen
import com.jupiter.filemanager.feature.whatsnew.WhatsNewScreen
import com.jupiter.filemanager.feature.workspace.WorkspaceDetailScreen
import com.jupiter.filemanager.feature.workspace.WorkspacesScreen

/**
 * Central navigation graph for the Jupiter file manager.
 *
 * Wires every [Destination] to its feature screen. Screens obtain their own
 * [androidx.lifecycle.ViewModel] instances internally via `hiltViewModel()`,
 * and read any route arguments (path / id) from their [androidx.lifecycle.SavedStateHandle].
 * This host therefore only declares the route arguments and supplies the
 * navigation lambdas.
 *
 * The start flow begins at [Destination.Splash], which decides — based on
 * onboarding completion and storage access state — whether to route to
 * Onboarding, Permission, or the [Destination.Main] shell.
 *
 * @param startDestination caller-provided start route (normally [Destination.Splash]).
 */
@Composable
fun JupiterNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    // Navigate to the appropriate screen for a file based on its type. Media and
    // archives open in their dedicated viewers; PDFs open in the PDF viewer; code and
    // small text-like documents open in the text editor; everything else falls back to
    // the generic preview screen.
    fun openByType(item: FileItem) {
        val route = when (item.type) {
            FileType.IMAGE -> Destination.ImageGallery.create(item.path)
            FileType.VIDEO -> Destination.VideoPlayer.create(item.path)
            FileType.AUDIO -> Destination.MusicPlayer.create(item.path)
            FileType.ARCHIVE -> Destination.ArchiveManagerRoute.create(item.path)
            FileType.PDF -> Destination.PdfViewer.create(item.path)
            FileType.CODE -> Destination.TextEditor.create(item.path)
            FileType.DOCUMENT, FileType.OTHER ->
                if (item.extension.lowercase() in TEXT_EDITABLE_EXTENSIONS) {
                    Destination.TextEditor.create(item.path)
                } else {
                    Destination.Preview.create(item.path)
                }
            else -> Destination.Preview.create(item.path)
        }
        navController.navigate(route)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // ---------------------------------------------------------------------
        // Startup & shell
        // ---------------------------------------------------------------------

        composable(route = Destination.Splash.route) {
            SplashScreen(
                onFinished = { route ->
                    navController.navigate(route) {
                        popUpTo(Destination.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = Destination.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Destination.Permission.route) {
                        popUpTo(Destination.Onboarding.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = Destination.Permission.route) {
            PermissionScreen(
                onGranted = {
                    navController.navigate(Destination.Main.route) {
                        popUpTo(Destination.Permission.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = Destination.Main.route) {
            MainScreen(
                onOpenRoute = { route -> navController.navigate(route) },
                onOpenFile = { item -> openByType(item) },
                onOpenPath = { path ->
                    navController.navigate(Destination.Browser.create(path))
                },
            )
        }

        // ---------------------------------------------------------------------
        // Existing browser / search / cleanup / vault / settings / transfer
        // ---------------------------------------------------------------------

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
                onOpenFile = { item -> openByType(item) },
                onNavigateRoute = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Search.route) {
            SearchScreen(
                onOpenFile = { item -> openByType(item) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Cleanup.route) {
            CleanupScreen(
                onOpenFile = { item -> openByType(item) },
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
                onOpenRoute = { route -> navController.navigate(route) },
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

        composable(
            route = Destination.PdfViewer.route,
            arguments = listOf(
                navArgument(Destination.PdfViewer.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            PdfViewerScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.TextEditor.route,
            arguments = listOf(
                navArgument(Destination.TextEditor.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            TextEditorScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------------------------------------------------------------
        // AI & analytics
        // ---------------------------------------------------------------------

        composable(route = Destination.AiAssistant.route) {
            AiAssistantScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.StorageAnalytics.route) {
            StorageAnalyticsScreen(
                onOpenRoute = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Duplicates.route) {
            DuplicatesScreen(
                onOpenFile = { item -> openByType(item) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.SmartMerge.route) {
            SmartMergeScreen(
                onOpenFile = { item -> openByType(item) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Downloads.route) {
            DownloadsScreen(
                onOpenFile = { item -> openByType(item) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Trash.route) {
            TrashScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.CategoryBrowse.route,
            arguments = listOf(
                navArgument(Destination.CategoryBrowse.ARG_TYPE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            CategoryBrowseScreen(
                onOpenFile = { item -> openByType(item) },
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------------------------------------------------------------
        // Organization
        // ---------------------------------------------------------------------

        composable(route = Destination.Tags.route) {
            TagsScreen(
                onOpenFile = { item -> openByType(item) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Workspaces.route) {
            WorkspacesScreen(
                onOpenWorkspace = { id ->
                    navController.navigate(Destination.WorkspaceDetail.create(id))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.WorkspaceDetail.route,
            arguments = listOf(
                navArgument(Destination.WorkspaceDetail.ARG_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            WorkspaceDetailScreen(
                onOpenFile = { item -> openByType(item) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.FileDetails.route,
            arguments = listOf(
                navArgument(Destination.FileDetails.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            FileDetailsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------------------------------------------------------------
        // Transfer & connectivity
        // ---------------------------------------------------------------------

        composable(route = Destination.TransferCenter.route) {
            TransferCenterScreen(
                onOpenRoute = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.NearbyTransfer.route) {
            NearbyTransferScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.WifiTransfer.route) {
            WifiTransferScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.CloudHub.route) {
            CloudHubScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.NasConnections.route) {
            NasConnectionsScreen(
                onOpenRemote = { id ->
                    navController.navigate(Destination.RemoteBrowser.create(id, ""))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.RemoteBrowser.route,
            arguments = listOf(
                navArgument(Destination.RemoteBrowser.ARG_CONNECTION) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Destination.RemoteBrowser.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            RemoteBrowserScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------------------------------------------------------------
        // Privacy & automation
        // ---------------------------------------------------------------------

        composable(route = Destination.PrivacyDashboard.route) {
            PrivacyDashboardScreen(
                onOpenRoute = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.DataTransparency.route) {
            DataTransparencyScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Compress.route) {
            CompressScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Albums.route) {
            AlbumsScreen(
                onOpenFile = { item -> openByType(item) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.Automation.route) {
            AutomationScreen(
                onCreateRule = { navController.navigate(Destination.RuleBuilder.route) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.RuleBuilder.route) {
            RuleBuilderScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------------------------------------------------------------
        // Archive & media
        // ---------------------------------------------------------------------

        composable(
            route = Destination.ArchiveManagerRoute.route,
            arguments = listOf(
                navArgument(Destination.ArchiveManagerRoute.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ArchiveManagerScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.MusicPlayer.route,
            arguments = listOf(
                navArgument(Destination.MusicPlayer.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            MusicPlayerScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.VideoPlayer.route,
            arguments = listOf(
                navArgument(Destination.VideoPlayer.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            VideoPlayerScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.ImageGallery.route,
            arguments = listOf(
                navArgument(Destination.ImageGallery.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ImageGalleryScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------------------------------------------------------------
        // Versioning & sync
        // ---------------------------------------------------------------------

        composable(
            route = Destination.VersionHistory.route,
            arguments = listOf(
                navArgument(Destination.VersionHistory.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            VersionHistoryScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.SyncConflicts.route) {
            SyncConflictsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.DualPane.route) {
            DualPaneScreen(
                onOpenFile = { item -> openByType(item) },
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------------------------------------------------------------
        // Growth: billing & what's new
        // ---------------------------------------------------------------------

        composable(route = Destination.Paywall.route) {
            PaywallScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Destination.WhatsNew.route) {
            WhatsNewScreen(
                onDismiss = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Extensions that should open in the in-app [TextEditorScreen] when their
 * [FileType] is generic (DOCUMENT/OTHER rather than CODE). Mirrors the text
 * classification used by the preview screen so small, plain-text files are editable.
 */
private val TEXT_EDITABLE_EXTENSIONS: Set<String> = setOf(
    "txt", "text", "log", "md", "markdown", "csv", "tsv",
    "json", "xml", "yaml", "yml", "ini", "cfg", "conf", "properties",
    "rtf", "tex", "srt", "vtt", "nfo",
)
