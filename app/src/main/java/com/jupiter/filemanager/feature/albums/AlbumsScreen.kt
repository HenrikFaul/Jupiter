package com.jupiter.filemanager.feature.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.data.media.Album
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.iconForFile
import java.io.File

/**
 * Gallery-style image albums, backed by `MediaStore` image buckets (Camera,
 * Screenshots, Download, WhatsApp Images, …) via [AlbumsViewModel]/`AlbumsSource`.
 *
 * The screen has two modes:
 *  - Albums grid: square album covers (Coil) with the album name and image count.
 *    Tapping an album drills into it.
 *  - Album images grid: square, cropped image thumbnails; the top bar shows a
 *    back-to-albums affordance and system-back returns to the grid rather than
 *    leaving the screen. Tapping an image delegates to [onOpenFile].
 *
 * Loading, empty and error states are all honest.
 *
 * @param onOpenFile invoked with the tapped image so the host can open it.
 * @param onBack invoked when the up affordance is tapped at the albums grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Inside an album, system-back returns to the album grid first.
    BackHandler(enabled = uiState.isInAlbum) {
        viewModel.backToAlbums()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.selectedAlbum?.name ?: "Albums",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isInAlbum) viewModel.backToAlbums() else onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (uiState.isInAlbum) "Back to albums" else "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && currentItemsEmpty(uiState) -> {
                LoadingView(modifier = Modifier.padding(innerPadding))
            }

            uiState.error != null && currentItemsEmpty(uiState) -> {
                ErrorView(
                    message = uiState.error ?: "Something went wrong.",
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.isInAlbum && uiState.images.isEmpty() -> {
                EmptyView(
                    title = "No images",
                    message = "This album has no images.",
                    icon = Icons.Outlined.PhotoLibrary,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            !uiState.isInAlbum && uiState.albums.isEmpty() -> {
                EmptyView(
                    title = "No albums yet",
                    message = "No image albums were found on this device.",
                    icon = Icons.Outlined.PhotoLibrary,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.isInAlbum -> {
                ImagesGrid(
                    images = uiState.images,
                    onOpenFile = onOpenFile,
                    contentPadding = innerPadding,
                )
            }

            else -> {
                AlbumsGrid(
                    albums = uiState.albums,
                    onOpenAlbum = viewModel::openAlbum,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

/** True when the currently visible listing has nothing to show. */
private fun currentItemsEmpty(state: AlbumsUiState): Boolean =
    if (state.isInAlbum) state.images.isEmpty() else state.albums.isEmpty()

/**
 * Grid of album covers: a square cropped cover with the album name and image count
 * beneath it. Tapping a cell drills into the album.
 */
@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    onOpenAlbum: (Album) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(
            items = albums,
            key = { it.bucketId },
        ) { album ->
            AlbumCell(album = album, onOpenAlbum = onOpenAlbum)
        }
    }
}

/** A single album: cover thumbnail plus name and count. */
@Composable
private fun AlbumCell(
    album: Album,
    onOpenAlbum: (Album) -> Unit,
) {
    val fallbackPainter = rememberVectorPainter(Icons.Outlined.PhotoLibrary)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onOpenAlbum(album) }
            .padding(4.dp),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(album.coverPath?.let { File(it) })
                .crossfade(true)
                .size(384)
                .build(),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
        Text(
            text = formatItemCount(album.count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Square, cropped thumbnail grid for the images inside an album. Uses the file's
 * type icon as placeholder/error fallback so a missing file never shows blank.
 */
@Composable
private fun ImagesGrid(
    images: List<FileItem>,
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
        gridItems(
            items = images,
            key = { it.path },
        ) { item ->
            ImageThumbnail(item = item, onOpenFile = onOpenFile)
        }
    }
}

/** A single square, cropped image cell. */
@Composable
private fun ImageThumbnail(
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
