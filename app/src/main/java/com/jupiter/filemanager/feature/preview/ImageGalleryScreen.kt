package com.jupiter.filemanager.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import java.io.File

/**
 * Full-screen image gallery.
 *
 * Pure UI: [ImageGalleryViewModel] resolves the sibling images of the opened path and
 * exposes them via [ImageGalleryUiState]. This screen renders them in a swipeable
 * [HorizontalPager], starting on the originally-opened image, with a horizontal
 * thumbnail strip beneath for quick navigation. Images are decoded by Coil
 * ([AsyncImage]) directly from the file on disk.
 *
 * Visual language follows the NEXUS brand: a dark image stage, rounded 16dp thumbnails,
 * and the current thumbnail outlined in the vivid-blue primary color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGalleryScreen(
    onBack: () -> Unit,
) {
    val viewModel: ImageGalleryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Gallery",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> LoadingView(modifier = Modifier.fillMaxSize())

                state.error != null -> ErrorView(
                    message = state.error ?: "Couldn't open this gallery.",
                    modifier = Modifier.fillMaxSize(),
                )

                state.isEmpty -> EmptyView(
                    title = "No images",
                    message = "There are no images in this folder to display.",
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> GalleryBody(
                    images = state.images,
                    initialIndex = state.initialIndex,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryBody(
    images: List<FileItem>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
) {
    val startPage = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { images.size },
    )
    val thumbState = rememberLazyListState()

    // Keep the thumbnail strip in sync with the currently-paged image.
    LaunchedEffect(pagerState.currentPage) {
        if (images.isNotEmpty()) {
            thumbState.animateScrollToItem(pagerState.currentPage)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        // Main image stage.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val item = images[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(item.path))
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Caption: current file name + position indicator.
        val current = images.getOrNull(pagerState.currentPage)
        if (current != null) {
            Text(
                text = current.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        Text(
            text = "${pagerState.currentPage + 1} / ${images.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Thumbnail strip.
        ThumbnailStrip(
            images = images,
            selectedIndex = pagerState.currentPage,
            listState = thumbState,
            onSelect = { index ->
                // Snap the pager to the tapped thumbnail.
                pagerState.requestScrollToPage(index)
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ThumbnailStrip(
    images: List<FileItem>,
    selectedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(
            items = images,
            key = { _, item -> item.path },
        ) { index, item ->
            val selected = index == selectedIndex
            val context = LocalContext.current
            val request = remember(item.path) {
                ImageRequest.Builder(context)
                    .data(File(item.path))
                    .crossfade(true)
                    .build()
            }
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(index) },
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}
