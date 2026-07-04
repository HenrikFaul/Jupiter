package com.jupiter.filemanager.feature.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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

/** Whether a drop performs a copy or a move. Copy is the default. */
private enum class DropMode { COPY, MOVE }

/**
 * Live state for an in-progress cross-pane drag. Held at the screen level so both
 * panes and the floating ghost overlay observe the same values.
 *
 * @property source the pane the drag originated from (the transfer source).
 * @property items the item(s) being dragged (a single long-pressed item, or the
 *   source pane's whole selection when it was already in selection mode).
 * @property pointer the current pointer location in WINDOW coordinates.
 */
private class DragState {
    var source by mutableStateOf<Pane?>(null)
    var items by mutableStateOf<List<FileItem>>(emptyList())
    var pointer by mutableStateOf(Offset.Zero)

    /**
     * The path of the FOLDER currently under the pointer in the *other* (non-source)
     * pane, or null when the pointer is over that pane's background, over the source
     * pane, or off both panes. Recomputed on every drag move so the row under the
     * finger is highlighted live. Compared against [FileItem.path] by folder rows.
     */
    var hoverTarget by mutableStateOf<String?>(null)

    val active: Boolean get() = source != null && items.isNotEmpty()

    fun clear() {
        source = null
        items = emptyList()
        pointer = Offset.Zero
        hoverTarget = null
    }
}

/**
 * Per-pane geometry captured via [onGloballyPositioned], in WINDOW coordinates, so
 * a drop can be hit-tested against a pane and its individual folder rows.
 *
 * @property bounds the whole pane's window bounds (used as the fallback drop target
 *   → the pane's current folder).
 * @property folderBounds window bounds of each currently visible FOLDER row, keyed
 *   by folder path (used to target a drop directly into a sub-folder).
 */
private class PaneGeometry {
    var bounds by mutableStateOf(Rect.Zero)
    val folderBounds: SnapshotStateMap<String, Rect> = mutableStateMapOf()
}

