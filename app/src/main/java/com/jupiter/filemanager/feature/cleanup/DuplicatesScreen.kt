package com.jupiter.filemanager.feature.cleanup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.MediaQuality
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.JupiterPill
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterStorageRing
import com.jupiter.filemanager.ui.components.JupiterWordmark
import com.jupiter.filemanager.ui.components.JupiterFloatingBottomNavigation
import com.jupiter.filemanager.ui.components.JupiterMainTab
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.theme.JupiterDesign
import java.io.File
import kotlinx.coroutines.launch

/**
 * Lists duplicate file groups detected on the primary storage volume. Each group
 * shows its files, the user can select redundant copies to delete, and a
 * confirmation dialog guards the destructive action.
 *
 * Tapping a file row opens it via [onOpenFile]; a trailing copy icon copies the
 * file's absolute path to the clipboard. Each row also surfaces a probed
 * media-quality label when available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel(),
    initialPresentation: DuplicatePresentation = DuplicatePresentation.EXACT,
    onMainTabSelected: ((JupiterMainTab) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showConfirm by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(initialPresentation) {
        viewModel.setPresentation(initialPresentation)
    }

    // When the user returns from the system settings screen after we asked for
    // All-Files-Access, re-run the scan so a just-granted permission recovers.
    LifecycleResumeEffect(state.permissionRequired) {
        if (state.permissionRequired) {
            viewModel.scan()
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val message = state.errorMessage ?: state.infoMessage
        if (message != null) {
            scope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            DuplicatesHeader(
                hasSelection = state.selectedCount > 0,
                menuExpanded = showHeaderMenu,
                enabled = !state.isDeleting,
                canRescan = !state.isScanning && !state.isDeleting,
                onBack = onBack,
                onHelp = { showHelp = true },
                onMenuExpandedChange = { showHeaderMenu = it },
                onToggleSelection = {
                    if (state.selectedCount > 0) viewModel.clearSelection()
                    else viewModel.selectDuplicatesKeepingBest()
                },
                onRescan = viewModel::scan,
                onOpenDuplicateAlertSettings = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (state.selectedCount > 0) {
                    DeleteBar(
                        selectedCount = state.selectedCount,
                        reclaimableBytes = state.selectedReclaimableBytes,
                        isDeleting = state.isDeleting,
                        includeNavigationInset = onMainTabSelected == null,
                        onDelete = { showConfirm = true },
                    )
                }
                if (onMainTabSelected != null) {
                    JupiterFloatingBottomNavigation(
                        selectedTab = JupiterMainTab.MORE,
                        onTabSelected = onMainTabSelected,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.permissionRequired -> {
                    PermissionRequiredView()
                }

                state.groups.isEmpty() && state.isScanning -> {
                    LoadingView()
                }

                state.groups.isEmpty() && state.analyzingPhotos -> {
                    AnalyzingPhotosView()
                }

                state.isEmpty -> {
                    EmptyView(
                        title = "No duplicates found",
                        message = "Your files look tidy. We didn't find any duplicate copies on this device.",
                        icon = Icons.Outlined.ContentCopy,
                    )
                }

                else -> {
                    val presentedGroups = state.visibleGroups
                    val removablePaths = remember(
                        state.visibleGroups,
                        state.groups,
                        state.qualities,
                    ) {
                        DuplicateSelectionPolicy.removablePaths(
                            actionableGroups = state.visibleGroups,
                            allGroups = state.groups,
                            qualities = state.qualities,
                        )
                    }
                    val protectedKeeperPaths = remember(state.groups, state.qualities) {
                        DuplicateSelectionPolicy.protectedKeeperPaths(
                            allGroups = state.groups,
                            qualities = state.qualities,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = JupiterDesign.ScreenPadding,
                            top = 12.dp,
                            end = JupiterDesign.ScreenPadding,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            SummaryCard(
                                duplicateItemCount = state.totalDuplicateItemCount,
                                exactItemCount = state.duplicateItemCount(DuplicatePresentation.EXACT),
                                similarItemCount = state.duplicateItemCount(DuplicatePresentation.SIMILAR),
                                groupCount = state.sizeFilteredGroups.size,
                                wastedBytes = state.sizeFilteredGroups.sumOf { it.wastedBytes },
                                isScanning = state.isScanning,
                                onReview = {
                                    scope.launch { listState.animateScrollToItem(3) }
                                },
                            )
                        }
                        item {
                            DuplicatePresentationSelector(
                                selected = state.presentation,
                                exactCount = state.duplicateItemCount(DuplicatePresentation.EXACT),
                                similarCount = state.duplicateItemCount(DuplicatePresentation.SIMILAR),
                                enabled = !state.isDeleting,
                                onSelect = viewModel::setPresentation,
                            )
                        }
                        item {
                            DuplicateReviewControls(
                                selectedCount = state.selectedCount,
                                removableCount = removablePaths.size,
                                sizeFilter = state.sizeFilter,
                                sizeOrder = state.sizeOrder,
                                enabled = !state.isDeleting,
                                onSelectAll = viewModel::selectDuplicatesKeepingBest,
                                onDeselectAll = viewModel::clearSelection,
                                onSizeFilter = viewModel::setSizeFilter,
                                onSizeOrder = viewModel::setSizeOrder,
                            )
                        }
                        if (state.analyzingPhotos) {
                            item { AnalyzingPhotosBanner() }
                        }
                        if (presentedGroups.isEmpty()) {
                            item {
                                EmptyView(
                                    title = if (state.presentation == DuplicatePresentation.SIMILAR) {
                                        "No similar photos yet"
                                    } else {
                                        "No exact duplicates"
                                    },
                                    message = if (state.presentation == DuplicatePresentation.SIMILAR) {
                                        "Photo analysis continues in the background and will surface matches here."
                                    } else {
                                        "Switch to Similar photos to review visually matching images."
                                    },
                                    icon = if (state.presentation == DuplicatePresentation.SIMILAR) {
                                        Icons.Outlined.Image
                                    } else {
                                        Icons.Outlined.ContentCopy
                                    },
                                )
                            }
                        }
                        items(presentedGroups, key = { it.hash }) { group ->
                            DuplicateGroupCard(
                                group = group,
                                selectedPaths = state.selectedPaths,
                                qualities = state.qualities,
                                protectedKeeperPaths = protectedKeeperPaths,
                                isBusy = state.isDeleting,
                                onToggle = viewModel::toggleSelection,
                                onOpenFile = onOpenFile,
                                onCopyPath = { path ->
                                    clipboardManager.setText(AnnotatedString(path))
                                    viewModel.notify("Path copied")
                                },
                            )
                        }
                        item(key = "safe_footer") {
                            DuplicateSafetyFooter()
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
                    "This moves the selected copies to Recycle Bin and makes " +
                        "${formatBytes(state.selectedReclaimableBytes)} available. You can restore " +
                        "them from Recycle Bin before they are permanently removed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        viewModel.deleteSelected()
                    },
                ) {
                    Text("Move to Recycle Bin", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
            title = { Text("Safe duplicate review") },
            text = {
                Text(
                    "Exact copies and visually similar photos stay separate. Jupiscan protects " +
                        "the quality-ranked best file in every group, and deletion moves reviewed " +
                        "copies to Recycle Bin.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Got it") }
            },
        )
    }
}

@Composable
private fun DuplicatesHeader(
    hasSelection: Boolean,
    menuExpanded: Boolean,
    enabled: Boolean,
    canRescan: Boolean,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onToggleSelection: () -> Unit,
    onRescan: () -> Unit,
    onOpenDuplicateAlertSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterWordmark(modifier = Modifier.weight(1f))
            IconButton(onClick = onHelp) {
                Icon(Icons.Outlined.HelpOutline, contentDescription = "About duplicate cleanup")
            }
            Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Duplicate cleanup actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(if (hasSelection) "Clear selection" else "Select duplicates, keep best")
                        },
                        enabled = enabled,
                        leadingIcon = {
                            Icon(
                                if (hasSelection) Icons.Outlined.RemoveDone else Icons.Outlined.DoneAll,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            onToggleSelection()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rescan") },
                        enabled = canRescan,
                        leadingIcon = {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            onRescan()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate alert settings") },
                        enabled = enabled,
                        leadingIcon = {
                            Icon(Icons.Outlined.HelpOutline, contentDescription = null)
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            onOpenDuplicateAlertSettings()
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Duplicate cleanup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** Exact copies and perceptually similar photos remain visibly and logically separate. */
@Composable
private fun DuplicatePresentationSelector(
    selected: DuplicatePresentation,
    exactCount: Int,
    similarCount: Int,
    enabled: Boolean,
    onSelect: (DuplicatePresentation) -> Unit,
) {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(7.dp),
        shape = JupiterDesign.CompactCardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JupiterPill(
                selected = selected == DuplicatePresentation.EXACT,
                onClick = { if (enabled) onSelect(DuplicatePresentation.EXACT) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text(
                    text = "Exact files ($exactCount)",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
            JupiterPill(
                selected = selected == DuplicatePresentation.SIMILAR,
                onClick = { if (enabled) onSelect(DuplicatePresentation.SIMILAR) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Similar photos ($similarCount)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * Actionable empty-state shown when the app lacks All-Files-Access. Explains why
 * the scan can't run and offers a button that opens the system settings screen to
 * grant access. On return, the screen's ON_RESUME effect re-runs the scan.
 */
@Composable
private fun PermissionRequiredView() {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "All Files Access needed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To find duplicate files across your storage, Jupiscan needs " +
                "permission to access all files. Grant access and the scan will start " +
                "automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                runCatching {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.fromParts("package", ctx.packageName, null),
                        )
                    } else {
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", ctx.packageName, null),
                        )
                    }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ctx.startActivity(intent)
                }.onFailure {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                        )
                    }
                }
            },
        ) {
            Text("Grant All Files Access")
        }
    }
}

