package com.jupiter.filemanager.feature.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.data.media.CategorySort
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.feature.browser.components.FileRow
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterPill
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.theme.JupiterDesign
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Device-wide category browser backed by MediaStore. Photos follow the supplied
 * dark, teal gallery treatment while retaining real file opening, MediaStore
 * sorting, honest loading/error states, and folder filters backed by file paths.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBrowseScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryBrowseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            CategoryBrowseTopBar(
                uiState = uiState,
                onBack = onBack,
                onSort = viewModel::setSort,
            )

            when {
                uiState.isLoading && uiState.items.isEmpty() && !uiState.isPhotoFilterEmpty -> {
                    LoadingView(modifier = Modifier.weight(1f))
                }

                uiState.error != null && uiState.items.isEmpty() && !uiState.isPhotoFilterEmpty -> {
                    ErrorView(
                        message = uiState.error ?: "Something went wrong.",
                        onRetry = viewModel::retry,
                        modifier = Modifier.weight(1f),
                    )
                }

                uiState.category == StorageCategory.IMAGES -> {
                    PhotoCategoryGrid(
                        uiState = uiState,
                        onOpenFile = onOpenFile,
                        onFilter = viewModel::setPhotoFilter,
                        modifier = Modifier.weight(1f),
                    )
                }

                uiState.items.isEmpty() -> {
                    EmptyView(
                        title = "Nothing here yet",
                        message = "No ${categoryTitle(uiState.category).lowercase()} were found on this device.",
                        icon = Icons.Outlined.FolderOff,
                        modifier = Modifier.weight(1f),
                    )
                }

                uiState.isGrid -> {
                    MediaCategoryGrid(
                        uiState = uiState,
                        onOpenFile = onOpenFile,
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    CategoryList(
                        uiState = uiState,
                        onOpenFile = onOpenFile,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBrowseTopBar(
    uiState: CategoryBrowseUiState,
    onBack: () -> Unit,
    onSort: (CategorySort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryTitle(uiState.category),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${formatItemCount(uiState.itemCount)}  •  ${formatBytes(uiState.totalSizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SortMenu(current = uiState.sort, onSelect = onSort)
    }
}

/** The Photos reference's folder choices, all backed by [PhotoLocationFilter.matches]. */
@Composable
private fun PhotoFilterRow(
    selected: PhotoLocationFilter,
    onSelect: (PhotoLocationFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PhotoLocationFilter.entries, key = { it.name }) { filter ->
            JupiterPill(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 96.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = iconForPhotoFilter(filter),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = filter.label, style = MaterialTheme.typography.labelLarge)
                    if (filter == selected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCategoryGrid(
    uiState: CategoryBrowseUiState,
    onOpenFile: (FileItem) -> Unit,
    onFilter: (PhotoLocationFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PhotoFilterRow(selected = uiState.photoFilter, onSelect = onFilter)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(10.dp))
            CategoryOverviewCard(uiState = uiState)
        }
        if (uiState.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        }

        if (uiState.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyView(
                    title = "No ${uiState.photoFilter.label.lowercase()} photos",
                    message = "Try another folder filter to see the rest of your photo library.",
                    icon = Icons.Outlined.FolderOff,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                )
            }
        } else if (uiState.sort == CategorySort.DATE_DESC) {
            photoDateGroups(uiState.items).forEach { (dayStart, dayItems) ->
                item(key = "date:$dayStart", span = { GridItemSpan(maxLineSpan) }) {
                    PhotoDateHeader(dayStart = dayStart, items = dayItems)
                }
                gridItems(items = dayItems, key = { "photo:${it.path}" }) { item ->
                    GridThumbnail(item = item, onOpenFile = onOpenFile)
                }
            }
        } else {
            // Name and size are global sorts; do not regroup them and silently change that order.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Photos • ${sortLabel(uiState.sort)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            gridItems(items = uiState.items, key = { "photo:${it.path}" }) { item ->
                GridThumbnail(item = item, onOpenFile = onOpenFile)
            }
        }
    }
}

@Composable
private fun MediaCategoryGrid(
    uiState: CategoryBrowseUiState,
    onOpenFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { CategoryOverviewCard(uiState = uiState) }
        gridItems(items = uiState.items, key = { "media:${it.path}" }) { item ->
            GridThumbnail(item = item, onOpenFile = onOpenFile)
        }
    }
}

@Composable
private fun CategoryList(
    uiState: CategoryBrowseUiState,
    onOpenFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "overview") { CategoryOverviewCard(uiState = uiState) }
        items(items = uiState.items, key = { "file:${it.path}" }) { item ->
            FileRow(
                item = item,
                selected = false,
                selectionMode = false,
                onClick = { onOpenFile(item) },
                onLongClick = { onOpenFile(item) },
                dense = true,
            )
        }
    }
}

@Composable
private fun CategoryOverviewCard(uiState: CategoryBrowseUiState) {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = iconForCategory(uiState.category),
                tint = colorForCategory(uiState.category),
                size = 56.dp,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categorySummaryTitle(uiState),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatBytes(uiState.totalSizeBytes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatItemCount(uiState.itemCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotoDateHeader(dayStart: Long, items: List<FileItem>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = photoDayLabel(dayStart),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${formatItemCount(items.size)} • ${formatBytes(items.sumOf { it.sizeBytes })}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Cropped thumbnail with an icon fallback; content URI strings work as well as file paths. */
@Composable
private fun GridThumbnail(item: FileItem, onOpenFile: (FileItem) -> Unit) {
    val fallbackPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(iconForFile(item))
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(item.path)
            .crossfade(true)
            .size(256)
            .build(),
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        placeholder = fallbackPainter,
        error = fallbackPainter,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(JupiterDesign.CompactCardShape)
            .clickable { onOpenFile(item) },
    )
}

@Composable
private fun SortMenu(current: CategorySort, onSelect: (CategorySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(imageVector = Icons.Filled.Sort, contentDescription = "Sort: ${sortLabel(current)}")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sortOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(sortLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    leadingIcon = { RadioButton(selected = option == current, onClick = null) },
                )
            }
        }
    }
}

private val sortOptions = listOf(CategorySort.DATE_DESC, CategorySort.NAME_ASC, CategorySort.SIZE_DESC)

private fun categoryTitle(category: StorageCategory): String = when (category) {
    StorageCategory.IMAGES -> "Photos"
    StorageCategory.VIDEOS -> "Videos"
    StorageCategory.AUDIO -> "Audio"
    StorageCategory.DOCUMENTS -> "Documents"
    StorageCategory.ARCHIVES -> "Archives"
    StorageCategory.APPS -> "APKs"
    StorageCategory.DOWNLOADS -> "Downloads"
    StorageCategory.OTHER -> "Other"
}

private fun categorySummaryTitle(state: CategoryBrowseUiState): String = when (state.category) {
    StorageCategory.IMAGES -> if (state.photoFilter == PhotoLocationFilter.ALL) "Photos" else "${state.photoFilter.label} photos"
    else -> categoryTitle(state.category)
}

private fun sortLabel(sort: CategorySort): String = when (sort) {
    CategorySort.DATE_DESC -> "Newest first"
    CategorySort.NAME_ASC -> "Name (A–Z)"
    CategorySort.SIZE_DESC -> "Largest first"
}

private fun iconForPhotoFilter(filter: PhotoLocationFilter): ImageVector = when (filter) {
    PhotoLocationFilter.ALL, PhotoLocationFilter.CAMERA -> Icons.Filled.Image
    PhotoLocationFilter.SCREENSHOTS -> Icons.Outlined.PhotoLibrary
    PhotoLocationFilter.DOWNLOADS -> Icons.Filled.Download
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

/** Groups the actual image timestamps by their local calendar date. */
private fun photoDateGroups(items: List<FileItem>): List<Pair<Long, List<FileItem>>> =
    items.groupBy { photoDayStart(it.lastModified) }.toList()

private fun photoDayStart(epochMillis: Long): Long {
    if (epochMillis <= 0L) return Long.MIN_VALUE
    return Calendar.getInstance().run {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}

private fun photoDayLabel(dayStart: Long): String {
    if (dayStart == Long.MIN_VALUE) return "Unknown date"
    val today = photoDayStart(System.currentTimeMillis())
    val yesterday = Calendar.getInstance().run {
        timeInMillis = today
        add(Calendar.DAY_OF_YEAR, -1)
        timeInMillis
    }
    return when (dayStart) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dayStart))
    }
}
