package com.jupiter.filemanager.feature.trash

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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.TrashItem
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Recycle Bin (Trash).
 *
 * Lists every file and folder currently held in the app-managed Recycle Bin,
 * sourced from the real [com.jupiter.filemanager.domain.repository.TrashRepository].
 * Each entry can be **Restored** back to its original location or **Deleted
 * permanently**. A top-bar action empties the entire bin behind a confirmation
 * dialog. When the bin is empty an honest empty state is shown.
 *
 * @param onBack pops the current screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEmptyConfirm by remember { mutableStateOf(false) }
    var pendingPermanentDelete by remember { mutableStateOf<TrashItem?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Recycle Bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::restoreAll,
                        enabled = !uiState.busy && uiState.items.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = "Restore all items",
                        )
                    }
                    IconButton(
                        onClick = { showEmptyConfirm = true },
                        enabled = !uiState.busy && uiState.items.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Empty Recycle Bin",
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
                    title = "Recycle Bin is empty",
                    message = "Files you delete are moved here so you can restore " +
                        "them if you change your mind. Nothing has been deleted yet.",
                    icon = Icons.Filled.DeleteOutline,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "trash_summary") {
                        TrashSummaryCard(
                            itemCount = uiState.items.size,
                            totalBytes = uiState.items.sumOf { it.sizeBytes },
                            autoDeleteDays = uiState.autoDeleteDays,
                            enabled = !uiState.busy,
                            onRestoreAll = viewModel::restoreAll,
                            onEmpty = { showEmptyConfirm = true },
                        )
                    }
                    items(items = uiState.items, key = { it.id }) { item ->
                        TrashItemCard(
                            item = item,
                            autoDeleteDays = uiState.autoDeleteDays,
                            enabled = !uiState.busy,
                            onRestore = { viewModel.restore(item.id) },
                            onDeletePermanently = { pendingPermanentDelete = item },
                        )
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                )
            },
            title = { Text(text = "Empty Recycle Bin?") },
            text = {
                Text(
                    text = "This permanently deletes all ${uiState.items.size} " +
                        "item(s) in the Recycle Bin. This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyConfirm = false
                        viewModel.emptyAll()
                    },
                ) {
                    Text(text = "Empty")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    pendingPermanentDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingPermanentDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                )
            },
            title = { Text(text = "Delete permanently?") },
            text = {
                Text(
                    text = "${item.name} will be permanently deleted from Recycle Bin. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPermanentDelete = null
                        viewModel.deletePermanently(item.id)
                    },
                ) {
                    Text(text = "Delete permanently", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermanentDelete = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

/** Live overview card — all values come from the actual Recycle Bin flow. */
@Composable
private fun TrashSummaryCard(
    itemCount: Int,
    totalBytes: Long,
    autoDeleteDays: Int,
    enabled: Boolean,
    onRestoreAll: () -> Unit,
    onEmpty: () -> Unit,
) {
    JupiterCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = Icons.Filled.DeleteOutline,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
                size = 64.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (autoDeleteDays > 0) {
                        "Auto-delete after $autoDeleteDays days"
                    } else {
                        "Recycle Bin"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$itemCount item" + if (itemCount == 1) "" else "s" +
                        " • ${formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRestoreAll,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restore all")
            }
            OutlinedButton(
                onClick = onEmpty,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Empty bin", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Milliseconds in a day, for the auto-delete countdown. */
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

/**
 * A card describing a single [TrashItem]: an icon, its name, size and when it was
 * deleted, its original location, and the Restore / Delete-permanently actions.
 */
@Composable
private fun TrashItemCard(
    item: TrashItem,
    autoDeleteDays: Int,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (item.isDirectory) {
                                Icons.Filled.Folder
                            } else {
                                Icons.Filled.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (!item.isDirectory) {
                                append(formatBytes(item.sizeBytes))
                                append(" · ")
                            }
                            append("Deleted ")
                            append(formatRelativeTime(item.deletedAt))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (autoDeleteDays > 0) {
                        val elapsedDays =
                            ((System.currentTimeMillis() - item.deletedAt) / DAY_MILLIS).toInt()
                        val remaining = (autoDeleteDays - elapsedDays).coerceAtLeast(0)
                        Text(
                            text = if (remaining <= 0) {
                                "Auto-deletes soon"
                            } else {
                                "Auto-deletes in $remaining day" + if (remaining == 1) "" else "s"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "From: ${item.originalPath}",
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
                OutlinedButton(
                    onClick = onRestore,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Restore")
                }
                OutlinedButton(
                    onClick = onDeletePermanently,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Delete")
                }
            }
        }
    }
}