/** Full-screen state shown while the photo library is being fingerprinted and no groups exist yet. */
@Composable
private fun AnalyzingPhotosView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Analyzing your photos…",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Comparing your photo library to find visually-similar duplicates (same photo " +
                "re-saved, resized or re-sent). This runs once and can take a few minutes on a large " +
                "gallery — matches appear here as they're found.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}

/** In-list hint that photo analysis is still running, so more similar-photo groups may appear. */
@Composable
private fun AnalyzingPhotosBanner() {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        shape = JupiterDesign.CompactCardShape,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = Icons.Outlined.Image,
                tint = JupiterDesign.Purple,
                size = 52.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Analyzing your photos", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Finding the best matches…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                strokeWidth = 4.dp,
            )
        }
    }
}

@Composable
private fun SummaryCard(
    duplicateItemCount: Int,
    exactItemCount: Int,
    similarItemCount: Int,
    groupCount: Int,
    wastedBytes: Long,
    isScanning: Boolean,
    onReview: () -> Unit,
) {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.HeroCardShape,
        contentPadding = PaddingValues(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterStorageRing(
                fraction = if (duplicateItemCount > 0) 1f else 0f,
                size = 126.dp,
                strokeWidth = 15.dp,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(50.dp),
                )
            }
            Spacer(Modifier.width(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$duplicateItemCount duplicate items",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append("Exact copies: $exactItemCount")
                        if (similarItemCount > 0) append(" · Similar photos to review: $similarItemCount")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "${formatBytes(wastedBytes)} can be freed",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = if (isScanning) {
                        "Exact copies share identical content · Still scanning"
                    } else {
                        "Exact matches are identical · $groupCount groups · Safe to review"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                Button(
                    onClick = onReview,
                    enabled = duplicateItemCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Review now")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

/**
 * A row of size filters that narrow the visible duplicate groups by their largest copy — so the
 * user can focus on the space-worth duplicates (multi-MB photos / multi-GB videos) and ignore the
 * many tiny few-KB images. A group matches when any of its copies is at least the selected size.
 */
@Composable
private fun SizeFilterRow(
    selected: SizeFilter,
    enabled: Boolean,
    onSelect: (SizeFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SizeFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                enabled = enabled,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selectedPaths: Set<String>,
    qualities: Map<String, MediaQuality>,
    protectedKeeperPaths: Set<String>,
    isBusy: Boolean,
    onToggle: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onCopyPath: (String) -> Unit,
) {
    var expanded by rememberSaveable(group.hash) { mutableStateOf(false) }
    val ranked = remember(group, qualities) {
        DuplicateSelectionPolicy.rank(group, qualities)
    }
    val best = ranked.firstOrNull()
    val bestQuality = best?.path?.let(qualities::get)
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CompactCardShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuplicateStackPreview(files = ranked)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = best?.name ?: if (group.similar) "Similar photos" else "Exact files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${group.files.size} files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${formatBytes(group.wastedBytes)} can be freed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✓",
                            color = JupiterDesign.Safe,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Keep best", style = MaterialTheme.typography.labelMedium)
                        bestQuality?.label?.takeIf(String::isNotBlank)?.let { label ->
                            Text(
                                text = "  |  $label",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse group" else "Review group",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ranked.forEachIndexed { index, file ->
                key(file.path) {
                    val quality = qualities[file.path]
                    DuplicateFileRow(
                        file = file,
                        isKept = index == 0,
                        canSelect = file.path !in protectedKeeperPaths && !isBusy,
                        isSelected = file.path in selectedPaths,
                        quality = quality,
                        relativeNote = relativeQualityNote(
                            isBest = index == 0,
                            best = bestQuality,
                            current = quality,
                        ),
                        onToggle = { onToggle(file.path) },
                        onOpen = { onOpenFile(file) },
                        onCopyPath = { onCopyPath(file.path) },
                    )
                    if (index != ranked.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Always-visible duplicate review controls. The old overflow-only size filter and selection toggle
 * looked as if the features had disappeared; keeping the explicit buttons and chips in the content
 * makes the safe workflow discoverable while preserving the ViewModel's keeper protection.
 */
@Composable
private fun DuplicateReviewControls(
    selectedCount: Int,
    removableCount: Int,
    sizeFilter: SizeFilter,
    sizeOrder: DuplicateSizeOrder,
    enabled: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSizeFilter: (SizeFilter) -> Unit,
    onSizeOrder: (DuplicateSizeOrder) -> Unit,
) {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CompactCardShape,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSelectAll,
                enabled = enabled && removableCount > 0 && selectedCount < removableCount,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Select all")
            }
            TextButton(
                onClick = onDeselectAll,
                enabled = enabled && selectedCount > 0,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.RemoveDone, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Deselect all")
            }
        }
        Text(
            text = "Minimum duplicate size",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )
        SizeFilterRow(selected = sizeFilter, enabled = enabled, onSelect = onSizeFilter)
        Text(
            text = "Sort by size",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuplicateSizeOrder.entries.forEach { order ->
                FilterChip(
                    selected = sizeOrder == order,
                    enabled = enabled,
                    onClick = { onSizeOrder(order) },
                    label = { Text(order.label) },
                )
            }
        }
        Text(
            text = "Select all never selects the protected keeper in any group.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DuplicateStackPreview(files: List<FileItem>) {
    val previews = files.take(4)
    Box(
        modifier = Modifier
            .width(126.dp)
            .height(96.dp),
    ) {
        previews.asReversed().forEachIndexed { reverseIndex, file ->
            val originalIndex = previews.lastIndex - reverseIndex
            val fallback = rememberVectorPainter(iconForFile(file))
            Surface(
                modifier = Modifier
                    .size(82.dp)
                    .offset(x = (originalIndex * 13).dp, y = (originalIndex * 3).dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (file.type == FileType.IMAGE || file.type == FileType.VIDEO) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(file.path))
                            .size(180)
                            .memoryCacheKey(file.path)
                            .diskCacheKey(file.path)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = fallback,
                        error = fallback,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconForFile(file),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
        }
        if (files.size > previews.size) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 2.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = "+${files.size - 1}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun DuplicateFileRow(
    file: FileItem,
    isKept: Boolean,
    canSelect: Boolean,
    isSelected: Boolean,
    quality: MediaQuality?,
    relativeNote: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onCopyPath: () -> Unit,
) {
    // The best copy is highlighted; the remaining (removable) copies are muted.
    val rowBackground = if (isKept) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    val nameColor = if (isKept) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .background(rowBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isThumbnailable = file.type == FileType.IMAGE || file.type == FileType.VIDEO
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp),
        ) {
            if (isThumbnailable) {
                // Real image/video thumbnail via Coil. Video frames decode because the
                // app registers a VideoFrameDecoder app-wide (see JupiterApp). The type
                // icon serves as both placeholder and error fallback.
                val fallbackPainter = rememberVectorPainter(iconForFile(file))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(file.path))
                        .crossfade(true)
                        .size(96)
                        // Stable per-path cache keys so a thumbnail is never confused with
                        // another file's (belt-and-braces alongside the row key() above).
                        .memoryCacheKey(file.path)
                        .diskCacheKey(file.path)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = fallbackPainter,
                    error = fallbackPainter,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForFile(file),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isKept) FontWeight.SemiBold else FontWeight.Normal,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isKept) {
                    QualityBadge(
                        text = "BEST",
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    QualityBadge(
                        text = "DUPLICATE",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
            val qualityLabel = quality?.label?.takeIf { it.isNotBlank() }
            if (qualityLabel != null || relativeNote != null) {
                val qualityLine = buildString {
                    if (qualityLabel != null) append(qualityLabel)
                    if (relativeNote != null) {
                        if (isNotEmpty()) append(" • ")
                        append(relativeNote)
                    }
                }
                Text(
                    text = qualityLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isKept) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = file.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onCopyPath) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy path",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Checkbox(
            checked = isSelected,
            enabled = canSelect,
            onCheckedChange = if (!canSelect) null else { _ -> onToggle() },
        )
    }
}

/** Small pill badge used to mark the best copy vs. removable duplicates. */
@Composable
private fun QualityBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Tiny relative-quality indicator for a copy compared to the best one in its
 * group. Only meaningful when both files have a probed (non-zero) score.
 */
private fun relativeQualityNote(
    isBest: Boolean,
    best: MediaQuality?,
    current: MediaQuality?,
): String? {
    if (best == null || current == null) return null
    if (best.score <= 0L || current.score <= 0L) return null
    if (isBest) return "best quality"
    return when {
        current.score >= best.score -> "same quality"
        current.width > 0 && best.width > 0 && current.height > 0 && best.height > 0 &&
            (current.width.toLong() * current.height) < (best.width.toLong() * best.height) ->
            "lower resolution"
        else -> "lower quality"
    }
}

@Composable
private fun DuplicateSafetyFooter() {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        shape = JupiterDesign.CompactCardShape,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = Icons.Filled.Shield,
                tint = JupiterDesign.Purple,
                size = 46.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Your files are safe", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Exact matches have identical content. Best copies stay protected; reviewed deletions go to Recycle Bin for recovery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeleteBar(
    selectedCount: Int,
    reclaimableBytes: Long,
    isDeleting: Boolean,
    includeNavigationInset: Boolean,
    onDelete: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (includeNavigationInset) Modifier.navigationBarsPadding() else Modifier)
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
