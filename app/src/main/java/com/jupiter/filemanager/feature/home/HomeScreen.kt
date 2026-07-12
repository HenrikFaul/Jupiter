package com.jupiter.filemanager.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.Bookmark
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import com.jupiter.filemanager.ui.components.SectionHeader
import com.jupiter.filemanager.ui.components.StorageBar
import com.jupiter.filemanager.ui.components.ToolTile
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterFileBadge
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterMainTab
import com.jupiter.filemanager.ui.components.JupiterProgressBar
import com.jupiter.filemanager.ui.components.JupiterStorageRing
import com.jupiter.filemanager.ui.components.JupiterWordmark
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.theme.JupiterDesign
import kotlin.math.roundToInt

/**
 * Home / Dashboard screen (NEXUS design language).
 *
 * Surfaces, top to bottom:
 *  - a branded "Home" header with a search entry-point ("Search files, AI, tags")
 *    and a Pro chip,
 *  - a Quick Access row (Downloads / Documents / Images) opening real folders,
 *  - a Storage Overview card with Internal + Cloud [StorageBar]s,
 *  - a Tools row (Clean Up / Duplicates / Secure Vault / Transfer),
 *  - recently visited locations and user bookmarks.
 *
 * @param onOpenFile invoked for a known recent file or folder, so both kinds
 *   open through the correct route.
 * @param onOpenPath invoked with a filesystem path the user wants to browse.
 * @param onNavigate invoked with a [Destination] route string for top-level
 *   feature destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFile: (FileItem) -> Unit,
    onOpenPath: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Branded header.
            item(key = "header") {
                HomeHeader(
                    onOpenAssistant = { onNavigate(Destination.AiAssistant.route) },
                    onOpenSettings = { onNavigate(Destination.Settings.route) },
                )
            }

            // Search entry point.
            item(key = "search") {
                SearchEntry(onClick = { onNavigate(Destination.Search.route) })
            }

            // Storage overview is the first data card so the user's free space is immediately
            // visible, matching the supplied dashboard hierarchy.
            item(key = "storage_card") {
                StorageOverviewCard(
                    primary = uiState.primaryVolume,
                    onClick = { onNavigate(Destination.StorageAnalytics.route) },
                )
            }

            // Quick access follows the storage card as a compact strip of real folders.
            item(key = "quick_access_row") {
                QuickAccessRow(
                    shortcuts = uiState.quickAccess,
                    onOpenPath = onOpenPath,
                    onNavigate = onNavigate,
                )
            }

            // Category tiles: each tile uses the exact MediaStore source that its destination
            // screen uses, so the live total and the opened list always agree.
            if (uiState.categories.isNotEmpty()) {
                item(key = "categories_header") {
                    SectionHeader(
                        title = "Categories",
                        actionLabel = "View all",
                        onAction = { onNavigate(Destination.StorageAnalytics.route) },
                    )
                }
                item(key = "categories_grid") {
                    CategoryGrid(
                        categories = uiState.categories,
                        onOpenCategory = { category ->
                            onNavigate(Destination.CategoryBrowse.create(category))
                        },
                    )
                }
            }

            // Tools.
            item(key = "tools_header") {
                SectionHeader(
                    title = "Tools",
                    actionLabel = "Clean up",
                    onAction = { onNavigate(Destination.Cleanup.route) },
                )
            }
            item(key = "tools_grid") {
                ToolsSection(onNavigate = onNavigate)
            }

            // Recents.
            if (uiState.recents.isNotEmpty()) {
                item(key = "recents_header") {
                    SectionHeader(
                        title = "Recent files",
                        actionLabel = "View all",
                        onAction = {
                            onNavigate(Destination.MainTab.create(JupiterMainTab.RECENT.route))
                        },
                    )
                }
                item(key = "recent_files_card") {
                    RecentFilesCard(
                        items = uiState.recents.take(4),
                        onOpenFile = onOpenFile,
                        onOpenDetails = { item ->
                            onNavigate(Destination.FileDetails.create(item.path))
                        },
                    )
                }
            }

            // Bookmarks.
            if (uiState.bookmarks.isNotEmpty()) {
                item(key = "bookmarks_header") {
                    SectionHeader(title = "Favorites")
                }
                items(uiState.bookmarks, key = { "bookmark_" + it.path }) { bookmark ->
                    BookmarkRow(bookmark = bookmark, onClick = { onOpenPath(bookmark.path) })
                }
            }

            if (uiState.error != null) {
                item(key = "error") {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Reference header: visual wordmark plus two real, functioning destinations. */
