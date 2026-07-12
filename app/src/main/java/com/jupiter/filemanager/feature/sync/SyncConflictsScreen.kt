package com.jupiter.filemanager.feature.sync

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.jupiter.filemanager.domain.model.SyncConflict
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Sync Conflicts.
 *
 * Lists unresolved local-vs-remote conflicts sourced from the real
 * [com.jupiter.filemanager.domain.repository.SyncRepository]. Each conflict offers
 * a clear "Keep local" / "Keep remote" choice that delegates to the repository's
 * resolve operation.
 *
 * No live sync backend (cloud / NAS protocol I/O, conflict detection) is wired up
 * yet, so the repository starts empty and this screen renders an honest empty state
 * rather than fabricating conflicts.
 *
 * @param onBack pops the current screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConflictsScreen(
    onBack: () -> Unit,
    viewModel: SyncConflictsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "Sync Conflicts") },
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> LoadingView()

                uiState.isEmpty -> EmptyView(
                    title = "No sync conflicts",
                    message = "When the same file changes both on this device and " +
                        "on a connected cloud or NAS source, the conflicting " +
                        "versions will appear here so you can choose which one to " +
                        "keep. Connect a sync source to get started.",
                    icon = Icons.Filled.CloudSync,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = uiState.conflicts, key = { it.id }) { conflict ->
                        ConflictCard(
                            conflict = conflict,
                            isResolving = uiState.resolvingId == conflict.id,
                            anyResolving = uiState.resolvingId != null,
                            onKeepLocal = { viewModel.keepLocal(conflict.id) },
                            onKeepRemote = { viewModel.keepRemote(conflict.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A card describing a single [SyncConflict]: the affected path and source, a
 * side-by-side comparison of the local and remote versions, and the resolution
 * actions.
 */
@Composable
private fun ConflictCard(
    conflict: SyncConflict,
    isResolving: Boolean,
    anyResolving: Boolean,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JupiterCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(JupiterDesign.CompactPadding),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.SyncProblem,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName(conflict.path),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = conflict.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = conflict.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VersionColumn(
                    icon = Icons.Filled.PhoneAndroid,
                    title = "Local",
                    modified = conflict.localModified,
                    sizeBytes = conflict.localSizeBytes,
                    modifier = Modifier.weight(1f),
                )
                VersionColumn(
                    icon = Icons.Filled.CloudSync,
                    title = "Remote",
                    modified = conflict.remoteModified,
                    sizeBytes = conflict.remoteSizeBytes,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isResolving) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Resolving…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onKeepLocal,
                        enabled = !anyResolving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Keep local")
                    }
                    Button(
                        onClick = onKeepRemote,
                        enabled = !anyResolving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Keep remote")
                    }
                }
            }
        }
    }
}

/**
 * A compact column describing one side (local or remote) of a conflict: an icon,
 * a title, the last-modified time, and the file size.
 */
@Composable
private fun VersionColumn(
    icon: ImageVector,
    title: String,
    modified: Long,
    sizeBytes: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatRelativeTime(modified),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Extracts the trailing file name from a path for display in headers. */
private fun displayName(path: String): String {
    val trimmed = path.trimEnd('/')
    val slash = trimmed.lastIndexOf('/')
    return if (slash >= 0 && slash < trimmed.length - 1) {
        trimmed.substring(slash + 1)
    } else {
        trimmed.ifEmpty { path }
    }
}
