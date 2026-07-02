package com.jupiter.filemanager.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.iconForFile
import java.io.File

/**
 * Instant, device-wide listing for a single [StorageCategory], backed by
 * `MediaStore` (see [CategoryBrowseViewModel]/`MediaStoreCategorySource`) rather
 * than a recursive filesystem walk.
 *
 * Images and videos render as a cropped square thumbnail grid; every other
 * category renders as a dense details list ([FileRow]). A small header reports
 * the item count and combined size. Tapping any item delegates to [onOpenFile]
 * (which the caller routes through the shared `openByType` helper). The sort menu
 * offers Date / Name / Size. Loading, empty and error states are all honest.
 *
 * @param onOpenFile invoked with the tapped file so the host can open it.
 * @param onBack invoked when the up affordance is tapped.
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
        topBar = {
            TopAppBar(
                title = { Text(text = categoryTitle(uiState.category)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    SortMenu(
                        current = uiState.sort,
                        onSelect = viewModel::setSort,
                    )
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.items.isEmpty() -> {
                LoadingView(modifier = Modifier.padding(innerPadding))
            }

            uiState.error != null && uiState.items.isEmpty() -> {
                ErrorView(
                    message = uiState.error ?: "Something went wrong.",
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.items.isEmpty() -> {
                EmptyView(
                    title = "Nothing here yet",
                    message = "No ${categoryTitle(uiState.category).lowercase()} were " +
                        "found on this device.",
                    icon = Icons.Outlined.FolderOff,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.isGrid -> {
                CategoryGrid(
                    uiState = uiState,
                    onOpenFile = onOpenFile,
                    contentPadding = innerPadding,
                )
            }

            else -> {
                CategoryList(
                    uiState = uiState,
                    onOpenFile = onOpenFile,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

/**
 * Thumbnail grid for images and videos: square, cropped Coil cells that fall back
 * to the type icon while loading or on error so a missing file never shows blank.
 */
@Composable
private fun CategoryGrid(
    uiState: CategoryBrowseUiState,
    onOpenFile: (FileItem) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CountHeader(uiState = uiState)
        }
        gridItems(
            items = uiState.items,
            key = { it.path },
        ) { item ->
            GridThumbnail(item = item, onOpenFile = onOpenFile)
        }
    }
}

/**
 * A single square, cropped thumbnail cell. Uses the file's type icon as both the
 * placeholder and the error fallback (mirrors [FileRow]'s thumbnail behaviour).
 */
@Composable
private fun GridThumbnail(
    item: FileItem,
    onOpenFile: (FileItem) -> Unit,
) {
    val fallbackPainter = rememberVectorPainter(iconForFile(item))
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(File(item.path))
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
            .clip(MaterialTheme.shapes.small)
            .clickable { onOpenFile(item) },
    )
}

/**
 * Details list for audio, documents, archives, apps, downloads and other files.
 * Reuses the dense [FileRow] for a consistent look with the browser.
 */
@Composable
private fun CategoryList(
    uiState: CategoryBrowseUiState,
    onOpenFile: (FileItem) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "__header__") {
            CountHeader(uiState = uiState)
        }
        items(
            items = uiState.items,
            key = { it.path },
        ) { item ->
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

/**
 * Small header line showing the item count and the combined size of the listing.
 */
@Composable
private fun CountHeader(uiState: CategoryBrowseUiState) {
    Text(
        text = "${formatItemCount(uiState.itemCount)}  •  ${formatBytes(uiState.totalSizeBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * Overflow sort menu offering Date / Name / Size, with the active order checked.
 */
@Composable
private fun SortMenu(
    current: CategorySort,
    onSelect: (CategorySort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Filled.Sort,
            contentDescription = "Sort",
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        SortOption(CategorySort.DATE_DESC, "Date", current) {
            onSelect(it); expanded = false
        }
        SortOption(CategorySort.NAME_ASC, "Name", current) {
            onSelect(it); expanded = false
        }
        SortOption(CategorySort.SIZE_DESC, "Size", current) {
            onSelect(it); expanded = false
        }
    }
}

@Composable
private fun SortOption(
    option: CategorySort,
    label: String,
    current: CategorySort,
    onSelect: (CategorySort) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onSelect(option) },
        leadingIcon = {
            RadioButton(
                selected = option == current,
                onClick = { onSelect(option) },
            )
        },
    )
}

/** Human-readable title for a [StorageCategory], used in the top bar and states. */
private fun categoryTitle(category: StorageCategory): String = when (category) {
    StorageCategory.IMAGES -> "Images"
    StorageCategory.VIDEOS -> "Videos"
    StorageCategory.AUDIO -> "Audio"
    StorageCategory.DOCUMENTS -> "Documents"
    StorageCategory.ARCHIVES -> "Archives"
    StorageCategory.APPS -> "APKs"
    StorageCategory.DOWNLOADS -> "Downloads"
    StorageCategory.OTHER -> "Other"
}