/**
 * A two-pane file browser. Each pane is an independent column with its own
 * breadcrumb trail and file listing, backed by its own
 * [FileBrowserViewModel] instance (keyed so the two are distinct).
 *
 * The two panes are wired for cross-pane transfers in two complementary ways:
 *
 *  - **Drag and drop.** Long-press a row and drag it. A floating ghost follows the
 *    pointer. Releasing over the *other* pane transfers there: onto a folder row →
 *    into that folder, otherwise → into the other pane's current folder. Releasing
 *    over the *same* pane cancels. Whether the drop copies or moves is governed by
 *    the "Drop" toggle (Copy by default). If the source pane is already in selection
 *    mode the whole selection is transferred; otherwise just the dragged item.
 *  - **Action bar.** Long-pressing enters selection mode and makes that pane the
 *    active (source) pane (accent border). The bottom action bar then offers
 *    Copy / Move to the *other* pane, Select all, and Clear.
 *
 * A **Compare** toolbar toggle tints rows whose name also exists in the other pane,
 * so same-named files line up at a glance. Toolbar actions also let you Swap the two
 * panes or Equalize them (point the inactive pane at the active pane's folder).
 *
 * Pure UI: all IO is delegated to the per-pane ViewModels via their existing
 * public functions only.
 *
 * @param onOpenFile invoked when a (non-directory) file is tapped in either pane.
 * @param onBack invoked when the top bar's back affordance is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var dropMode by remember { mutableStateOf(DropMode.COPY) }
    var compareMode by remember { mutableStateOf(false) }

    val activeViewModel = if (activePane == Pane.LEFT) leftViewModel else rightViewModel
    val activeState = if (activePane == Pane.LEFT) leftState else rightState
    val otherViewModel = if (activePane == Pane.LEFT) rightViewModel else leftViewModel
    val otherState = if (activePane == Pane.LEFT) rightState else leftState

    // Shared drag + geometry state for the manual pointerInput drag-and-drop.
    val drag = remember { DragState() }
    val leftGeom = remember { PaneGeometry() }
    val rightGeom = remember { PaneGeometry() }
    // Window origin of the overlay Box, so window-space pointer coords map to local
    // coords for the drag ghost's graphicsLayer translation.
    val boxWindowOrigin = remember { mutableStateOf(Offset.Zero) }

    // Names present in BOTH panes — cheap in-memory match by name for Compare mode.
    val sharedNames = remember(compareMode, leftState.items, rightState.items) {
        if (!compareMode) emptySet()
        else {
            val leftNames = leftState.items.mapTo(HashSet()) { it.name }
            rightState.items.mapNotNullTo(HashSet()) { if (leftNames.contains(it.name)) it.name else null }
        }
    }

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

    // Both panes may run transfers (drag can originate from either pane, regardless
    // of which is "active"). Refresh the opposite pane whenever either completes so
    // dropped items appear immediately.
    val leftOpRunning = leftState.operation?.state == OperationState.RUNNING
    LaunchedEffect(leftOpRunning) {
        if (leftState.operation?.state == OperationState.COMPLETED) rightViewModel.refresh()
    }
    val rightOpRunning = rightState.operation?.state == OperationState.RUNNING
    LaunchedEffect(rightOpRunning) {
        if (rightState.operation?.state == OperationState.COMPLETED) leftViewModel.refresh()
    }

    val showActionBar = activeState.selectionMode

    // Hit-tests a WINDOW-space [pointer] against the OTHER (non-source) pane and
    // returns the FOLDER path directly under it, or null when the pointer is over
    // that pane's background, over the source pane, or off both panes. Drives both
    // the live drop-target highlight (via drag.hoverTarget) and the drop resolution.
    fun computeDropFolder(pointer: Offset): String? {
        val source = drag.source ?: return null
        val target = if (source == Pane.LEFT) Pane.RIGHT else Pane.LEFT
        val targetGeom = if (target == Pane.LEFT) leftGeom else rightGeom
        if (!targetGeom.bounds.contains(pointer)) return null
        return targetGeom.folderBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
    }

    // Resolves a released pointer position into a transfer, honouring [dropMode].
    // Runs the transfer on the SOURCE pane's ViewModel into the hit-tested target.
    fun handleDrop() {
        val source = drag.source
        val dragged = drag.items
        if (source == null || dragged.isEmpty()) {
            drag.clear()
            return
        }
        val target = if (source == Pane.LEFT) Pane.RIGHT else Pane.LEFT
        val targetGeom = if (target == Pane.LEFT) leftGeom else rightGeom
        val targetVm = if (target == Pane.LEFT) leftViewModel else rightViewModel
        val targetState = if (target == Pane.LEFT) leftState else rightState
        val sourceVm = if (source == Pane.LEFT) leftViewModel else rightViewModel
        val sourceState = if (source == Pane.LEFT) leftState else rightState
        val p = drag.pointer

        // Must be released over the OTHER pane; same-pane (or nowhere) → no-op.
        if (!targetGeom.bounds.contains(p)) {
            drag.clear()
            return
        }

        // Prefer a specific folder row under the pointer; else the pane's folder.
        val folderHit = computeDropFolder(p)
        val destination = folderHit ?: targetState.currentPath

        // Never transfer a folder into itself.
        if (dragged.any { it.path == destination }) {
            drag.clear()
            return
        }

        // Ensure the source ViewModel's selection is exactly the dragged item(s)
        // synchronously (StateFlow.value updates immediately) before the transfer,
        // since copySelectedTo/moveSelectedTo read the current selection.
        val alreadySelected = sourceState.selectionMode &&
            sourceState.selectedPaths.isNotEmpty() &&
            dragged.all { sourceState.selectedPaths.contains(it.path) }
        if (!alreadySelected) {
            sourceVm.enterSelection(dragged.first())
            dragged.drop(1).forEach { sourceVm.toggleSelection(it) }
        }

        when (dropMode) {
            DropMode.COPY -> sourceVm.copySelectedTo(destination)
            DropMode.MOVE -> sourceVm.moveSelectedTo(destination)
        }
        sourceVm.clearSelection()
        drag.clear()
    }

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
                    // Compare: tint rows whose name exists in the other pane.
                    IconButton(onClick = { compareMode = !compareMode }) {
                        Icon(
                            imageVector = Icons.Filled.CompareArrows,
                            contentDescription = if (compareMode) "Compare on" else "Compare off",
                            tint = if (compareMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
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
                    dropMode = dropMode,
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
        // Top-level Box so the floating drag ghost can be drawn over both panes.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned {
                    val b = it.boundsInWindow()
                    boxWindowOrigin.value = Offset(b.left, b.top)
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drop-mode toggle: governs both drag-drops and the action-bar default.
                DropModeBar(
                    dropMode = dropMode,
                    onDropModeChange = { dropMode = it },
                )

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
                        geometry = leftGeom,
                        drag = drag,
                        pane = Pane.LEFT,
                        sharedNames = sharedNames,
                        onDrop = { handleDrop() },
                        computeDropFolder = { computeDropFolder(it) },
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
                        geometry = rightGeom,
                        drag = drag,
                        pane = Pane.RIGHT,
                        sharedNames = sharedNames,
                        onDrop = { handleDrop() },
                        computeDropFolder = { computeDropFolder(it) },
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

            // Floating drag ghost, drawn last so it sits above both panes, offset
            // to the pointer's window position (minus the top-level Box's own window
            // origin so window coords map back to local coords).
            if (drag.active) {
                DragGhost(
                    label = drag.items.firstOrNull()?.name ?: "",
                    count = drag.items.size,
                    dropMode = dropMode,
                    pointer = drag.pointer,
                    boxOrigin = boxWindowOrigin.value,
                )
            }
        }
    }
}

/**
 * The Drop-mode toggle row: chooses whether a drop (drag-and-drop or the action
 * bar's default highlight) performs a Copy or a Move. Copy is the default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropModeBar(
    dropMode: DropMode,
    onDropModeChange: (DropMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Drop:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            SegmentedButton(
                selected = dropMode == DropMode.COPY,
                onClick = { onDropModeChange(DropMode.COPY) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            ) {
                Text("Copy")
            }
            SegmentedButton(
                selected = dropMode == DropMode.MOVE,
                onClick = { onDropModeChange(DropMode.MOVE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
            ) {
                Text("Move")
            }
        }
    }
}

/**
 * The floating drag ghost that follows the pointer during a drag. Shows the dragged
 * item's name and, when several items are dragged, a count badge. [pointer] and
 * [boxOrigin] are both in window coordinates; the ghost is offset by their delta so
 * it lands under the finger within the overlay Box.
 */
