package com.jupiter.filemanager.feature.trash

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.jupiter.filemanager.ui.components.JupiterFloatingBottomNavigation
import com.jupiter.filemanager.ui.components.JupiterMainTab
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
    onMainTabSelected: (JupiterMainTab) -> Unit = {},
    showBackButton: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEmptyConfirm by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var pendingPermanentDelete by remember { mutableStateOf<TrashItem?>(null) }

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
                title = {
                    Text(
                        text = "Recycle Bin",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showInfo = true },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "About Recycle Bin",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            JupiterFloatingBottomNavigation(
                selectedTab = JupiterMainTab.MORE,
                onTabSelected = onMainTabSelected,
            )
        },
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
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    item(key = "trash_list_controls") {
                        TrashListControls(
                            totalCount = uiState.visibleItems.size,
                            selectedSort = uiState.sort,
                            selectedFilter = uiState.filter,
                            showSortMenu = showSortMenu,
                            showFilterMenu = showFilterMenu,
                            onShowSortMenu = { showSortMenu = it },
                            onShowFilterMenu = { showFilterMenu = it },
                            onSortSelected = viewModel::setSort,
                            onFilterSelected = viewModel::setFilter,
                        )
                    }
                    if (uiState.visibleItems.isEmpty()) {
                        item(key = "trash_filter_empty") {
                            JupiterCard(contentPadding = PaddingValues(20.dp)) {
                                Text(
                                    text = "No ${uiState.filter.label.lowercase()} match this filter.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(items = uiState.visibleItems, key = { it.id }) { item ->
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

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("About Recycle Bin") },
            text = {
                Text(
                    if (uiState.autoDeleteDays > 0) {
                        "Items remain recoverable for ${uiState.autoDeleteDays} days, then Jupiscan permanently deletes them."
                    } else {
                        "Automatic deletion is OFF. Items remain recoverable until you permanently delete them."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Got it") }
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
                        "Auto-delete is off"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (autoDeleteDays > 0) {
                        "Items are permanently deleted after the retention period."
                    } else {
                        "Items stay here until you restore or permanently delete them."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrashSummaryAction(
                icon = Icons.Filled.Restore,
                title = "Restore all",
                subtitle = "$itemCount item" + if (itemCount == 1) "" else "s",
                tint = MaterialTheme.colorScheme.primary,
                enabled = enabled,
                onClick = onRestoreAll,
                modifier = Modifier.weight(1f),
            )
            TrashSummaryAction(
                icon = Icons.Filled.DeleteForever,
                title = "Empty bin",
                subtitle = "$itemCount items · ${formatBytes(totalBytes)}",
                tint = MaterialTheme.colorScheme.error,
                enabled = enabled,
                onClick = onEmpty,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrashSummaryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(icon = icon, tint = tint, contentDescription = null, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = tint)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrashListControls(
    totalCount: Int,
    selectedSort: TrashSort,
    selectedFilter: TrashFilter,
    showSortMenu: Boolean,
    showFilterMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    onShowFilterMenu: (Boolean) -> Unit,
    onSortSelected: (TrashSort) -> Unit,
    onFilterSelected: (TrashFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$totalCount item" + if (totalCount == 1) "" else "s",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            Row(
                modifier = Modifier
                    .clickable { onShowSortMenu(true) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedSort.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { onShowSortMenu(false) },
            ) {
                TrashSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label) },
                        onClick = {
                            onSortSelected(sort)
                            onShowSortMenu(false)
                        },
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { onShowFilterMenu(true) }) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = "Filter: ${selectedFilter.label}",
                    tint = if (selectedFilter == TrashFilter.ALL) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = { onShowFilterMenu(false) },
            ) {
                TrashFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.label) },
                        onClick = {
                            onFilterSelected(filter)
                            onShowFilterMenu(false)
                        },
                    )
                }
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
    var menuExpanded by remember { mutableStateOf(false) }
    val elapsedDays = ((System.currentTimeMillis() - item.deletedAt) / DAY_MILLIS).toInt()
    val remaining = (autoDeleteDays - elapsedDays).coerceAtLeast(0)

    JupiterCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = if (item.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                contentDescription = null,
                size = 52.dp,
            )
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
                    text = "Deleted ${formatRelativeTime(item.deletedAt)} · ${formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (autoDeleteDays > 0) {
                        if (remaining <= 0) "◷ Auto-deletes soon" else {
                            "◷ Auto-deletes in $remaining day" + if (remaining == 1) "" else "s"
                        }
                    } else {
                        "Auto-delete off · ${item.originalPath}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (autoDeleteDays > 0) {
                        JupiterDesign.CategoryArchive
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(
                    enabled = enabled,
                    onClick = { menuExpanded = true },
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Actions for ${item.name}")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Restore") },
                        leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRestore()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete forever", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeletePermanently()
                        },
                    )
                }
            }
        }
    }
}
