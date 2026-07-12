package com.jupiter.filemanager.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen image gallery.
 *
 * Pure UI: [ImageGalleryViewModel] resolves the sibling images of the opened path and
 * exposes them via [ImageGalleryUiState]. This screen renders them in a swipeable
 * [HorizontalPager], starting on the originally-opened image, with a horizontal
 * thumbnail strip beneath for quick navigation. Images are decoded by Coil
 * ([AsyncImage]) directly from the file on disk.
 *
 * Visual language follows Jupiter's dark image stage, compact rounded thumbnails,
 * and the current thumbnail outlined in the teal primary color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGalleryScreen(
    onBack: () -> Unit,
    initialImages: List<FileItem>? = null,
    initialPath: String? = null,
    startInSlideshow: Boolean = false,
    onInitialImagesConsumed: () -> Unit = {},
) {
    val viewModel: ImageGalleryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Retain the in-memory hand-off for this destination even after the NavHost
    // clears its one-shot request. The ViewModel receives the same list so a
    // configuration change keeps the exact filtered order.
    val launchImages = remember {
        initialImages
            ?.filter { !it.isDirectory && it.type == com.jupiter.filemanager.domain.model.FileType.IMAGE }
            ?.distinctBy { it.path }
            .orEmpty()
    }

    LaunchedEffect(Unit) {
        if (launchImages.isNotEmpty()) {
            viewModel.useLaunchImages(launchImages, initialPath)
            onInitialImagesConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (startInSlideshow) "Slideshow" else "Gallery",
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
            val displayedImages = launchImages.ifEmpty { state.images }
            when {
                displayedImages.isNotEmpty() -> GalleryBody(
                    images = displayedImages,
                    initialIndex = if (launchImages.isNotEmpty()) {
                        launchImages.indexOfFirst { it.path == initialPath }.coerceAtLeast(0)
                    } else {
                        state.initialIndex
                    },
                    startInSlideshow = startInSlideshow,
                    modifier = Modifier.fillMaxSize(),
                )

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

                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryBody(
    images: List<FileItem>,
    initialIndex: Int,
    startInSlideshow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val startPage = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { images.size },
    )
    val thumbState = rememberLazyListState()
    val navigationScope = rememberCoroutineScope()
    var isPlaying by rememberSaveable { mutableStateOf(startInSlideshow) }

    // One frame is scheduled at a time. Pausing or leaving this destination
    // cancels the effect immediately; a swipe/manual navigation resets the full
    // three-second viewing interval for the newly settled image.
    LaunchedEffect(isPlaying, pagerState.settledPage, images.size) {
        if (SlideshowPolicy.canAutoAdvance(isPlaying, images.size)) {
            delay(SlideshowPolicy.FRAME_INTERVAL_MILLIS)
            pagerState.animateScrollToPage(
                SlideshowPolicy.nextIndex(pagerState.settledPage, images.size),
            )
        }
    }

    // Keep the thumbnail strip in sync with the currently-paged image.
    LaunchedEffect(pagerState.currentPage) {
        if (images.isNotEmpty()) {
            thumbState.animateScrollToItem(pagerState.currentPage)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
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

        Spacer(modifier = Modifier.height(8.dp))

        SlideshowControls(
            isPlaying = isPlaying,
            enabled = images.size > 1,
            onPrevious = {
                navigationScope.launch {
                    pagerState.animateScrollToPage(
                        SlideshowPolicy.previousIndex(pagerState.settledPage, images.size),
                    )
                }
            },
            onTogglePlayback = { isPlaying = !isPlaying },
            onNext = {
                navigationScope.launch {
                    pagerState.animateScrollToPage(
                        SlideshowPolicy.nextIndex(pagerState.settledPage, images.size),
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Thumbnail strip.
        ThumbnailStrip(
            images = images,
            selectedIndex = pagerState.currentPage,
            listState = thumbState,
            onSelect = { index ->
                navigationScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SlideshowControls(
    isPlaying: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous image",
                )
            }
            IconButton(onClick = onTogglePlayback, enabled = enabled) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause slideshow" else "Resume slideshow",
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNext, enabled = enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next image",
                )
            }
            Text(
                text = if (isPlaying && enabled) "Playing · 3 sec" else "Slideshow paused",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            )
        }
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
