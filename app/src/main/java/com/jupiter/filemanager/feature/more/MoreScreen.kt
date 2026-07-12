package com.jupiter.filemanager.feature.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.ui.components.SectionHeader
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.ToolTile
import com.jupiter.filemanager.ui.navigation.Destination

/**
 * Represents a single advanced tool entry in the "More" tab.
 */
private data class MoreTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * Represents a labelled group of [MoreTool]s rendered under a [SectionHeader].
 */
private data class MoreSection(
    val title: String,
    val tools: List<MoreTool>,
)

/**
 * The "More" tab of the main shell. Presents a grouped list of every advanced
 * tool in Jupiter. Each tile invokes [onOpenRoute] with the destination route
 * for the corresponding feature. This screen is pure UI and requires no
 * ViewModel.
 */
@Composable
fun MoreScreen(
    onOpenRoute: (String) -> Unit,
) {
    val sections = rememberMoreSections()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "More",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "jupiter-tools-intro") {
                JupiterCard {
                    Text(
                        text = "Jupiter tools",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Storage, organization, transfer and privacy controls in one place.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            sections.forEach { section ->
                item(key = "header_${section.title}") {
                    SectionHeader(
                        title = section.title,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(
                    items = section.tools,
                    key = { tool -> tool.route + "_" + tool.title },
                ) { tool ->
                    ToolTile(
                        title = tool.title,
                        subtitle = tool.subtitle,
                        icon = tool.icon,
                        onClick = { onOpenRoute(tool.route) },
                    )
                }
            }
        }
    }
}

/**
 * Builds the static catalogue of advanced tools grouped into sections. Routes
 * map directly onto [Destination] entries handled by the outer NavHost.
 */
@Composable
private fun rememberMoreSections(): List<MoreSection> = listOf(
    MoreSection(
        title = "Storage & Cleanup",
        tools = listOf(
            MoreTool(
                title = "Storage Analytics",
                subtitle = "See what's using your space",
                icon = Icons.Filled.Analytics,
                route = Destination.StorageAnalytics.route,
            ),
            MoreTool(
                title = "Smart Cleanup",
                subtitle = "Free up space quickly",
                icon = Icons.Filled.AutoFixHigh,
                route = Destination.Cleanup.route,
            ),
            MoreTool(
                title = "Duplicate cleanup",
                subtitle = "Find duplicates and keep the best copy",
                icon = Icons.Filled.CopyAll,
                route = Destination.Duplicates.route,
            ),
            MoreTool(
                title = "Recycle Bin",
                subtitle = "Restore or permanently remove deleted files",
                icon = Icons.Filled.Delete,
                route = Destination.Trash.route,
            ),
            MoreTool(
                title = "Compress",
                subtitle = "Shrink photos & videos to your screen size",
                icon = Icons.Filled.Compress,
                route = Destination.Compress.route,
            ),
        ),
    ),
    MoreSection(
        title = "Organize",
        tools = listOf(
            MoreTool(
                title = "Tags & Collections",
                subtitle = "Group files with tags",
                icon = Icons.Filled.Label,
                route = Destination.Tags.route,
            ),
            MoreTool(
                title = "Albums",
                subtitle = "Browse photos grouped by album",
                icon = Icons.Filled.PhotoLibrary,
                route = Destination.Albums.route,
            ),
            MoreTool(
                title = "Project Workspaces",
                subtitle = "Bundle files into projects",
                icon = Icons.Filled.Work,
                route = Destination.Workspaces.route,
            ),
        ),
    ),
    MoreSection(
        title = "Transfer & Connect",
        tools = listOf(
            MoreTool(
                title = "Transfer Center",
                subtitle = "Manage file transfers",
                icon = Icons.Filled.SwapHoriz,
                route = Destination.TransferCenter.route,
            ),
            MoreTool(
                title = "Cloud Hub",
                subtitle = "Connect cloud accounts",
                icon = Icons.Filled.Cloud,
                route = Destination.CloudHub.route,
            ),
            MoreTool(
                title = "NAS / SMB",
                subtitle = "Connect network storage",
                icon = Icons.Filled.Dns,
                route = Destination.NasConnections.route,
            ),
        ),
    ),
    MoreSection(
        title = "Privacy & Security",
        tools = listOf(
            MoreTool(
                title = "Secure Vault",
                subtitle = "Encrypt private files",
                icon = Icons.Filled.Lock,
                route = Destination.Vault.route,
            ),
            MoreTool(
                title = "Privacy Dashboard",
                subtitle = "Review your privacy",
                icon = Icons.Filled.PrivacyTip,
                route = Destination.PrivacyDashboard.route,
            ),
        ),
    ),
    MoreSection(
        title = "Automation & Sync",
        tools = listOf(
            MoreTool(
                title = "Automation",
                subtitle = "Create rules and triggers",
                icon = Icons.Filled.AutoMode,
                route = Destination.Automation.route,
            ),
            MoreTool(
                title = "Archive Manager",
                subtitle = "Compress and extract files",
                icon = Icons.Filled.Archive,
                route = Destination.ArchiveManagerRoute.create(""),
            ),
            MoreTool(
                title = "Sync Conflicts",
                subtitle = "Resolve sync issues",
                icon = Icons.Filled.SyncProblem,
                route = Destination.SyncConflicts.route,
            ),
        ),
    ),
    MoreSection(
        title = "Tools",
        tools = listOf(
            MoreTool(
                title = "Dual Pane",
                subtitle = "Browse two folders at once",
                icon = Icons.Filled.ViewColumn,
                route = Destination.DualPane.route,
            ),
            MoreTool(
                title = "AI Assistant",
                subtitle = "Ask about your files",
                icon = Icons.Filled.AutoFixHigh,
                route = Destination.AiAssistant.route,
            ),
            MoreTool(
                title = "Settings",
                subtitle = "App preferences",
                icon = Icons.Filled.Settings,
                route = Destination.Settings.route,
            ),
        ),
    ),
)