@Composable
private fun DragGhost(
    label: String,
    count: Int,
    dropMode: DropMode,
    pointer: Offset,
    boxOrigin: Offset,
) {
    val local = pointer - boxOrigin
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.graphicsLayer {
            // Offset so the ghost trails just below-right of the finger.
            translationX = local.x + 24f
            translationY = local.y + 24f
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (dropMode == DropMode.MOVE) {
                    Icons.Filled.DriveFileMove
                } else {
                    Icons.Filled.ContentCopy
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (count > 1) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * The cross-pane transfer action bar, shown while the active pane is in
 * selection mode. Copy / Move target the *other* pane's current folder. The
 * button matching the current [dropMode] is emphasised so the bar and the
 * drag-and-drop default stay in sync.
 */
@Composable
private fun ActionBar(
    dropMode: DropMode,
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
                emphasised = dropMode == DropMode.COPY,
                onClick = onCopy,
            )
            ActionBarButton(
                icon = Icons.Filled.DriveFileMove,
                label = "Move",
                enabled = enabled,
                emphasised = dropMode == DropMode.MOVE,
                onClick = onMove,
            )
            ActionBarButton(
                icon = Icons.Filled.SelectAll,
                label = "Select all",
                enabled = true,
                emphasised = false,
                onClick = onSelectAll,
            )
            ActionBarButton(
                icon = Icons.Filled.Clear,
                label = "Clear",
                enabled = true,
                emphasised = false,
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
    emphasised: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (emphasised) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = label, tint = contentColor)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

/**
 * One side of the dual-pane layout: a breadcrumb trail above an independent file
 * listing. Renders honest loading / error / empty states from [state]. When
 * [isActive] it is wrapped in an accent border so the transfer source is obvious.
 *
 * Each row is draggable via a long-press drag gesture that feeds the shared [drag]
 * state; folder rows additionally report their window bounds into [geometry] so a
 * drop can be hit-tested against them.
 */
@Composable
private fun Pane(
    state: FileBrowserUiState,
    isActive: Boolean,
    geometry: PaneGeometry,
    drag: DragState,
    pane: Pane,
    sharedNames: Set<String>,
    onDrop: () -> Unit,
    computeDropFolder: (Offset) -> String?,
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

    // This pane is the live drop target when a drag from the OTHER pane is currently
    // hovering over its bounds. Tint the whole column faintly so the destination is
    // obvious even when the pointer is over background (not a specific folder row).
    val isDropTarget = drag.active &&
        drag.source != null &&
        drag.source != pane &&
        geometry.bounds.contains(drag.pointer)
    val paneTint = if (isDropTarget) {
        Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f))
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .border(BorderStroke(2.dp, borderColor))
            .then(paneTint)
            .onGloballyPositioned { geometry.bounds = it.boundsInWindow() },
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
                            DraggableFileRow(
                                item = item,
                                state = state,
                                geometry = geometry,
                                drag = drag,
                                pane = pane,
                                shared = sharedNames.contains(item.name),
                                onDrop = onDrop,
                                computeDropFolder = computeDropFolder,
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Folder-bounds are only meaningful while their rows exist; prune paths that
    // are no longer part of the listing so stale targets can't be hit.
    LaunchedEffect(state.items) {
        val live = state.items.filter { it.isDirectory }.mapTo(HashSet()) { it.path }
        val stale = geometry.folderBounds.keys.filterNot { live.contains(it) }
        stale.forEach { geometry.folderBounds.remove(it) }
    }
}

/**
 * A [FileRow] whose trailing area carries an explicit drag handle. Pressing the
 * handle and moving the finger starts a cross-pane drag *immediately* (via
 * [detectDragGestures], not a long-press) so it never competes with the row's own
 * tap / long-press. On drag start it captures the payload — the pane's whole
 * selection if it is already in selection mode, else this single [item] — and seeds
 * the shared [drag] state; on each drag it accumulates the pointer's window position;
 * on end/cancel it hands off to [onDrop] (which hit-tests and transfers) or clears.
 *
 * Folder rows still report their window bounds into [geometry] (via the row's own
 * [onGloballyPositioned]) so a drop can be hit-tested against them.
 */
@Composable
private fun DraggableFileRow(
    item: FileItem,
    state: FileBrowserUiState,
    geometry: PaneGeometry,
    drag: DragState,
    pane: Pane,
    shared: Boolean,
    onDrop: () -> Unit,
    computeDropFolder: (Offset) -> String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // The drag handle's own window origin, kept current so the drag's local offset
    // can be converted to a window-space pointer position for hit-testing.
    var handleOrigin by remember { mutableStateOf(Offset.Zero) }

    // Row modifier: report folder bounds only (drop targets). No drag gesture here —
    // the drag lives entirely on the handle so the row's tap/long-press is untouched.
    val rowModifier = Modifier
        .onGloballyPositioned { coords ->
            if (item.isDirectory) {
                geometry.folderBounds[item.path] = coords.boundsInWindow()
            }
        }

    // Live drop-target highlight: this folder is the one currently under the dragged
    // object. Recomputed every drag move via drag.hoverTarget, so only the row under
    // the finger lights up. Composed *over* the existing Compare (shared) tint.
    val isDropHover = drag.active && item.isDirectory && item.path == drag.hoverTarget
    var background: Modifier = Modifier
    if (shared) {
        background = background.background(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        )
    }
    if (isDropHover) {
        background = background.background(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        )
    }

    FileRow(
        item = item,
        selected = state.selectedPaths.contains(item.path),
        selectionMode = state.selectionMode,
        onClick = onClick,
        onLongClick = onLongClick,
        dense = true,
        modifier = rowModifier.then(background),
        dragHandle = {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Drag to other pane",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        handleOrigin = Offset(b.left, b.top)
                    }
                    .pointerInput(item.path, state.selectionMode, state.selectedPaths) {
                        detectDragGestures(
                            onDragStart = { local ->
                                // Payload: whole selection if already selecting, else this item.
                                val payload = if (state.selectionMode && state.selectedPaths.isNotEmpty()) {
                                    state.items.filter { state.selectedPaths.contains(it.path) }
                                        .ifEmpty { listOf(item) }
                                } else {
                                    listOf(item)
                                }
                                drag.source = pane
                                drag.items = payload
                                drag.pointer = handleOrigin + local
                                // Seed the highlight from the initial pointer position.
                                drag.hoverTarget = computeDropFolder(drag.pointer)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                drag.pointer += dragAmount
                                // Fully dynamic: re-resolve the folder under the pointer
                                // on every move so the highlight tracks the finger live.
                                drag.hoverTarget = computeDropFolder(drag.pointer)
                            },
                            onDragEnd = { onDrop() },
                            onDragCancel = { drag.clear() },
                        )
                    }
                    .padding(8.dp),
            )
        },
    )
}
