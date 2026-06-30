package com.jupiter.filemanager.feature.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SortField
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
import com.jupiter.filemanager.ui.components.iconForFile
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import java.io.File

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
 * In addition to the core listing the screen exposes three structural controls,
 * all driven from the ViewModel/UiState:
 *  - a **view-mode toggle** (list / grid) in the top bar and listing header
 *    ([FileBrowserViewModel.toggleViewMode]);
 *  - a horizontal **browser tab row** ([BrowserTabRow]) for switching/closing
 *    open directories and opening new tabs;
 *  - a collapsible **folder tree panel** ([FolderTreePanel]) that mirrors the
 *    path hierarchy of the current volume and lets the user jump to any ancestor
 *    or expandable child directory.
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

    // Handles used by the per-item actions (copy path to clipboard, open with an
    // external app). The clipboard manager and context are read here at the
    // composable scope; the coroutine scope drives the snackbar feedback.
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val onCopyPath: (FileItem) -> Unit = { item ->
        clipboardManager.setText(AnnotatedString(item.path))
        scope.launch { snackbarHostState.showSnackbar("Path copied") }
    }
    val onOpenWith: (FileItem) -> Unit = { item ->
        openWithExternalApp(context, item)
    }

    // Transient UI controllers (dialogs/sheets) kept local to the screen.
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }

    // A pending permanent delete awaiting user confirmation. Holds a short
    // human-readable summary of what would be deleted; the actual targets are the
    // current ViewModel selection, which is established before the dialog is shown.
    var deleteConfirmation by remember { mutableStateOf<DeleteConfirmation?>(null) }

    // Inline name search: toggled from the top bar, drives FilterOption.query.
    var searchActive by remember { mutableStateOf(false) }

    // Whether the directory listing renders as a grid is now ViewModel-owned so
    // it survives tab switches and configuration changes.
    val gridMode = uiState.viewMode == ViewMode.GRID

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

    // System back: close search, collapse tree, leave selection, else go up a
    // directory, else exit.
    BackHandler(enabled = true) {
        when {
            searchActive -> {
                searchActive = false
                if (uiState.filter.query.isNotEmpty()) {
                    viewModel.setFilter(uiState.filter.copy(query = ""))
                }
            }
            uiState.treeExpanded -> viewModel.toggleTree()
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
                    onDelete = {
                        // Confirm before the (permanent) delete; the selection is
                        // already established, so summarise it by count.
                        deleteConfirmation = DeleteConfirmation(
                            count = uiState.selectedPaths.size,
                        )
                    },
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
                    searchActive = searchActive,
                    searchQuery = uiState.filter.query,
                    gridMode = gridMode,
                    treeExpanded = uiState.treeExpanded,
                    onSearchQueryChange = {
                        viewModel.setFilter(uiState.filter.copy(query = it))
                    },
                    onSearchToggle = { active ->
                        searchActive = active
                        if (!active && uiState.filter.query.isNotEmpty()) {
                            viewModel.setFilter(uiState.filter.copy(query = ""))
                        }
                    },
                    onBack = {
                        if (uiState.canNavigateUp) viewModel.navigateUp() else onBack()
                    },
                    onSortFilter = { showSortSheet = true },
                    onToggleViewMode = { viewModel.toggleViewMode() },
                    onToggleTree = { viewModel.toggleTree() },
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
            // Horizontal strip of open browser tabs with select / close and an
            // add-tab affordance. Only shown outside of selection mode to keep
            // the contextual selection bar uncluttered.
            if (!uiState.selectionMode) {
                BrowserTabRow(
                    tabs = uiState.tabs,
                    activeTabIndex = uiState.activeTabIndex,
                    onSelectTab = { index -> viewModel.selectTab(index) },
                    onCloseTab = { index -> viewModel.closeTab(index) },
                    onAddTab = { viewModel.openTab(uiState.currentPath) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Breadcrumbs(
                crumbs = uiState.breadcrumbs,
                onCrumbClick = { crumb -> viewModel.openDirectory(crumb.path) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Always-visible quick type filter chips (All / Photos / Docs / …).
            FileTypeFilterRow(
                selected = uiState.filter.typeFilter,
                onSelect = { type ->
                    viewModel.setFilter(uiState.filter.copy(typeFilter = type))
                },
            )

            // Inline sort label + tree and list/grid toggles, directly above the listing.
            ListingHeaderRow(
                sortLabel = sortOptionLabel(uiState.sortOption),
                gridMode = gridMode,
                treeExpanded = uiState.treeExpanded,
                onSortClick = { showSortSheet = true },
                onToggleViewMode = { viewModel.toggleViewMode() },
                onToggleTree = { viewModel.toggleTree() },
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

            // The listing area, optionally fronted by a collapsible folder tree
            // panel that mirrors the current volume's path hierarchy.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                if (uiState.treeExpanded) {
                    FolderTreePanel(
                        breadcrumbs = uiState.breadcrumbs,
                        children = uiState.items,
                        currentPath = uiState.currentPath,
                        onNavigate = { path -> viewModel.openDirectory(path) },
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(220.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
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
                            val onItemClick: (FileItem) -> Unit = { item ->
                                if (uiState.selectionMode) {
                                    viewModel.toggleSelection(item)
                                } else if (item.isDirectory) {
                                    viewModel.openDirectory(item.path)
                                } else {
                                    onOpenFile(item)
                                }
                            }
                            val onItemLongClick: (FileItem) -> Unit = { item ->
                                if (uiState.selectionMode) {
                                    viewModel.toggleSelection(item)
                                } else {
                                    actionTarget = item
                                }
                            }
                            if (gridMode) {
                                FileGrid(
                                    items = uiState.items,
                                    selectedPaths = uiState.selectedPaths,
                                    onItemClick = onItemClick,
                                    onItemLongClick = onItemLongClick,
                                )
                            } else {
                                FileList(
                                    items = uiState.items,
                                    selectedPaths = uiState.selectedPaths,
                                    selectionMode = uiState.selectionMode,
                                    onItemClick = onItemClick,
                                    onItemLongClick = onItemLongClick,
                                )
                            }
                        }
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
                    onRequestDelete = { item ->
                        // Establish the selection first (the dialog confirms
                        // against it), then surface the confirmation for this item.
                        viewModel.enterSelection(item)
                        deleteConfirmation = DeleteConfirmation(
                            count = 1,
                            name = item.name,
                            fromSheet = true,
                        )
                    },
                    onRequestTransfer = { transfer -> pendingTransfer = transfer },
                    onCopyPath = onCopyPath,
                    onOpenWith = onOpenWith,
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
            onDismiss = {
                pendingTransfer = null
                // Sheet-initiated transfers entered selection mode up front; if the
                // chooser is cancelled, leave selection mode so the screen isn't stuck.
                if (transfer.fromSheet) {
                    viewModel.clearSelection()
                }
            },
        )
    }

    val pendingDelete = deleteConfirmation
    if (pendingDelete != null) {
        DeleteConfirmDialog(
            confirmation = pendingDelete,
            onConfirm = {
                deleteConfirmation = null
                viewModel.deleteSelected()
            },
            onDismiss = {
                // Sheet-initiated single-item delete entered selection mode up front;
                // if the user cancels, leave selection mode so the screen isn't stuck.
                if (pendingDelete.fromSheet) {
                    viewModel.clearSelection()
                }
                deleteConfirmation = null
            },
        )
    }
}

/**
 * Launches an external app via [Intent.ACTION_VIEW] for [item], exposing it
 * through a [FileProvider] content URI (authority = applicationId +
 * ".fileprovider"). The item's mime type is used (falling back to a wildcard),
 * read permission is granted to receiving apps, and the intent is wrapped in a
 * chooser. All failures are swallowed so the UI never crashes when no handler
 * exists or the path lies outside the configured FileProvider roots.
 */
private fun openWithExternalApp(context: Context, item: FileItem) {
    try {
        val authority = context.packageName + ".fileprovider"
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            authority,
            File(item.path),
        )
        val mime = item.mimeType ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: ActivityNotFoundException) {
        // No app can handle this file; nothing else we can do from here.
    } catch (_: IllegalArgumentException) {
        // FileProvider couldn't map the path (outside configured roots); ignore.
    }
}

/**
 * A permanent delete awaiting user confirmation. The actual items to delete are
 * the current ViewModel selection (established before the dialog is shown); this
 * only carries what is needed to phrase the confirmation prompt.
 *
 * @property count number of items that would be deleted.
 * @property name single item's name, when exactly one item is being deleted.
 */
private data class DeleteConfirmation(
    val count: Int,
    val name: String? = null,
    // true when raised from the per-item actions sheet, which enters selection mode
    // up front; cancelling must then leave selection mode so the screen isn't stuck.
    val fromSheet: Boolean = false,
)

/**
 * Confirmation dialog shown before a permanent delete, mirroring the
 * [RenameDialog] presentation. Deletes are irreversible, so the listing is only
 * removed when the user explicitly confirms here.
 */
@Composable
private fun DeleteConfirmDialog(
    confirmation: DeleteConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = if (confirmation.count == 1 && confirmation.name != null) {
        "Permanently delete \"" + confirmation.name + "\"? This cannot be undone."
    } else {
        "Permanently delete " + confirmation.count + " items? This cannot be undone."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete") },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

/** Whether a pending bulk transfer is a copy or a move. */
private enum class TransferMode { COPY, MOVE }

/**
 * A copy/move awaiting a user-chosen destination folder.
 *
 * @property fromSheet true when the transfer was started from the per-item
 *   actions sheet, which calls [FileBrowserViewModel.enterSelection] up front. In
 *   that case cancelling the destination chooser must clear the selection again
 *   so the screen does not get stuck in selection mode. Transfers started from
 *   the selection top bar (false) leave the existing selection untouched.
 */
private data class PendingTransfer(
    val mode: TransferMode,
    val startPath: String,
    val fromSheet: Boolean = false,
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
    onRequestDelete: (FileItem) -> Unit,
    onRequestTransfer: (PendingTransfer) -> Unit,
    onCopyPath: (FileItem) -> Unit,
    onOpenWith: (FileItem) -> Unit,
) {
    when (action) {
        FileAction.OPEN -> {
            if (item.isDirectory) viewModel.openDirectory(item.path) else onOpenFile(item)
        }
        FileAction.OPEN_WITH -> onOpenWith(item)
        FileAction.COPY_PATH -> onCopyPath(item)
        FileAction.RENAME -> onRequestRename(item)
        FileAction.DELETE -> onRequestDelete(item)
        FileAction.COPY -> {
            // Select the item, then ask the user to choose a destination folder.
            viewModel.enterSelection(item)
            onRequestTransfer(
                PendingTransfer(
                    mode = TransferMode.COPY,
                    startPath = item.parentPath ?: item.path,
                    fromSheet = true,
                ),
            )
        }
        FileAction.MOVE -> {
            viewModel.enterSelection(item)
            onRequestTransfer(
                PendingTransfer(
                    mode = TransferMode.MOVE,
                    startPath = item.parentPath ?: item.path,
                    fromSheet = true,
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

/**
 * Horizontal strip of open browser tabs. Each tab shows its folder title, can be
 * activated by tapping, and (when more than one tab is open) closed via its
 * trailing close affordance. A trailing add button opens a new tab at the
 * currently visible directory.
 */
@Composable
private fun BrowserTabRow(
    tabs: List<BrowserTab>,
    activeTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onAddTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                BrowserTabChip(
                    title = tab.title.ifBlank { "Files" },
                    active = index == activeTabIndex,
                    closable = tabs.size > 1,
                    onClick = { onSelectTab(index) },
                    onClose = { onCloseTab(index) },
                )
            }
            IconButton(onClick = onAddTab) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New tab",
                )
            }
        }
    }
}

/** A single pill-shaped tab inside [BrowserTabRow]. */
@Composable
private fun BrowserTabChip(
    title: String,
    active: Boolean,
    closable: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val background = if (active) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .background(color = background, shape = RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = if (closable) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        if (closable) {
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close tab",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Collapsible folder tree side panel. Renders the path hierarchy of the current
 * volume as an indented, expandable tree: every ancestor breadcrumb is shown as
 * an expanded node, and the immediate child folders of the current directory are
 * listed (collapsed) beneath it. Tapping any node calls [onNavigate] to descend
 * into that directory.
 */
@Composable
private fun FolderTreePanel(
    breadcrumbs: List<Breadcrumb>,
    children: List<FileItem>,
    currentPath: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val childFolders = remember(children) { children.filter { it.isDirectory } }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item(key = "__tree_header__") {
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                )
            }

            // Ancestor chain: each breadcrumb is an expanded tree node.
            itemsIndexed(breadcrumbs, key = { _, crumb -> "anc_" + crumb.path }) { depth, crumb ->
                FolderTreeNode(
                    label = crumb.name,
                    depth = depth,
                    expanded = true,
                    isCurrent = crumb.path.trimEnd('/') == currentPath.trimEnd('/'),
                    onClick = { onNavigate(crumb.path) },
                )
            }

            // Immediate child folders of the current directory, one level deeper.
            items(childFolders, key = { "child_" + it.path }) { folder ->
                FolderTreeNode(
                    label = folder.name,
                    depth = breadcrumbs.size,
                    expanded = false,
                    isCurrent = false,
                    onClick = { onNavigate(folder.path) },
                )
            }

            if (breadcrumbs.isEmpty() && childFolders.isEmpty()) {
                item(key = "__tree_empty__") {
                    Text(
                        text = "No folders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

/** A single indented row inside [FolderTreePanel]. */
@Composable
private fun FolderTreeNode(
    label: String,
    depth: Int,
    expanded: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val indent = (depth * 14).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp + indent, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Horizontally scrollable row of quick type-filter chips bound to
 * [FilterOption.typeFilter]. The leading "All" chip clears the filter (null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileTypeFilterRow(
    selected: FileType?,
    onSelect: (FileType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options: List<Pair<String, FileType?>> = listOf(
        "All" to null,
        "Photos" to FileType.IMAGE,
        "Docs" to FileType.DOCUMENT,
        "Videos" to FileType.VIDEO,
        "Audio" to FileType.AUDIO,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (label, type) ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(text = label) },
            )
        }
    }
}

/**
 * Thin header row above the listing: the active sort label (tap to open the sort
 * sheet) on the left and the tree + list/grid view toggles on the right.
 */
@Composable
private fun ListingHeaderRow(
    sortLabel: String,
    gridMode: Boolean,
    treeExpanded: Boolean,
    onSortClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleTree: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSortClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Sort,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = sortLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleTree) {
            Icon(
                imageVector = Icons.Filled.AccountTree,
                contentDescription = if (treeExpanded) "Hide folder tree" else "Show folder tree",
                tint = if (treeExpanded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onToggleViewMode) {
            Icon(
                imageVector = if (gridMode) {
                    Icons.AutoMirrored.Filled.ViewList
                } else {
                    Icons.Filled.GridView
                },
                contentDescription = if (gridMode) "Show as list" else "Show as grid",
            )
        }
    }
}

/** A short, human-readable description of the active [SortOption]. */
private fun sortOptionLabel(option: SortOption): String {
    val field = when (option.field) {
        SortField.NAME -> "Name"
        SortField.SIZE -> "Size"
        SortField.DATE_MODIFIED -> "Date"
        SortField.TYPE -> "Type"
    }
    return "Sorted by $field"
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

/**
 * Compact grid presentation of the directory contents: an adaptive grid of
 * icon + name cells. Pairs with the list/grid toggle in [ListingHeaderRow] and
 * the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileGrid(
    items: List<FileItem>,
    selectedPaths: Set<String>,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 4.dp,
            bottom = 96.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = items,
            key = { it.path },
        ) { item ->
            FileGridCell(
                item = item,
                selected = item.path in selectedPaths,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
            )
        }
    }
}

/** A single compact icon + name (+ size) cell used by [FileGrid]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCell(
    item: FileItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = background, shape = RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = iconForFile(item),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        // Folders show their item count (when known); files show a size label.
        val caption = if (item.isDirectory) {
            item.childCount?.let { count -> "$count items" }
        } else {
            com.jupiter.filemanager.core.util.formatBytes(item.sizeBytes)
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Standard browsing top app bar with back, search, sort/filter, view-mode/tree toggles and an overflow menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(
    title: String,
    searchActive: Boolean,
    searchQuery: String,
    gridMode: Boolean,
    treeExpanded: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSortFilter: () -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleTree: () -> Unit,
    overflowExpanded: Boolean,
    onOverflowToggle: (Boolean) -> Unit,
    onCreateFolder: () -> Unit,
) {
    TopAppBar(
        title = {
            if (searchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(text = "Search files…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            } else {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { if (searchActive) onSearchToggle(false) else onBack() }) {
                Icon(
                    imageVector = if (searchActive) {
                        Icons.Filled.Close
                    } else {
                        Icons.AutoMirrored.Filled.ArrowBack
                    },
                    contentDescription = if (searchActive) "Close search" else "Back",
                )
            }
        },
        actions = {
            if (!searchActive) {
                IconButton(onClick = { onSearchToggle(true) }) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                    )
                }
                IconButton(onClick = onToggleTree) {
                    Icon(
                        imageVector = Icons.Filled.AccountTree,
                        contentDescription = if (treeExpanded) "Hide folder tree" else "Show folder tree",
                        tint = if (treeExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                IconButton(onClick = onToggleViewMode) {
                    Icon(
                        imageVector = if (gridMode) {
                            Icons.AutoMirrored.Filled.ViewList
                        } else {
                            Icons.Filled.GridView
                        },
                        contentDescription = if (gridMode) "Show as list" else "Show as grid",
                    )
                }
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
