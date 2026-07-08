package com.jupiter.filemanager.feature.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.RemoteEntry
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView

/**
 * Browser for a single remote connection (SMB/NAS, SFTP, FTP/FTPS, WebDAV).
 *
 * The target connection id and path are read by [RemoteBrowserViewModel] from its
 * [androidx.lifecycle.SavedStateHandle]. The screen lists the current directory's
 * [RemoteEntry] items (directories first), renders a breadcrumb of the path with an
 * "up" affordance, and reacts to taps: directories reload the listing in-place,
 * files trigger a download whose result is surfaced honestly in a snackbar.
 *
 * All protocol I/O runs in the repository layer and is delivered as results, so the
 * screen only ever renders loading / empty / error / content states and never
 * crashes on a network failure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteBrowserScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemoteBrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface the one-shot download result (success path or honest failure)
    // and then clear it so it does not re-show on recomposition.
    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.title.ifBlank { "Remote" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = uiState.path.ifBlank { "/" }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                    if (!uiState.isAtRoot) {
                        IconButton(onClick = viewModel::navigateUp) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "Up one level",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Breadcrumb(
                path = uiState.path,
                onSegmentClick = viewModel::navigateTo,
            )
            HorizontalDivider()

            // A download spinning bar shown above the list while a file transfers.
            if (uiState.isDownloading) {
                DownloadingBar(name = uiState.downloadingPath)
            }

            when {
                uiState.isLoading -> {
                    LoadingView()
                }

                uiState.error != null -> {
                    ErrorView(
                        message = uiState.error ?: "Something went wrong.",
                        onRetry = viewModel::retry,
                    )
                }

                uiState.isEmpty -> {
                    EmptyView(
                        title = "Empty folder",
                        message = "This folder has no files or subfolders.",
                        icon = Icons.Outlined.FolderOpen,
                    )
                }

                else -> {
                    RemoteEntryList(
                        entries = uiState.entries,
                        downloadingPath = uiState.downloadingPath,
                        onEntryClick = viewModel::openEntry,
                    )
                }
            }
        }
    }
}

/**
 * Honest empty state when the screen could not resolve a connection at all is
 * handled by the [ErrorView] branch above (the view model emits an error). This
 * composable renders an entry as a tappable row.
 */
@Composable
private fun RemoteEntryList(
    entries: List<RemoteEntry>,
    downloadingPath: String?,
    onEntryClick: (RemoteEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(
            items = entries,
            key = { it.path },
        ) { entry ->
            RemoteEntryRow(
                entry = entry,
                isDownloading = !entry.isDirectory && entry.path == downloadingPath,
                onClick = { onEntryClick(entry) },
            )
        }
    }
}

@Composable
private fun RemoteEntryRow(
    entry: RemoteEntry,
    isDownloading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = if (entry.isDirectory) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = iconForRemoteEntry(entry),
                    contentDescription = null,
                    tint = if (entry.isDirectory) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitleFor(entry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isDownloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else if (entry.isDirectory) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Horizontally scrollable breadcrumb of the current path. */
@Composable
private fun Breadcrumb(
    path: String,
    onSegmentClick: (String) -> Unit,
) {
    val absolute = path.startsWith("/")
    val segments = path.split('/').filter { it.isNotEmpty() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Root crumb.
        BreadcrumbSegment(
            label = if (absolute) "/" else "Share",
            onClick = { onSegmentClick(if (absolute) "/" else "") },
        )
        var accumulated = ""
        segments.forEachIndexed { index, segment ->
            accumulated = if (absolute) accumulated + "/" + segment
            else if (index == 0) segment
            else accumulated + "/" + segment
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            val target = accumulated
            BreadcrumbSegment(
                label = segment,
                onClick = { onSegmentClick(target) },
            )
        }
    }
}

@Composable
private fun BreadcrumbSegment(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun DownloadingBar(name: String?) {
    val fileName = name?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (fileName != null) "Downloading " + fileName + "…" else "Downloading…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Material icon for a remote entry: a folder icon for directories, a file icon otherwise. */
private fun iconForRemoteEntry(entry: RemoteEntry): ImageVector =
    if (entry.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile

/** Secondary line for a row: "Folder" for directories, size + modified time for files. */
private fun subtitleFor(entry: RemoteEntry): String =
    if (entry.isDirectory) {
        "Folder"
    } else {
        val size = formatBytes(entry.sizeBytes)
        if (entry.lastModified > 0L) {
            size + "  ·  " + formatRelativeTime(entry.lastModified)
        } else {
            size
        }
    }
