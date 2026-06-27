package com.jupiter.filemanager.feature.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.navigation.Destination

/**
 * Home screen: the entry point after storage access is granted.
 *
 * Surfaces, top to bottom:
 *  - a top "Internal storage" tile that opens the primary volume root,
 *  - per-volume storage usage cards with a [LinearProgressIndicator],
 *  - a horizontal row of category shortcuts,
 *  - quick-access tiles (Search / Cleanup / Vault / Settings),
 *  - recently visited locations,
 *  - user bookmarks.
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

    val primaryRoot: String? = uiState.volumes
        .firstOrNull { it.isPrimary }
        ?.rootPath
        ?: uiState.volumes.firstOrNull()?.rootPath

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "Jupiter") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Top internal-storage tile.
            if (primaryRoot != null) {
                item(key = "internal_tile") {
                    InternalStorageTile(
                        rootPath = primaryRoot,
                        onClick = { onOpenPath(primaryRoot) },
                    )
                }
            }

            // Per-volume usage cards.
            if (uiState.volumes.isNotEmpty()) {
                item(key = "volumes_header") {
                    SectionHeader(title = "Storage")
                }
                items(uiState.volumes, key = { it.id }) { volume ->
                    StorageVolumeCard(
                        volume = volume,
                        onClick = { onOpenPath(volume.rootPath) },
                    )
                }
            }

            // Category shortcuts.
            if (uiState.categories.isNotEmpty()) {
                item(key = "categories_header") {
                    SectionHeader(title = "Categories")
                }
                item(key = "categories_row") {
                    CategoryShortcutsRow(categories = uiState.categories)
                }
            }

            // Quick-access feature tiles.
            item(key = "quick_access_header") {
                SectionHeader(title = "Quick access")
            }
            item(key = "quick_access_row") {
                QuickAccessRow(onNavigate = onNavigate)
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
                    SectionHeader(title = "Bookmarks")
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

/** A small uppercase-ish section label. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Prominent tile that opens the primary volume root. */
@Composable
private fun InternalStorageTile(
    rootPath: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Internal storage",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = rootPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Usage card for one [StorageVolumeInfo] with a linear usage bar. */
@Composable
private fun StorageVolumeCard(
    volume: StorageVolumeInfo,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = volume.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (volume.isRemovable) {
                    Text(
                        text = "Removable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { volume.usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Text(
                text = formatBytes(volume.usedBytes) + " used of " + formatBytes(volume.totalBytes) +
                    "  •  " + formatBytes(volume.availableBytes) + " free",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Horizontally-scrolling category shortcuts. */
@Composable
private fun CategoryShortcutsRow(categories: List<CategoryUsage>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(categories, key = { it.category.name }) { usage ->
            CategoryShortcut(usage = usage)
        }
    }
}

/** A single category chip showing icon, name and aggregated size/count. */
@Composable
private fun CategoryShortcut(usage: CategoryUsage) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(120.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryIconBadge(category = usage.category)
            Text(
                text = labelFor(usage.category),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(usage.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatItemCount(usage.fileCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryIconBadge(category: StorageCategory) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconFor(category),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Row of quick-access feature tiles routing to top-level destinations. */
@Composable
private fun QuickAccessRow(onNavigate: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item(key = "qa_search") {
            QuickAccessTile(
                icon = Icons.Filled.Search,
                label = "Search",
                onClick = { onNavigate(Destination.Search.route) },
            )
        }
        item(key = "qa_cleanup") {
            QuickAccessTile(
                icon = Icons.Filled.CleaningServices,
                label = "Cleanup",
                onClick = { onNavigate(Destination.Cleanup.route) },
            )
        }
        item(key = "qa_vault") {
            QuickAccessTile(
                icon = Icons.Filled.Lock,
                label = "Vault",
                onClick = { onNavigate(Destination.Vault.route) },
            )
        }
        item(key = "qa_settings") {
            QuickAccessTile(
                icon = Icons.Filled.Settings,
                label = "Settings",
                onClick = { onNavigate(Destination.Settings.route) },
            )
        }
    }
}

@Composable
private fun QuickAccessTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.width(110.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** A recently visited file/folder entry. */
@Composable
private fun RecentRow(
    item: FileItem,
    onClick: () -> Unit,
) {
    ListItem(
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
        modifier = Modifier.clickableRow(onClick),
    )
}

/** A saved bookmark entry. */
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
) {
    ListItem(
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
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.clickableRow(onClick),
    )
}

/** Small helper that makes a [ListItem] fill width and respond to taps. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this
        .fillMaxWidth()
        .clickable(onClick = onClick)

private fun iconFor(category: StorageCategory): ImageVector = when (category) {
    StorageCategory.IMAGES -> Icons.Filled.Image
    StorageCategory.VIDEOS -> Icons.Filled.Videocam
    StorageCategory.AUDIO -> Icons.Filled.Audiotrack
    StorageCategory.DOCUMENTS -> Icons.Filled.Description
    StorageCategory.ARCHIVES -> Icons.Filled.FolderZip
    StorageCategory.APPS -> Icons.Filled.PhoneAndroid
    StorageCategory.DOWNLOADS -> Icons.Filled.Download
    StorageCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun labelFor(category: StorageCategory): String = when (category) {
    StorageCategory.IMAGES -> "Images"
    StorageCategory.VIDEOS -> "Videos"
    StorageCategory.AUDIO -> "Audio"
    StorageCategory.DOCUMENTS -> "Documents"
    StorageCategory.ARCHIVES -> "Archives"
    StorageCategory.APPS -> "Apps"
    StorageCategory.DOWNLOADS -> "Downloads"
    StorageCategory.OTHER -> "Other"
}
