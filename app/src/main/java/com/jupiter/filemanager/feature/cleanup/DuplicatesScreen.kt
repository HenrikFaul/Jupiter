package com.jupiter.filemanager.feature.cleanup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.MediaQuality
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.iconForFile
import java.io.File
import kotlinx.coroutines.launch

/**
 * Lists duplicate file groups detected on the primary storage volume. Each group
 * shows its files, the user can select redundant copies to delete, and a
 * confirmation dialog guards the destructive action.
 *
 * Tapping a file row opens it via [onOpenFile]; a trailing copy icon copies the
 * file's absolute path to the clipboard. Each row also surfaces a probed
 * media-quality label when available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showConfirm by remember { mutableStateOf(false) }

    // When the user returns from the system settings screen after we asked for
    // All-Files-Access, re-run the scan so a just-granted permission recovers.
    LifecycleResumeEffect(state.permissionRequired) {
        if (state.permissionRequired) {
            viewModel.scan()
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val message = state.errorMessage ?: state.infoMessage
        if (message != null) {
            scope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate cleanup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.selectDuplicatesKeepingBest() }) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Keep best copy in each group",
                        )
                    }
                    // Toggle: select every extra copy when nothing is selected, otherwise clear the
                    // whole selection. One button does both (the ✓✓ used to only ever select).
                    val hasSelection = state.selectedCount > 0
                    IconButton(
                        onClick = {
                            if (hasSelection) viewModel.clearSelection() else viewModel.selectAllExtras()
                        },
                    ) {
                        Icon(
                            imageVector = if (hasSelection) {
                                Icons.Outlined.RemoveDone
                            } else {
                                Icons.Outlined.DoneAll
                            },
                            contentDescription = if (hasSelection) {
                                "Clear selection"
                            } else {
                                "Select all extra copies"
                            },
                        )
                    }
                    IconButton(
                        onClick = { viewModel.scan() },
                        enabled = !state.isScanning && !state.isDeleting,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Rescan",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.selectedCount > 0) {
                DeleteBar(
                    selectedCount = state.selectedCount,
                    reclaimableBytes = state.selectedReclaimableBytes,
                    isDeleting = state.isDeleting,
                    onDelete = { showConfirm = true },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.permissionRequired -> {
                    PermissionRequiredView()
                }

                state.groups.isEmpty() && state.isScanning -> {
                    LoadingView()
                }

                state.isEmpty -> {
                    EmptyView(
                        title = "No duplicates found",
                        message = "Your files look tidy. We didn't find any duplicate copies on this device.",
                        icon = Icons.Outlined.ContentCopy,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            SummaryCard(
                                groupCount = state.groups.size,
                                wastedBytes = state.totalWastedBytes,
                                isScanning = state.isScanning,
                            )
                        }
                        item {
                            KeepBestBanner(
                                enabled = !state.isDeleting,
                                onKeepBest = { viewModel.selectDuplicatesKeepingBest() },
                            )
                        }
                        items(state.groups, key = { it.hash }) { group ->
                            DuplicateGroupCard(
                                group = group,
                                selectedPaths = state.selectedPaths,
                                qualities = state.qualities,
                                onToggle = viewModel::toggleSelection,
                                onOpenFile = onOpenFile,
                                onCopyPath = { path ->
                                    clipboardManager.setText(AnnotatedString(path))
                                    viewModel.notify("Path copied")
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete ${state.selectedCount} file" + if (state.selectedCount == 1) "" else "s") },
            text = {
                Text(
                    "This will permanently delete the selected copies and reclaim " +
                        "${formatBytes(state.selectedReclaimableBytes)}. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        viewModel.deleteSelected()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Actionable empty-state shown when the app lacks All-Files-Access. Explains why
 * the scan can't run and offers a button that opens the system settings screen to
 * grant access. On return, the screen's ON_RESUME effect re-runs the scan.
 */
@Composable
private fun PermissionRequiredView() {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "All Files Access needed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To find duplicate files across your storage, Jupiter needs " +
                "permission to access all files. Grant access and the scan will start " +
                "automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                runCatching {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.fromParts("package", ctx.packageName, null),
                        )
                    } else {
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", ctx.packageName, null),
                        )
                    }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ctx.startActivity(intent)
                }.onFailure {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                        )
                    }
                }
            },
        ) {
            Text("Grant All Files Access")
        }
    }
}

