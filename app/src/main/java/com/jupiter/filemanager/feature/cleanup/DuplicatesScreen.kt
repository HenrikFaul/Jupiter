package com.jupiter.filemanager.feature.cleanup

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.iconForFile
import kotlinx.coroutines.launch

/**
 * Lists duplicate file groups detected on the primary storage volume. Each group
 * shows its files, the user can select redundant copies to delete, and a
 * confirmation dialog guards the destructive action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    onBack: () -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showConfirm by remember { mutableStateOf(false) }

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
                title = { Text("Duplicate Files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.selectAllExtras() }) {
                        Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = "Select all extra copies",
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
                        items(state.groups, key = { it.hash }) { group ->
                            DuplicateGroupCard(
                                group = group,
                                selectedPaths = state.selectedPaths,
                                onToggle = viewModel::toggleSelection,
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

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selectedPaths: Set<String>,
    onToggle: (String) -> Unit,
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
            group.files.forEachIndexed { index, file ->
                DuplicateFileRow(
                    file = file,
                    isKept = index == 0,
                    isSelected = file.path in selectedPaths,
                    onToggle = { onToggle(file.path) },
                )
                if (index != group.files.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
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
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = iconForFile(file),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            Text(
                text = file.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
        )
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
