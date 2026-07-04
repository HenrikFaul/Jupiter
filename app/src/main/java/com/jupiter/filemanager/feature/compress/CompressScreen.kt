package com.jupiter.filemanager.feature.compress

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.data.media.CompressPreset
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.iconForFile
import java.io.File

/**
 * Compress screen.
 *
 * Reads the phone's real display via the ViewModel and recommends a sensible
 * target resolution, then compresses a chosen image/video and shows the
 * Original -> Compressed size delta with space saved.
 *
 * Two phases, both driven purely by [CompressUiState]:
 *  - **Picker**: a grid of the device's images/videos, largest first.
 *  - **Configure/Run**: source preview, device line, recommended preset chips, a
 *    Compress button with live progress, and a before/after result card offering
 *    open/share of the output.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(onBack: () -> Unit) {
    val viewModel: CompressViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compress") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.sourceItem != null && !uiState.isCompressing) {
                            viewModel.clearSource()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.sourceItem != null -> ConfigureContent(
                    state = uiState,
                    onSelectPreset = viewModel::selectPreset,
                    onCompress = viewModel::compress,
                    onOpen = { path -> openOutput(context, path) },
                    onShare = { path -> shareOutput(context, path) },
                    onChooseAnother = viewModel::clearSource,
                )

                uiState.isLoadingMedia -> LoadingView()

                uiState.isEmpty -> EmptyView(
                    title = "Nothing to compress",
                    message = "No images or videos were found on this device. " +
                        "Grant media access, then pull to refresh.",
                    icon = Icons.Filled.PhotoLibrary,
                )

                else -> PickerContent(
                    state = uiState,
                    onPick = viewModel::selectSource,
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Picker phase                                                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun PickerContent(
    state: CompressUiState,
    onPick: (FileItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.availableMedia, key = { it.path }) { item ->
            MediaCell(item = item, onPick = onPick)
        }
    }
}

/**
 * One square, cropped media thumbnail with a size badge and (for video) a play
 * glyph, plus the full filename beneath it. Uses the file's type icon as
 * placeholder/error, matching the browser. The AsyncImage is driven by an
 * [ImageRequest] over the file path so the app-wide loader renders both image
 * and video thumbnails (falling back to the type icon on error).
 */
@Composable
private fun MediaCell(
    item: FileItem,
    onPick: (FileItem) -> Unit,
) {
    val fallback = rememberVectorPainter(iconForFile(item))
    Column(
        modifier = Modifier.clickable { onPick(item) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(item.path))
                    .crossfade(true)
                    .size(256)
                    .build(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                placeholder = fallback,
                error = fallback,
                modifier = Modifier.fillMaxSize(),
            )

            if (item.type == FileType.VIDEO) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                )
            }

            Text(
                text = formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Text(
            text = item.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Configure / run phase                                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun ConfigureContent(
    state: CompressUiState,
    onSelectPreset: (CompressPreset) -> Unit,
    onCompress: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: (String) -> Unit,
    onChooseAnother: () -> Unit,
) {
    val item = state.sourceItem ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Device / screen recommendation line.
        Text(
            text = state.deviceLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Source preview + identity.
        SourceHeader(item = item, state = state)

        if (state.result != null) {
            ResultCard(
                result = state.result,
                onOpen = onOpen,
                onShare = onShare,
                onChooseAnother = onChooseAnother,
            )
            return@Column
        }

        if (state.presets.isNotEmpty()) {
            Text(
                text = "Target quality",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PresetChips(
                presets = state.presets,
                selected = state.selectedPreset,
                estimates = state.estimates,
                enabled = !state.isCompressing,
                onSelect = onSelectPreset,
            )
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (state.isCompressing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (item.type == FileType.VIDEO) {
                        "Compressing video… ${state.progress}%"
                    } else {
                        "Compressing image…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.type == FileType.VIDEO && state.progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            Button(
                onClick = onCompress,
                enabled = state.canCompress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Compress,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compress")
            }
        }
    }
}

@Composable
private fun SourceHeader(
    item: FileItem,
    state: CompressUiState,
) {
    val fallback = rememberVectorPainter(iconForFile(item))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(item.path))
                .crossfade(true)
                .size(256)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            placeholder = fallback,
            error = fallback,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val dims = state.sourceDims
            val dimsText = if (dims != null) "${dims.width}×${dims.height} · " else ""
            Text(
                text = "$dimsText${formatBytes(item.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresetChips(
    presets: List<CompressPreset>,
    selected: CompressPreset?,
    estimates: Map<Int, Long>,
    enabled: Boolean,
    onSelect: (CompressPreset) -> Unit,
) {
    // A simple wrapping flow via a Column of Rows would need FlowRow; keep it
    // robust with a horizontally scrollable single row of chips.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.chunked(3).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPresets.forEach { preset ->
                    val isSelected = selected != null &&
                        preset.targetLongEdgePx == selected.targetLongEdgePx &&
                        preset.label == selected.label
                    val estimate = estimates[preset.targetLongEdgePx]
                    FilterChip(
                        selected = isSelected,
                        enabled = enabled,
                        onClick = { onSelect(preset) },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (preset.recommended) "${preset.label} ★" else preset.label,
                                )
                                if (estimate != null) {
                                    Text(
                                        text = "≈ ${formatBytes(estimate)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: CompressResult,
    onOpen: (String) -> Unit,
    onShare: (String) -> Unit,
    onChooseAnother: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Compression complete",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Original ${formatBytes(result.originalBytes)}  →  " +
                    "Compressed ${formatBytes(result.compressedBytes)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Saved ${formatBytes(result.savedBytes)} " +
                    "(${(result.savedFraction * 100).toInt()}%)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen(result.outputPath) }) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open")
                }
                OutlinedButton(onClick = { onShare(result.outputPath) }) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }

            TextButton(onClick = onChooseAnother) {
                Text("Compress another")
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  External intents (open / share the output via FileProvider)               */
/* -------------------------------------------------------------------------- */

/**
 * Opens [path] in an external viewer via an [Intent.ACTION_VIEW] backed by a
 * [androidx.core.content.FileProvider] content URI (authority = applicationId +
 * ".fileprovider"). All failures are swallowed so the UI never crashes.
 */
private fun openOutput(context: Context, path: String) {
    launchForOutput(context, path, Intent.ACTION_VIEW)
}

/**
 * Shares [path] via an [Intent.ACTION_SEND] backed by a FileProvider content URI.
 * All failures are swallowed so the UI never crashes.
 */
private fun shareOutput(context: Context, path: String) {
    launchForOutput(context, path, Intent.ACTION_SEND)
}

private fun launchForOutput(context: Context, path: String, action: String) {
    try {
        val file = File(path)
        val authority = context.packageName + ".fileprovider"
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        val mime = mimeForExtension(file.extension)

        val intent = Intent(action).apply {
            if (action == Intent.ACTION_SEND) {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
            } else {
                setDataAndType(uri, mime)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val title = if (action == Intent.ACTION_SEND) "Share with" else "Open with"
        context.startActivity(
            Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: ActivityNotFoundException) {
        // No app can handle this file; nothing else we can do from here.
    } catch (_: IllegalArgumentException) {
        // FileProvider couldn't map the path (outside configured roots); ignore.
    }
}

private fun mimeForExtension(ext: String): String = when (ext.lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "jpg", "jpeg" -> "image/jpeg"
    else -> "*/*"
}
