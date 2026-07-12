package com.jupiter.filemanager.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.JupiterIconBadge

/**
 * In-app destination chooser backed by FileRepository.listFiles. It only emits
 * navigation and confirmation intents; filesystem mutation remains in the VM.
 */
@Composable
fun CategoryFolderPickerDialog(
    state: CategoryFolderPickerState,
    selectedCount: Int,
    onOpenFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Move $selectedCount ${if (selectedCount == 1) "photo" else "photos"}")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pickerDisplayPath(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (state.canNavigateUp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateUp)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Parent folder",
                            )
                        }
                        Text("Parent folder", style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                }

                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                }

                state.error?.let { error ->
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }

                if (!state.isLoading && state.error == null && state.folders.isEmpty()) {
                    Text(
                        text = "No subfolders here. You can move the selection into this folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        items(
                            items = state.folders,
                            key = FileItem::path,
                        ) { folder ->
                            FolderDestinationRow(
                                folder = folder,
                                onClick = { onOpenFolder(folder.path) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.isLoading && state.error == null && selectedCount > 0,
            ) {
                Text("Move here")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FolderDestinationRow(
    folder: FileItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = Icons.Filled.Folder,
            size = 42.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open ${folder.name}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun pickerDisplayPath(state: CategoryFolderPickerState): String {
    if (PhotoMovePolicy.samePath(state.currentPath, state.rootPath)) return "Storage"
    return state.currentPath
        .replace('\\', '/')
        .removePrefix(state.rootPath.replace('\\', '/').trimEnd('/'))
        .trimStart('/')
        .takeIf(String::isNotBlank)
        ?: "Storage"
}