@Composable
private fun HomeHeader(
    onOpenAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterWordmark(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenAssistant) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "AI assistant",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More and settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** A small "Pro" chip with a crown. */
@Composable
private fun ProChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Pro",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Tappable search field that routes to the Search destination. */
@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Search files, folders, APKs",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Search and filters",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** One three-cell quick-access strip, matching the supplied dashboard hierarchy. */
@Composable
private fun QuickAccessRow(
    shortcuts: List<QuickAccessShortcut>,
    onOpenPath: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    if (shortcuts.isEmpty()) {
        Text(
            text = "No standard folders available yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    val visible = shortcuts.take(3)
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            visible.forEachIndexed { index, shortcut ->
                QuickAccessTile(
                    shortcut = shortcut,
                    onClick = {
                        // Category-backed shortcuts open the instant, device-wide
                        // MediaStore listing; anything else falls back to browsing
                        // the concrete folder path.
                        val category = categoryForShortcut(shortcut.id)
                        if (category != null) {
                            onNavigate(Destination.CategoryBrowse.create(category))
                        } else {
                            onOpenPath(shortcut.path)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (index != visible.lastIndex) {
                    VerticalDivider(
                        modifier = Modifier.height(48.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/**
 * Maps a Quick Access shortcut [id] to the [StorageCategory] whose instant
 * MediaStore listing it should open, or null when the shortcut has no category
 * equivalent and should simply browse its folder path.
 */
private fun categoryForShortcut(id: String): StorageCategory? = when (id) {
    "images" -> StorageCategory.IMAGES
    "videos" -> StorageCategory.VIDEOS
    "audio" -> StorageCategory.AUDIO
    "documents" -> StorageCategory.DOCUMENTS
    "downloads" -> StorageCategory.DOWNLOADS
    else -> null
}

/** A compact horizontal cell inside the shared Quick Access strip. */
@Composable
private fun QuickAccessTile(
    shortcut: QuickAccessShortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(JupiterDesign.CompactCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = iconForShortcut(shortcut.id),
            tint = colorForShortcut(shortcut.id),
            contentDescription = null,
            size = 32.dp,
        )
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortcut.label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = quickAccessSubtitle(shortcut),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun colorForShortcut(id: String): Color = when (id) {
    "downloads" -> JupiterDesign.TealEnd
    "documents" -> JupiterDesign.CategoryDocument
    "images" -> Color(0xFFFF8068)
    else -> JupiterDesign.CategoryOther
}

private fun quickAccessSubtitle(shortcut: QuickAccessShortcut): String {
    val count = shortcut.itemCount
    val size = shortcut.sizeBytes
    return when {
        count != null && size != null -> formatItemCount(count) + " • " + formatBytes(size)
        count != null -> formatItemCount(count)
        size != null -> formatBytes(size)
        else -> "Open folder"
    }
}

/** Storage Overview card with Internal + Cloud usage bars. */
@Composable
private fun StorageOverviewCard(
    primary: StorageVolumeInfo?,
    onClick: () -> Unit,
) {
    JupiterCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp),
        shape = JupiterDesign.HeroCardShape,
    ) {
        if (primary == null) {
            Text(
                text = "Storage information is unavailable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@JupiterCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            JupiterStorageRing(
                fraction = primary.usedFraction,
                size = 108.dp,
                strokeWidth = 12.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(primary.usedFraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Storage overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open storage analysis",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "${formatBytes(primary.availableBytes)} free",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "of ${formatBytes(primary.totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                JupiterProgressBar(fraction = primary.usedFraction)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StorageLegend(
                        color = JupiterDesign.TealStart,
                        label = "${formatBytes(primary.usedBytes)} used",
                    )
                    StorageLegend(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "${formatBytes(primary.availableBytes)} free",
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** A compact dashboard grid that keeps every existing high-value tool reachable. */
@Composable
private fun ToolsSection(onNavigate: (String) -> Unit) {
    val tools = listOf(
        HomeTool("Duplicate cleanup", "Review safely", Icons.Filled.ContentCopy, Destination.Duplicates.route, MaterialTheme.colorScheme.primary),
        HomeTool("App storage", "Apps & cache", Icons.Filled.Apps, Destination.AppStorage.route, JupiterDesign.CategoryApk),
        HomeTool("Secure vault", "Keep files safe", Icons.Filled.Lock, Destination.Vault.route, JupiterDesign.CategoryVideo),
        HomeTool("Cloud hub", "Manage clouds", Icons.Filled.Cloud, Destination.CloudHub.route, JupiterDesign.CategoryDownload),
        HomeTool("Automation", "Smart actions", Icons.Filled.SmartToy, Destination.Automation.route, JupiterDesign.CategoryApk),
        HomeTool("Transfers", "Send anywhere", Icons.Filled.SwapHoriz, Destination.TransferCenter.route, JupiterDesign.Warning),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tool ->
                    HomeToolTile(
                        tool = tool,
                        onClick = { onNavigate(tool.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private data class HomeTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val accent: Color,
)

@Composable
private fun HomeToolTile(
    tool: HomeTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JupiterCard(
        modifier = modifier
            .clip(JupiterDesign.CompactCardShape)
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        shape = JupiterDesign.CompactCardShape,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = tool.icon,
                tint = tool.accent,
                contentDescription = null,
                size = 28.dp,
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 8.sp, lineHeight = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, lineHeight = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** One divided reference card containing the most recent real entries. */
@Composable
private fun RecentFilesCard(
    items: List<FileItem>,
    onOpenFile: (FileItem) -> Unit,
    onOpenDetails: (FileItem) -> Unit,
) {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        items.forEachIndexed { index, item ->
            RecentRow(
                item = item,
                onClick = { onOpenFile(item) },
                onOpenDetails = { onOpenDetails(item) },
            )
            if (index != items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun RecentRow(
    item: FileItem,
    onClick: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    var menuExpanded by remember(item.path) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterFileBadge(item = item, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${recentTypeLabel(item)}  •  ${formatRelativeTime(item.lastModified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.isDirectory) {
            Text(
                text = formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More actions for ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Open") },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Details") },
                    onClick = {
                        menuExpanded = false
                        onOpenDetails()
                    },
                )
            }
        }
    }
}

private fun recentTypeLabel(item: FileItem): String = when {
    item.isDirectory -> "Folder"
    item.extension.isNotBlank() -> item.extension.uppercase()
    else -> item.type.name.lowercase().replaceFirstChar { it.uppercase() }
}

/** A saved bookmark entry. */
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
) {
    JupiterCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = bookmark.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun iconForShortcut(id: String): ImageVector = when (id) {
    "downloads" -> Icons.Filled.Download
    "documents" -> Icons.Filled.Description
    "images" -> Icons.Filled.Image
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun iconForCategory(category: StorageCategory): ImageVector = when (category) {
    StorageCategory.IMAGES -> Icons.Filled.Image
    StorageCategory.VIDEOS -> Icons.Filled.Videocam
    StorageCategory.AUDIO -> Icons.Filled.Audiotrack
    StorageCategory.DOCUMENTS -> Icons.Filled.Description
    StorageCategory.ARCHIVES -> Icons.Filled.FolderZip
    StorageCategory.APPS -> Icons.Filled.PhoneAndroid
    StorageCategory.DOWNLOADS -> Icons.Filled.Download
    StorageCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** Per-category accent colour for the tile icon badge (mirrors the storage-analytics breakdown). */
private fun colorForCategory(category: StorageCategory): Color = when (category) {
    StorageCategory.IMAGES -> JupiterDesign.CategoryPhoto
    StorageCategory.VIDEOS -> JupiterDesign.CategoryVideo
    StorageCategory.AUDIO -> JupiterDesign.CategoryAudio
    StorageCategory.DOCUMENTS -> JupiterDesign.CategoryDocument
    StorageCategory.ARCHIVES -> JupiterDesign.CategoryArchive
    StorageCategory.APPS -> JupiterDesign.CategoryApk
    StorageCategory.DOWNLOADS -> JupiterDesign.CategoryDownload
    StorageCategory.OTHER -> JupiterDesign.CategoryOther
}

private fun labelForCategory(category: StorageCategory): String = when (category) {
    StorageCategory.IMAGES -> "Photos"
    StorageCategory.VIDEOS -> "Videos"
    StorageCategory.AUDIO -> "Audio"
    StorageCategory.DOCUMENTS -> "Documents"
    StorageCategory.ARCHIVES -> "Archives"
    StorageCategory.APPS -> "APKs"
    StorageCategory.DOWNLOADS -> "Downloads"
    StorageCategory.OTHER -> "Other"
}

/** The order categories appear in the Home grid (Other is omitted — it is the analytics catch-all). */
private val CATEGORY_TILE_ORDER = listOf(
    StorageCategory.IMAGES,
    StorageCategory.VIDEOS,
    StorageCategory.AUDIO,
    StorageCategory.DOCUMENTS,
    StorageCategory.APPS,
    StorageCategory.ARCHIVES,
    StorageCategory.DOWNLOADS,
)

/**
 * A colourful 4-column grid of category tiles (Photos / Videos / Audio / Documents / APKs /
 * Archives / Downloads), each showing the category's total size. Laid out as fixed rows (it lives
 * inside the Home [LazyColumn], so it must not scroll itself). Empty trailing cells are padded so
 * every tile keeps an equal width.
 */
@Composable
private fun CategoryGrid(
    categories: List<CategoryUsage>,
    onOpenCategory: (StorageCategory) -> Unit,
) {
    val byCategory = categories.associateBy { it.category }
    val tiles = CATEGORY_TILE_ORDER.mapNotNull { byCategory[it] }
    if (tiles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.take(4).takeIf { it.isNotEmpty() }?.let { firstRow ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                firstRow.forEach { usage ->
                    CategoryTile(
                        usage = usage,
                        onClick = { onOpenCategory(usage.category) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        tiles.drop(4).takeIf { it.isNotEmpty() }?.let { secondRow ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondRow.forEach { usage ->
                    CategoryTile(
                        usage = usage,
                        onClick = { onOpenCategory(usage.category) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One category tile: a colour-tinted icon badge above the label and total size. */
@Composable
private fun CategoryTile(
    usage: CategoryUsage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = colorForCategory(usage.category)
    JupiterCard(
        modifier = modifier
            .clip(JupiterDesign.CompactCardShape)
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp),
        shape = JupiterDesign.CompactCardShape,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = iconForCategory(usage.category),
                tint = accent,
                contentDescription = null,
                size = 32.dp,
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = labelForCategory(usage.category),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp, lineHeight = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBytes(usage.sizeBytes),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
