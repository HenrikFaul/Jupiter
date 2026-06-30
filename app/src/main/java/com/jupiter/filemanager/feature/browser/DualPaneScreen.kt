package com.jupiter.filemanager.feature.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.feature.browser.components.Breadcrumbs
import com.jupiter.filemanager.feature.browser.components.FileRow
import com.jupiter.filemanager.feature.browser.components.OperationProgressCard
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView

/** Identifies which of the two panes is currently the active (transfer source) pane. */
private enum class Pane { LEFT, RIGHT }

/**
 * A two-pane file browser. Each pane is an independent column with its own
 * breadcrumb trail and file listing, backed by its own
 * [FileBrowserViewModel] instance (keyed so the two are distinct).
 *
 * The two panes are wired for cross-pane transfers: long-pressing an item enters
 * selection mode in that pane and makes it the active (source) pane (shown with
 * an accent border). An action bar then offers Copy / Move to the *other* pane,
 * Select all, and Clear. On completion the destination pane is refreshed so the
 * transferred items appear immediately. Toolbar actions let you Swap the two
 * panes or Equalize them (point the inactive pane at the active pane's folder).
 *
 * Pure UI: all IO is delegated to the per-pane ViewModels via their existing
 * public functions only.
 *
 * @param onOpenFile invoked when a (non-directory) file is tapped in either pane.
 * @param onBack invoked when the top bar's back affordance is pressed.
 */
@Composable
fun DualPaneScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
) {
    val leftViewModel: FileBrowserViewModel = hiltViewModel(key = "paneLeft")
    val rightViewModel: FileBrowserViewModel = hiltViewModel(key = "paneRight")

    val leftState by leftViewModel.uiState.collectAsStateWithLifecycle()
    val rightState by rightViewModel.uiState.collectAsStateWithLifecycle()

    var activePane by remember { mutableStateOf(Pane.LEFT) }

    val activeViewModel = if (activePane == Pane.LEFT) leftViewModel else rightViewModel
    val activeState = if (activePane == Pane.LEFT) leftState else rightState
    val otherViewModel = if (activePane == Pane.LEFT) rightViewModel else leftViewModel
    val otherState = if (activePane == Pane.LEFT) rightState else leftState

    // When the active pane's operation reaches a terminal state, refresh the
    // destination pane (the source pane reloads itself in the ViewModel). Keyed
    // on the operation's running flag so it fires once per completed transfer.
    val operationRunning = activeState.operation?.state == OperationState.RUNNING
    LaunchedEffect(activePane, operationRunning) {
        val op = activeState.operation
        if (op != null && op.state == OperationState.COMPLETED) {
            otherViewModel.refresh()
        }
    }

    val showActionBar = activeState.selectionMode

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual pane") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    // Swap: each pane navigates to the other's current folder.
                    IconButton(
                        onClick = {
                            val leftPath = leftState.currentPath
                            val rightPath = rightState.currentPath
                            leftViewModel.openDirectory(rightPath)
                            rightViewModel.openDirectory(leftPath)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "Swap panes",
                        )
                    }
                    // Equalize: point the inactive pane at the active pane's folder.
                    IconButton(
                        onClick = {
                            otherViewModel.openDirectory(activeState.currentPath)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Sync panes",
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (showActionBar) {
                ActionBar(
                    onCopy = { activeViewModel.copySelectedTo(otherState.currentPath) },
                    onMove = { activeViewModel.moveSelectedTo(otherState.currentPath) },
                    onSelectAll = {
                        activeState.items.forEach { item ->
                            if (!activeState.selectedPaths.contains(item.path)) {
                                activeViewModel.toggleSelection(item)
                            }
                        }
                    },
                    onClear = { activeViewModel.clearSelection() },
                    selectedCount = activeState.selectedPaths.size,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Surface the active pane's in-flight / just-finished operation above the panes.
            activeState.operation?.let { op ->
                OperationProgressCard(
                    progress = op,
                    onCancel = { activeViewModel.cancelOperation() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Pane(
                    state = leftState,
                    isActive = activePane == Pane.LEFT,
                    onCrumbPath = { path ->
                        activePane = Pane.LEFT
                        leftViewModel.openDirectory(path)
                    },
                    onItemClick = { item ->
                        activePane = Pane.LEFT
                        when {
                            leftState.selectionMode -> leftViewModel.toggleSelection(item)
                            item.isDirectory -> leftViewModel.openDirectory(item.path)
                            else -> onOpenFile(item)
                        }
                    },
                    onItemLongClick = { item ->
                        activePane = Pane.LEFT
                        leftViewModel.enterSelection(item)
                    },
                    onRetry = leftViewModel::refresh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )

                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Pane(
                    state = rightState,
                    isActive = activePane == Pane.RIGHT,
                    onCrumbPath = { path ->
                        activePane = Pane.RIGHT
                        rightViewModel.openDirectory(path)
                    },
                    onItemClick = { item ->
                        activePane = Pane.RIGHT
                        when {
                            rightState.selectionMode -> rightViewModel.toggleSelection(item)
                            item.isDirectory -> rightViewModel.openDirectory(item.path)
                            else -> onOpenFile(item)
                        }
                    },
                    onItemLongClick = { item ->
                        activePane = Pane.RIGHT
                        rightViewModel.enterSelection(item)
                    },
                    onRetry = rightViewModel::refresh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * The cross-pane transfer action bar, shown while the active pane is in
 * selection mode. Copy / Move target the *other* pane's current folder.
 */
@Composable
private fun ActionBar(
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    selectedCount: Int,
) {
    BottomAppBar {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val enabled = selectedCount > 0
            ActionBarButton(
                icon = Icons.Filled.ContentCopy,
                label = "Copy",
                enabled = enabled,
                onClick = onCopy,
            )
            ActionBarButton(
                icon = Icons.Filled.DriveFileMove,
                label = "Move",
                enabled = enabled,
                onClick = onMove,
            )
            ActionBarButton(
                icon = Icons.Filled.SelectAll,
                label = "Select all",
                enabled = true,
                onClick = onSelectAll,
            )
            ActionBarButton(
                icon = Icons.Filled.Clear,
                label = "Clear",
                enabled = true,
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun ActionBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = label)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * One side of the dual-pane layout: a breadcrumb trail above an independent file
 * listing. Renders honest loading / error / empty states from [state]. When
 * [isActive] it is wrapped in an accent border so the transfer source is obvious.
 */
@Composable
private fun Pane(
    state: FileBrowserUiState,
    isActive: Boolean,
    onCrumbPath: (String) -> Unit,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Column(
        modifier = modifier.border(BorderStroke(2.dp, borderColor)),
    ) {
        Breadcrumbs(
            crumbs = state.breadcrumbs,
            onCrumbClick = { onCrumbPath(it.path) },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.items.isEmpty() -> {
                    LoadingView(modifier = Modifier.fillMaxSize())
                }

                state.error != null && state.items.isEmpty() -> {
                    ErrorView(
                        message = state.error,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.items.isEmpty() -> {
                    EmptyView(
                        title = "Empty folder",
                        message = "This folder has no items.",
                        icon = Icons.Outlined.FolderOff,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(items = state.items, key = { it.path }) { item ->
                            FileRow(
                                item = item,
                                selected = state.selectedPaths.contains(item.path),
                                selectionMode = state.selectionMode,
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) },
                                dense = true,
                            )
                        }
                    }
                }
            }
        }
    }
}
