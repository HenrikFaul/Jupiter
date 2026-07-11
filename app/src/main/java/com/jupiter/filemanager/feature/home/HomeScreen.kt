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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.domain.model.Bookmark
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import com.jupiter.filemanager.ui.components.SectionHeader
import com.jupiter.filemanager.ui.components.StorageBar
import com.jupiter.filemanager.ui.components.ToolTile
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.navigation.Destination
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
 * @param onOpenPath invoked with a filesystem path the user wants to browse.
 * @param onNavigate invoked with a [Destination] route string for top-level
 *   feature destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPath: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Branded header.
            item(key = "header") {
                HomeHeader()
            }

            // Search entry point.
            item(key = "search") {
                SearchEntry(onClick = { onNavigate(Destination.Search.route) })
            }

            // Quick access folders.
            item(key = "quick_access_header") {
                SectionHeader(
                    title = "Quick Access",
                    actionLabel = "See All",
                    onAction = { onNavigate(Destination.Search.route) },
                )
            }
            item(key = "quick_access_row") {
                QuickAccessRow(
                    shortcuts = uiState.quickAccess,
                    onOpenPath = onOpenPath,
                    onNavigate = onNavigate,
                )
            }

            // Category tiles: a colourful icon grid, each showing the category's total size; tapping
            // one opens that category's file list. Only shown once the storage overview has loaded.
            if (uiState.categories.isNotEmpty()) {
                item(key = "categories_header") {
                    SectionHeader(
                        title = "Categories",
                        actionLabel = "Storage",
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

            // Storage overview.
            item(key = "storage_header") {
                SectionHeader(
                    title = "Storage Overview",
                    actionLabel = "Details",
                    onAction = { onNavigate(Destination.StorageAnalytics.route) },
                )
            }
            item(key = "storage_card") {
                StorageOverviewCard(
                    primary = uiState.primaryVolume,
                    onClick = { onNavigate(Destination.StorageAnalytics.route) },
                )
            }

            // Tools.
            item(key = "tools_header") {
                SectionHeader(
                    title = "Tools",
                )
            }
            item(key = "tools_grid") {
                ToolsSection(onNavigate = onNavigate)
            }

            // Recents.
            if (uiState.recents.isNotEmpty()) {
                item(key = "recents_header") {
                    SectionHeader(title = "Recent")
                }
                items(uiState.recents, key = { "recent_" + it.path }) { item ->
                    RecentRow(item = item, onClick = { onOpenPath(item.path) })
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

/** Branded greeting header with a Pro chip. */
@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { /* TODO: open navigation drawer / menu */ }) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Your files at a glance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProChip()
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Search files, AI, tags",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Horizontally-scrolling Quick Access folder tiles. */
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
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(shortcuts, key = { it.id }) { shortcut ->
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
            )
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

/** A single Quick Access folder tile with icon, name and aggregated usage. */
@Composable
private fun QuickAccessTile(
    shortcut: QuickAccessShortcut,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForShortcut(shortcut.id),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = shortcut.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = quickAccessSubtitle(shortcut),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (primary != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StorageBar(
                        label = "Internal storage",
                        usedBytes = primary.usedBytes,
                        totalBytes = primary.totalBytes,
                    )
                    Text(
                        text = "${(primary.usedFraction * 100).roundToInt()}% used",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            } else {
                Text(
                    text = "Storage information is unavailable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Cloud storage is not connected yet — surface an honest empty bar
            // with a clear affordance rather than fabricating usage figures.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StorageBar(
                    label = "Cloud",
                    usedBytes = 0L,
                    totalBytes = 0L,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "No cloud account connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Tools section: Clean Up, Duplicates, Secure Vault, Transfer. */
@Composable
private fun ToolsSection(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolTile(
            title = "Clean Up",
            subtitle = "Free up space",
            icon = Icons.Filled.CleaningServices,
            onClick = { onNavigate(Destination.Cleanup.route) },
        )
        ToolTile(
            title = "Duplicates",
            subtitle = "Find duplicate files",
            icon = Icons.Filled.ContentCopy,
            onClick = { onNavigate(Destination.Duplicates.route) },
        )
        ToolTile(
            title = "Secure Vault",
            subtitle = "Protect private files",
            icon = Icons.Filled.Lock,
            onClick = { onNavigate(Destination.Vault.route) },
        )
        ToolTile(
            title = "Transfer",
            subtitle = "Send & receive files",
            icon = Icons.Filled.SwapHoriz,
            onClick = { onNavigate(Destination.TransferCenter.route) },
        )
    }
}

/** A recently visited file/folder entry. */
@Composable
private fun RecentRow(
    item: FileItem,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = item.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(
                imageVector = iconForFile(item),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    )
}

/** A saved bookmark entry. */
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = {
            Text(
                text = bookmark.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = bookmark.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    )
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
    StorageCategory.IMAGES -> Color(0xFF42A5F5)    // blue
    StorageCategory.VIDEOS -> Color(0xFFEF5350)    // red
    StorageCategory.AUDIO -> Color(0xFFAB47BC)     // purple
    StorageCategory.DOCUMENTS -> Color(0xFF26A69A) // teal
    StorageCategory.ARCHIVES -> Color(0xFFFFA726)  // orange
    StorageCategory.APPS -> Color(0xFF66BB6A)      // green
    StorageCategory.DOWNLOADS -> Color(0xFF5C6BC0) // indigo
    StorageCategory.OTHER -> Color(0xFF8D6E63)     // brown
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
    val columns = 4
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(columns).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowTiles.forEach { usage ->
                    CategoryTile(
                        usage = usage,
                        onClick = { onOpenCategory(usage.category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad the last row so tiles keep an equal width instead of stretching.
                repeat(columns - rowTiles.size) {
                    Spacer(modifier = Modifier.weight(1f))
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconForCategory(usage.category),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = labelForCategory(usage.category),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatBytes(usage.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
