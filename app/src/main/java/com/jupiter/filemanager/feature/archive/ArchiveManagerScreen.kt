package com.jupiter.filemanager.feature.archive

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.LoadingView
import java.util.Locale

/**
 * Archive manager screen.
 *
 * Lists the archive files found in the folder resolved from the navigation `path`
 * argument (its parent folder when a file is supplied, the storage root when
 * absent). Each archive can be extracted into a sibling folder, and a "Create ZIP"
 * action compresses the folder's contents into a new archive.
 *
 * Extraction is multi-format: ZIP-family archives (zip / jar / apk / aar / war) plus
 * tar, tar.gz / tgz, gz, tar.bz2 / bz2, 7z and rar are all detected by extension and
 * dispatched through [com.jupiter.filemanager.data.file.ArchiveManager.extractArchive]
 * by the ViewModel. Each row shows a small badge with the detected format.
 *
 * While a create/extract operation runs, an inline progress panel renders the live
 * [com.jupiter.filemanager.domain.model.FileOperationProgress] published by the
 * ViewModel. This composable performs no IO of its own — all archive work is driven
 * by [ArchiveManagerViewModel] via [com.jupiter.filemanager.data.file.ArchiveManager].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveManagerScreen(
    onBack: () -> Unit,
) {
    val viewModel: ArchiveManagerViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Archive Manager",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (state.folderName.isNotBlank()) {
                            Text(
                                text = state.folderName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
            )
        },
        floatingActionButton = {
            if (state.canCreateArchive) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::createArchive,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.FolderZip,
                            contentDescription = null,
                        )
                    },
                    text = { Text("Create ZIP") },
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
                state.isLoading -> LoadingView()

                state.isBusy -> OperationProgressPanel(
                    state = state,
                    onCancel = viewModel::cancelOperation,
                )

                state.error != null && state.archives.isEmpty() -> EmptyView(
                    title = "Couldn't open folder",
                    message = state.error ?: "Something went wrong.",
                    icon = Icons.Filled.Archive,
                )

                state.archives.isEmpty() -> EmptyView(
                    title = "No archives here",
                    message = state.emptyMessage
                        ?: "Compress files into a ZIP to get started.",
                    icon = Icons.Filled.Inventory2,
                )

                else -> ArchiveList(
                    archives = state.archives,
                    onExtract = viewModel::extract,
                )
            }

            // Transient completion / failure banner over the listing.
            if (state.phase == ArchiveOperationPhase.COMPLETED ||
                state.phase == ArchiveOperationPhase.FAILED
            ) {
                ResultBanner(
                    state = state,
                    onDismiss = viewModel::dismissResult,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

/**
 * Scrollable list of extractable archives. Each row shows the archive name, size and
 * detected format with an inline "Extract" action.
 */
@Composable
private fun ArchiveList(
    archives: List<FileItem>,
    onExtract: (FileItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(archives, key = { it.path }) { archive ->
            ArchiveRow(archive = archive, onExtract = { onExtract(archive) })
        }
    }
}

/** A single archive card with name, size, format badge and an extract button. */
@Composable
private fun ArchiveRow(
    archive: FileItem,
    onExtract: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExtract),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderZip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = archive.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val format = archiveFormatLabel(archive.name)
                    if (format != null) {
                        FormatBadge(label = format)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = formatBytes(archive.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onExtract) {
                Icon(
                    imageVector = Icons.Filled.Unarchive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Extract")
            }
        }
    }
}

/** A small pill showing the archive's detected format (e.g. "TAR.GZ", "7Z", "RAR"). */
@Composable
private fun FormatBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Full-screen progress panel shown while a create or extract operation runs. Renders
 * a determinate [LinearProgressIndicator] from the latest progress snapshot when the
 * total size is known, falling back to an indeterminate one otherwise.
 */
@Composable
private fun OperationProgressPanel(
    state: ArchiveManagerUiState,
    onCancel: () -> Unit,
) {
    val progress = state.progress
    val title = when (state.phase) {
        ArchiveOperationPhase.COMPRESSING -> "Creating archive…"
        ArchiveOperationPhase.EXTRACTING -> "Extracting archive…"
        else -> "Working…"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (state.phase == ArchiveOperationPhase.EXTRACTING) {
                Icons.Filled.Unarchive
            } else {
                Icons.Filled.FolderZip
            },
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        // Surface the archive currently being extracted (and its format) when known.
        val selected = state.selectedArchive
        if (state.phase == ArchiveOperationPhase.EXTRACTING && selected != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selected.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        val currentName = progress?.currentFileName.orEmpty()
        if (currentName.isNotBlank()) {
            Text(
                text = currentName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        val fraction = progress?.fraction ?: 0f
        if (progress != null && progress.totalBytes > 0L) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatBytes(progress.processedBytes) + " / " +
                    formatBytes(progress.totalBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

/** Bottom banner summarising a completed or failed operation. */
@Composable
private fun ResultBanner(
    state: ArchiveManagerUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val succeeded = state.phase == ArchiveOperationPhase.COMPLETED
    val container = if (succeeded) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = if (succeeded) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (succeeded) "Operation complete" else "Operation failed",
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                )
                val detail = if (succeeded) {
                    "Files are ready in this folder."
                } else {
                    state.error ?: "Something went wrong."
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    }
}

/**
 * Returns an upper-cased label for the archive format detected from [name]
 * (e.g. "TAR.GZ", "ZIP", "7Z", "RAR"), or null when no supported archive suffix is
 * recognised. Multi-part suffixes are matched before their single-part tails so a
 * `.tar.gz` is not mislabelled as `.gz`.
 */
private fun archiveFormatLabel(name: String): String? {
    val lower = name.lowercase(Locale.ROOT)
    val ext = ARCHIVE_FORMAT_SUFFIXES.firstOrNull { lower.endsWith(".$it") }
    return ext?.uppercase(Locale.ROOT)
}

/**
 * Recognised archive suffixes (lower-cased, without the leading dot), ordered so
 * multi-part suffixes precede their single-part tails for correct longest match.
 * Mirrors the formats dispatched by
 * [com.jupiter.filemanager.data.file.ArchiveManager.extractArchive].
 */
private val ARCHIVE_FORMAT_SUFFIXES: List<String> = listOf(
    "tar.gz", "tar.bz2",
    "zip", "jar", "apk", "aar", "war",
    "tgz", "tbz2", "tbz", "tar",
    "7z", "rar",
    "gz", "bz2",
)
