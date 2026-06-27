package com.jupiter.filemanager.feature.browser

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.feature.browser.components.Breadcrumbs
import com.jupiter.filemanager.feature.browser.components.CreateFolderDialog
import com.jupiter.filemanager.feature.browser.components.FileAction
import com.jupiter.filemanager.feature.browser.components.FileActionsSheet
import com.jupiter.filemanager.feature.browser.components.FileRow
import com.jupiter.filemanager.feature.browser.components.OperationProgressCard
import com.jupiter.filemanager.feature.browser.components.RenameDialog
import com.jupiter.filemanager.feature.browser.components.SortFilterSheet
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The file browser screen: lists the contents of a directory, supports
 * navigation, multi-select with bulk operations, sorting/filtering, folder
 * creation, and per-item actions (rename, delete, copy, move, bookmark, ...).
 *
 * The screen is a pure UI layer: all state comes from [FileBrowserViewModel] and
 * all side effects (file IO) are delegated to it. Per the shared contract the
 * screen decides, on row click, whether to descend into a directory or open a
 * file via [onOpenFile].
 *
 * @param initialPath the directory to open first; ignored here because the
 *   ViewModel already resolves it from its [androidx.lifecycle.SavedStateHandle].
 * @param onOpenFile invoked when a non-directory item is tapped.
 * @param onNavigateRoute invoked to navigate to another destination route.
 * @param onBack invoked when system back is pressed at the storage root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    initialPath: String?,
    onOpenFile: (FileItem) -> Unit,
    onNavigateRoute: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Transient UI controllers (dialogs/sheets) kept local to the screen.
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }

    // A pending copy/move that is waiting for the user to pick a destination
    // folder. The selection in [uiState] is left intact while the chooser is
    // open so the ViewModel can resolve the source items on confirm.
    var pendingTransfer by remember { mutableStateOf<PendingTransfer?>(null) }

    // Surface errors via the snackbar, then clear them so they don't repeat.
    LaunchedEffect(uiState.error) {
        val message = uiState.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    // System back: leave selection, else go up a directory, else exit the screen.
    BackHandler(enabled = true) {
        when {
            uiState.selectionMode -> viewModel.clearSelection()
            uiState.canNavigateUp -> viewModel.navigateUp()
            else -> onBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (uiState.selectionMode) {
                SelectionTopBar(
                    selectedCount = uiState.selectedPaths.size,
                    onClose = { viewModel.clearSelection() },
                    onDelete = { viewModel.deleteSelected() },
                    onCopy = {
                        // Ask the user where to copy to via a folder chooser; the
                        // selection is preserved until they confirm a destination.
                        pendingTransfer = PendingTransfer(
                            mode = TransferMode.COPY,
                            startPath = uiState.currentPath,
                        )
                    },
                    onMove = {
                        pendingTransfer = PendingTransfer(
                            mode = TransferMode.MOVE,
                            startPath = uiState.currentPath,
                        )
                    },
                )
            } else {
                BrowserTopBar(
                    title = currentFolderTitle(uiState.currentPath),
                    onBack = {
                        if (uiState.canNavigateUp) viewModel.navigateUp() else onBack()
                    },
                    onSortFilter = { showSortSheet = true },
                    overflowExpanded = showOverflowMenu,
                    onOverflowToggle = { showOverflowMenu = it },
                    onCreateFolder = {
                        showOverflowMenu = false
                        showCreateFolderDialog = true
                    },
                )
            }
        },
        floatingActionButton = {
            if (!uiState.selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = null,
                        )
                    },
                    text = { Text(text = "New folder") },
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Breadcrumbs(
                crumbs = uiState.breadcrumbs,
                onCrumbClick = { crumb -> viewModel.openDirectory(crumb.path) },
                modifier = Modifier.fillMaxWidth(),
            )

            val operation = uiState.operation
            if (operation != null) {
                OperationProgressCard(
                    progress = operation,
                    onCancel = { viewModel.cancelOperation() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    uiState.isLoading && uiState.items.isEmpty() -> {
                        LoadingView()
                    }

                    uiState.error != null && uiState.items.isEmpty() -> {
                        ErrorView(
                            message = uiState.error ?: "Something went wrong.",
                            onRetry = { viewModel.refresh() },
                        )
                    }

                    uiState.items.isEmpty() -> {
                        EmptyView(
                            title = "Empty folder",
                            message = "There are no files to show here.",
                            icon = Icons.Filled.FolderOpen,
                        )
                    }

                    else -> {
                        FileList(
                            items = uiState.items,
                            selectedPaths = uiState.selectedPaths,
                            selectionMode = uiState.selectionMode,
                            onItemClick = { item ->
                                if (uiState.selectionMode) {
                                    viewModel.toggleSelection(item)
                                } else if (item.isDirectory) {
                                    viewModel.openDirectory(item.path)
                                } else {
                                    onOpenFile(item)
                                }
                            },
                            onItemLongClick = { item ->
                                if (uiState.selectionMode) {
                                    viewModel.toggleSelection(item)
                                } else {
                                    actionTarget = item
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // ---- dialogs & bottom sheets ----------------------------------------

    if (showSortSheet) {
        SortFilterSheet(
            current = uiState.sortOption,
            filter = uiState.filter,
            onApply = { sort, filter ->
                viewModel.setSort(sort)
                viewModel.setFilter(filter)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false },
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false },
        )
    }

    val sheetTarget = actionTarget
    if (sheetTarget != null) {
        FileActionsSheet(
            item = sheetTarget,
            onAction = { action ->
                actionTarget = null
                handleFileAction(
                    action = action,
                    item = sheetTarget,
                    viewModel = viewModel,
                    onOpenFile = onOpenFile,
                    onRequestRename = { renameTarget = it },
                    onRequestTransfer = { transfer -> pendingTransfer = transfer },
                )
            },
            onDismiss = { actionTarget = null },
        )
    }

    val renaming = renameTarget
    if (renaming != null) {
        RenameDialog(
            initialName = renaming.name,
            onConfirm = { newName ->
                viewModel.rename(renaming, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    val transfer = pendingTransfer
    if (transfer != null) {
        FolderChooserDialog(
            title = when (transfer.mode) {
                TransferMode.COPY -> "Copy to…"
                TransferMode.MOVE -> "Move to…"
            },
            startPath = transfer.startPath,
            onConfirm = { destinationPath ->
                pendingTransfer = null
                when (transfer.mode) {
                    TransferMode.COPY -> viewModel.copySelectedTo(destinationPath)
                    TransferMode.MOVE -> viewModel.moveSelectedTo(destinationPath)
                }
            },
            onDismiss = { pendingTransfer = null },
        )
    }
}

/** Whether a pending bulk transfer is a copy or a move. */
private enum class TransferMode { COPY, MOVE }

/** A copy/move awaiting a user-chosen destination folder. */
private data class PendingTransfer(
    val mode: TransferMode,
    val startPath: String,
)

/**
 * Routes a [FileAction] chosen from the per-item actions sheet to the
 * appropriate ViewModel call or screen-local UI state change.
 */
private fun handleFileAction(
    action: FileAction,
    item: FileItem,
    viewModel: FileBrowserViewModel,
    onOpenFile: (FileItem) -> Unit,
    onRequestRename: (FileItem) -> Unit,
    onRequestTransfer: (PendingTransfer) -> Unit,
) {
    when (action) {
        FileAction.OPEN -> {
            if (item.isDirectory) viewModel.openDirectory(item.path) else onOpenFile(item)
        }
        FileAction.RENAME -> onRequestRename(item)
        FileAction.DELETE -> {
            viewModel.enterSelection(item)
            viewModel.deleteSelected()
        }
        FileAction.COPY -> {
            // Select the item, then ask the user to choose a destination folder.
            viewModel.enterSelection(item)
            onRequestTransfer(
                PendingTransfer(
                    mode = TransferMode.COPY,
                    startPath = item.parentPath ?: item.path,
                ),
            )
        }
        FileAction.MOVE -> {
            viewModel.enterSelection(item)
            onRequestTransfer(
                PendingTransfer(
                    mode = TransferMode.MOVE,
                    startPath = item.parentPath ?: item.path,
                ),
            )
        }
        FileAction.ADD_BOOKMARK -> viewModel.addBookmark(item)
        // SHARE, COMPRESS and DETAILS are handled by other layers / not yet wired.
        FileAction.SHARE,
        FileAction.COMPRESS,
        FileAction.DETAILS -> Unit
    }
}

/** Scrollable list of [FileRow]s for the current directory contents. */
@Composable
private fun FileList(
    items: List<FileItem>,
    selectedPaths: Set<String>,
    selectionMode: Boolean,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(
            items = items,
            key = { it.path },
        ) { item ->
            FileRow(
                item = item,
                selected = item.path in selectedPaths,
                selectionMode = selectionMode,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Standard browsing top app bar with back, sort/filter and an overflow menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(
    title: String,
    onBack: () -> Unit,
    onSortFilter: () -> Unit,
    overflowExpanded: Boolean,
    onOverflowToggle: (Boolean) -> Unit,
    onCreateFolder: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
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
            IconButton(onClick = onSortFilter) {
                Icon(
                    imageVector = Icons.Filled.Sort,
                    contentDescription = "Sort and filter",
                )
            }
            IconButton(onClick = { onOverflowToggle(true) }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                )
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { onOverflowToggle(false) },
            ) {
                DropdownMenuItem(
                    text = { Text(text = "New folder") },
                    onClick = onCreateFolder,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                        )
                    },
                )
            }
        },
    )
}

/** Contextual top app bar shown while one or more items are selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        title = {
            Text(
                text = "$selectedCount selected",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear selection",
                )
            }
        },
        actions = {
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                )
            }
            IconButton(onClick = onMove) {
                Icon(
                    imageVector = Icons.Filled.DriveFileMove,
                    contentDescription = "Move",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                )
            }
        },
    )
}

/**
 * Hilt entry point used to obtain the [FileRepository] from a Composable, where
 * constructor injection is unavailable. This lets the folder chooser list
 * directories without disturbing the browser's own listing state.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
private interface FileBrowserScreenEntryPoint {
    fun fileRepository(): FileRepository
}

private fun fileRepositoryOf(context: Context): FileRepository =
    EntryPointAccessors
        .fromApplication(context.applicationContext, FileBrowserScreenEntryPoint::class.java)
        .fileRepository()

/**
 * A self-contained folder chooser: starting at [startPath] the user can drill
 * into subfolders, go up to the parent, and confirm the currently displayed
 * directory as the destination via [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderChooserDialog(
    title: String,
    startPath: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { fileRepositoryOf(context) }
    val rootDirectory = remember(repository) { repository.rootDirectory() }

    var currentPath by remember { mutableStateOf(startPath) }
    var folders by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        loading = true
        loadError = null
        when (
            val result = repository.listFiles(
                currentPath,
                SortOption(),
                FilterOption(),
            )
        ) {
            is AppResult.Success -> {
                folders = result.data.filter { it.isDirectory }
                loadError = null
            }
            is AppResult.Failure -> {
                folders = emptyList()
                loadError = result.error.displayMessage
            }
        }
        loading = false
    }

    val canGoUp = currentPath.trimEnd('/') != rootDirectory.trimEnd('/') &&
        parentDirectoryOf(currentPath) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = currentFolderTitle(currentPath),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )

                if (canGoUp) {
                    FolderChooserRow(
                        label = "..",
                        icon = Icons.Filled.ArrowUpward,
                        onClick = {
                            parentDirectoryOf(currentPath)?.let { currentPath = it }
                        },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> Text(text = "Loading…")
                        loadError != null -> Text(text = loadError ?: "Unable to open folder.")
                        folders.isEmpty() -> Text(text = "No subfolders here.")
                        else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(items = folders, key = { it.path }) { folder ->
                                FolderChooserRow(
                                    label = folder.name,
                                    icon = Icons.Filled.Folder,
                                    onClick = { currentPath = folder.path },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentPath) }) {
                Text(text = "Select this folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

/** A single tappable row inside the folder chooser. */
@Composable
private fun FolderChooserRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Returns the parent directory of [path], or null when there is no meaningful
 * parent (root or blank).
 */
private fun parentDirectoryOf(path: String): String? {
    val normalized = path.trimEnd('/')
    if (normalized.isEmpty() || normalized == "/") return null
    val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return parent.ifEmpty { "/" }
}

/**
 * Derives a human-friendly title from [path]: the last path segment, or "/" for
 * the storage root and an empty string.
 */
private fun currentFolderTitle(path: String): String {
    val normalized = path.trimEnd('/')
    if (normalized.isEmpty()) return "/"
    return normalized.substringAfterLast('/').ifEmpty { "/" }
}
