package com.jupiter.filemanager.feature.preview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import java.io.File

/**
 * Full-screen file preview.
 *
 * Rendering is chosen from [PreviewUiState.previewKind]:
 *  - IMAGE / VIDEO  -> Coil [AsyncImage] (video frames via [VideoFrameDecoder]).
 *  - TEXT           -> scrollable monospace [Text].
 *  - AUDIO / PDF / UNSUPPORTED -> an info card with an "Open with" button that fires
 *    an [Intent.ACTION_VIEW] using a [FileProvider] content URI.
 *
 * All file IO (resolving the item, reading text) happens in [PreviewViewModel]; this
 * composable is pure UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    path: String,
    onBack: () -> Unit,
) {
    val viewModel: PreviewViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.file?.name ?: "Preview",
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
                actions = {
                    val file = state.file
                    if (file != null) {
                        IconButton(onClick = { openWithExternalApp(context, file) }) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open with another app",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        PreviewContent(
            state = state,
            context = context,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
private fun PreviewContent(
    state: PreviewUiState,
    context: Context,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingView(modifier = modifier)

        state.error != null && state.file == null -> ErrorView(
            message = state.error,
            modifier = modifier,
        )

        else -> {
            val file = state.file
            if (file == null) {
                ErrorView(
                    message = state.error ?: "Unable to preview this file.",
                    modifier = modifier,
                )
            } else {
                when (state.previewKind) {
                    PreviewKind.IMAGE -> ImagePreview(file = file, modifier = modifier)
                    PreviewKind.VIDEO -> VideoPreview(file = file, modifier = modifier)
                    PreviewKind.TEXT -> TextPreview(
                        content = state.textContent,
                        error = state.error,
                        modifier = modifier,
                    )

                    PreviewKind.AUDIO -> UnsupportedPreview(
                        file = file,
                        context = context,
                        icon = Icons.Default.Audiotrack,
                        title = "Audio file",
                        message = "Play this file in your preferred media player.",
                        modifier = modifier,
                    )

                    PreviewKind.PDF -> UnsupportedPreview(
                        file = file,
                        context = context,
                        icon = Icons.Default.PictureAsPdf,
                        title = "PDF document",
                        message = "Open this document in a PDF viewer.",
                        modifier = modifier,
                    )

                    PreviewKind.UNSUPPORTED -> UnsupportedPreview(
                        file = file,
                        context = context,
                        icon = Icons.Default.OpenInNew,
                        title = "Preview not available",
                        message = "This file type can't be previewed here. Open it with another app.",
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(file: FileItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(file.path))
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun VideoPreview(file: FileItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier.padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(file.path))
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // Overlay an "open with" affordance so the user can actually play the video.
        Button(
            onClick = { openWithExternalApp(context, file) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Play video",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun TextPreview(
    content: String?,
    error: String?,
    modifier: Modifier = Modifier,
) {
    when {
        content == null && error != null -> ErrorView(message = error, modifier = modifier)
        content.isNullOrEmpty() -> Box(
            modifier = modifier.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "This file is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

@Composable
private fun UnsupportedPreview(
    file: FileItem,
    context: Context,
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBytes(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { openWithExternalApp(context, file) }) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Open with",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Launches an external app via [Intent.ACTION_VIEW] for [file], exposing it through a
 * [FileProvider] content URI (authority = applicationId + ".fileprovider"). Read
 * permission is granted to receiving apps. Failures are swallowed so the UI never
 * crashes when no handler exists.
 */
private fun openWithExternalApp(context: Context, file: FileItem) {
    try {
        val authority = context.packageName + ".fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, File(file.path))
        val mime = file.mimeType ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: ActivityNotFoundException) {
        // No app can handle this file; nothing else we can do from here.
    } catch (_: IllegalArgumentException) {
        // FileProvider couldn't map the path (outside configured roots); ignore.
    }
}