@Composable
private fun SummaryCard(
    groupCount: Int,
    wastedBytes: Long,
    isScanning: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = formatBytes(wastedBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "reclaimable across $groupCount duplicate group" +
                    if (groupCount == 1) "" else "s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (isScanning) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Still scanning…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Prominent one-tap action that selects every duplicate copy while keeping the
 * best copy in each group. The user can then review the selection and remove the
 * rest with the existing delete flow. This absorbs the former "Smart Merge".
 */
@Composable
private fun KeepBestBanner(
    enabled: Boolean,
    onKeepBest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Keep the best copy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Auto-select every duplicate and keep the highest-quality copy in each group.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onKeepBest,
                enabled = enabled,
            ) {
                Text("Keep best")
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selectedPaths: Set<String>,
    qualities: Map<String, MediaQuality>,
    onToggle: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onCopyPath: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${group.files.size} copies",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${formatBytes(group.wastedBytes)} wasted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            // Best copy first: highest probed quality score, then largest size.
            val ranked = remember(group, qualities) {
                group.files.sortedWith(
                    compareByDescending<FileItem> { qualities[it.path]?.score ?: 0L }
                        .thenByDescending { it.sizeBytes },
                )
            }
            val bestQuality = qualities[ranked.firstOrNull()?.path]
            ranked.forEachIndexed { index, file ->
                // Key each row by its file path so Compose never REUSES a row's node (and its
                // Coil AsyncImage) for a DIFFERENT file after the list changes — e.g. when a
                // deleted copy is removed and the rows below shift up. Without this the recycled
                // AsyncImage kept showing the previous file's cached thumbnail, so a deleted row
                // appeared to be replaced by an unrelated ("random") image.
                key(file.path) {
                    val quality = qualities[file.path]
                    DuplicateFileRow(
                        file = file,
                        isKept = index == 0,
                        isSelected = file.path in selectedPaths,
                        quality = quality,
                        relativeNote = relativeQualityNote(
                            isBest = index == 0,
                            best = bestQuality,
                            current = quality,
                        ),
                        onToggle = { onToggle(file.path) },
                        onOpen = { onOpenFile(file) },
                        onCopyPath = { onCopyPath(file.path) },
                    )
                    if (index != ranked.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateFileRow(
    file: FileItem,
    isKept: Boolean,
    isSelected: Boolean,
    quality: MediaQuality?,
    relativeNote: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onCopyPath: () -> Unit,
) {
    // The best copy is highlighted; the remaining (removable) copies are muted.
    val rowBackground = if (isKept) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    val nameColor = if (isKept) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .background(rowBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isThumbnailable = file.type == FileType.IMAGE || file.type == FileType.VIDEO
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp),
        ) {
            if (isThumbnailable) {
                // Real image/video thumbnail via Coil. Video frames decode because the
                // app registers a VideoFrameDecoder app-wide (see JupiterApp). The type
                // icon serves as both placeholder and error fallback.
                val fallbackPainter = rememberVectorPainter(iconForFile(file))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(file.path))
                        .crossfade(true)
                        .size(96)
                        // Stable per-path cache keys so a thumbnail is never confused with
                        // another file's (belt-and-braces alongside the row key() above).
                        .memoryCacheKey(file.path)
                        .diskCacheKey(file.path)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = fallbackPainter,
                    error = fallbackPainter,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForFile(file),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isKept) FontWeight.SemiBold else FontWeight.Normal,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isKept) {
                    QualityBadge(
                        text = "BEST",
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    QualityBadge(
                        text = "DUPLICATE",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (isKept) {
                    "Recommended to keep • ${formatBytes(file.sizeBytes)}"
                } else {
                    "${formatBytes(file.sizeBytes)} • ${formatRelativeTime(file.lastModified)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isKept) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val qualityLabel = quality?.label?.takeIf { it.isNotBlank() }
            if (qualityLabel != null || relativeNote != null) {
                val qualityLine = buildString {
                    if (qualityLabel != null) append(qualityLabel)
                    if (relativeNote != null) {
                        if (isNotEmpty()) append(" • ")
                        append(relativeNote)
                    }
                }
                Text(
                    text = qualityLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isKept) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = file.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onCopyPath) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy path",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
        )
    }
}

/** Small pill badge used to mark the best copy vs. removable duplicates. */
@Composable
private fun QualityBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Tiny relative-quality indicator for a copy compared to the best one in its
 * group. Only meaningful when both files have a probed (non-zero) score.
 */
private fun relativeQualityNote(
    isBest: Boolean,
    best: MediaQuality?,
    current: MediaQuality?,
): String? {
    if (best == null || current == null) return null
    if (best.score <= 0L || current.score <= 0L) return null
    if (isBest) return "best quality"
    return when {
        current.score >= best.score -> "same quality"
        current.width > 0 && best.width > 0 && current.height > 0 && best.height > 0 &&
            (current.width.toLong() * current.height) < (best.width.toLong() * best.height) ->
            "lower resolution"
        else -> "lower quality"
    }
}

@Composable
private fun DeleteBar(
    selectedCount: Int,
    reclaimableBytes: Long,
    isDeleting: Boolean,
    onDelete: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Clear the system navigation bar (gesture pill / 3-button bar) so the Delete
                // action is never drawn underneath it and stays tappable on every device.
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${formatBytes(reclaimableBytes)} reclaimable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onDelete,
                enabled = !isDeleting,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDeleting) "Deleting…" else "Delete",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
