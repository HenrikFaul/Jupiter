package com.jupiter.filemanager.feature.cleanup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatStorageBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.model.StorageOverview
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.theme.JupiterDesign
import java.io.File

/**
 * Cleanup screen: shows a categorized storage breakdown, a selectable list of large
 * files and duplicate groups, plus a "Reclaim X" delete button guarded by a
 * confirmation dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: CleanupViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    // When permission was missing, re-run the scan once the user returns to the
    // screen (e.g. after granting All-Files-Access in Settings).
    val permissionRequired by rememberUpdatedState(uiState.permissionRequired)
    LifecycleResumeEffect(Unit) {
        if (permissionRequired) {
            viewModel.scan()
        }
        onPauseOrDispose {}
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cleanup",
                        style = MaterialTheme.typography.headlineSmall,
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
                    IconButton(
                        onClick = viewModel::rescan,
                        enabled = !uiState.isScanning,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Rescan storage",
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = {
            ReclaimBar(
                reclaimableBytes = uiState.reclaimableBytes,
                selectedCount = uiState.selectedForDeletion.size,
                onReclaim = { showDeleteConfirm = true },
            )
        },
    ) { innerPadding ->
        when {
            uiState.permissionRequired -> {
                PermissionRequiredView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            uiState.error != null && uiState.overview == null &&
                uiState.largeFiles.isEmpty() && uiState.duplicateGroups.isEmpty() -> {
                ErrorView(
                    message = uiState.error ?: "Something went wrong.",
                    onRetry = viewModel::scan,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> CleanupContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                isScanning = uiState.isScanning,
                overview = uiState.overview,
                largeFiles = uiState.largeFiles,
                duplicateGroups = uiState.duplicateGroups,
                selectedForDeletion = uiState.selectedForDeletion,
                inlineError = uiState.error,
                fromIndex = uiState.fromIndex,
                indexedCount = uiState.indexedCount,
                onToggleSelection = viewModel::toggleSelection,
                onOpenFile = onOpenFile,
                onScan = viewModel::scan,
                onRescan = viewModel::rescan,
            )
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            count = uiState.selectedForDeletion.size,
            reclaimableBytes = uiState.reclaimableBytes,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSelected()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * Scrolling body of the screen composed of the storage breakdown, large files and
 * duplicate groups.
 */
@Composable
private fun CleanupContent(
    modifier: Modifier,
    isScanning: Boolean,
    overview: StorageOverview?,
    largeFiles: List<FileItem>,
    duplicateGroups: List<DuplicateGroup>,
    selectedForDeletion: Set<String>,
    inlineError: String?,
    fromIndex: Boolean,
    indexedCount: Int,
    onToggleSelection: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onScan: () -> Unit,
    onRescan: () -> Unit,
) {
    val hasScanned = overview != null || isScanning ||
        largeFiles.isNotEmpty() || duplicateGroups.isNotEmpty()
    val potentialReclaimBytes = largeFiles.sumOf { it.sizeBytes } +
        duplicateGroups.sumOf { it.wastedBytes }
    val safeToCleanBytes = duplicateGroups.sumOf { it.wastedBytes }

    // Collapse state for the file-list sections. Hoisted here (rather than inside the
    // LazyColumn item lambdas) so it survives when the header scrolls out of view.
    // Collapsed by default: the section HEADERS stay visible, only the rows are hidden.
    var largeExpanded by remember { mutableStateOf(false) }
    var duplicatesExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isScanning) {
            item(key = "scanning") {
                ScanningCard()
            }
        }

        if (fromIndex) {
            item(key = "index-status") {
                IndexStatusCard(indexedCount = indexedCount, onRescan = onRescan)
            }
        }

        if (hasScanned) {
            item(key = "reclaim-hero") {
                PotentialReclaimHero(totalBytes = potentialReclaimBytes)
            }
            if (safeToCleanBytes > 0L) {
                item(key = "safe-to-clean") {
                    SafeToCleanCard(
                        safeBytes = safeToCleanBytes,
                        onSelectSafe = {
                            duplicateGroups.forEach { group ->
                                group.files.drop(1).forEach { file ->
                                    if (file.path !in selectedForDeletion) {
                                        onToggleSelection(file.path)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        if (inlineError != null) {
            item(key = "inline-error") {
                Text(
                    text = inlineError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (overview != null) {
            item(key = "breakdown") {
                StorageBreakdownCard(overview = overview)
            }
        }

        if (hasScanned) {
            val largeHasRows = largeFiles.isNotEmpty()
            item(key = "large-header") {
                CollapsibleSectionHeader(
                    title = "Large files",
                    subtitle = if (largeFiles.isEmpty() && !isScanning) {
                        "No large files found"
                    } else {
                        formatItemCount(largeFiles.size)
                    },
                    expanded = largeExpanded,
                    expandable = largeHasRows,
                    onToggle = { largeExpanded = !largeExpanded },
                )
            }
            if (largeExpanded) {
                items(largeFiles, key = { "large-" + it.path }) { file ->
                    SelectableFileRow(
                        item = file,
                        selected = file.path in selectedForDeletion,
                        onToggle = { onToggleSelection(file.path) },
                        onOpen = { onOpenFile(file) },
                    )
                }
            }

            val duplicatesHasRows = duplicateGroups.isNotEmpty()
            item(key = "dup-header") {
                CollapsibleSectionHeader(
                    title = "Duplicate files",
                    subtitle = if (duplicateGroups.isEmpty() && !isScanning) {
                        "No duplicates found"
                    } else {
                        val wasted = duplicateGroups.sumOf { it.wastedBytes }
                        duplicateGroups.size.toString() + " groups - " + formatBytes(wasted) + " wasted"
                    },
                    expanded = duplicatesExpanded,
                    expandable = duplicatesHasRows,
                    onToggle = { duplicatesExpanded = !duplicatesExpanded },
                )
            }
            if (duplicatesExpanded) {
                duplicateGroups.forEachIndexed { index, group ->
                    item(key = "dup-group-header-" + group.hash + "-" + index) {
                        DuplicateGroupHeader(group = group)
                    }
                    items(group.files, key = { "dup-" + group.hash + "-" + it.path }) { file ->
                        SelectableFileRow(
                            item = file,
                            selected = file.path in selectedForDeletion,
                            onToggle = { onToggleSelection(file.path) },
                            onOpen = { onOpenFile(file) },
                        )
                    }
                }
            }
        }

        if (overview == null && largeFiles.isEmpty() && duplicateGroups.isEmpty() && !isScanning) {
            item(key = "scan-cta") {
                ScanCallToAction(onScan = onScan)
            }
        }
    }
}

/** A small indeterminate banner shown while the scan is in progress. */
@Composable
private fun ScanningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CompactCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Scanning storage...",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Banner shown when the results were served instantly from the persistent file index
 * (no deep scan). Explains that the index self-updates on downloads/edits and offers a
 * one-tap full rescan for ground-truth results.
 */
@Composable
private fun IndexStatusCard(indexedCount: Int, onRescan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CompactCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Instant results from your file index",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (indexedCount > 0) {
                        formatItemCount(indexedCount) +
                            " indexed - downloads and edits update it automatically."
                    } else {
                        "Downloads and edits update it automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onRescan) {
                Text(text = "Rescan")
            }
        }
    }
}

/** Prominent hero summarizing the total space that could potentially be freed. */
@Composable
private fun PotentialReclaimHero(totalBytes: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            JupiterIconBadge(
                icon = Icons.Filled.CleaningServices,
                contentDescription = null,
                size = 52.dp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatBytes(totalBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "can be freed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Green-toned card summarizing safe-to-remove bytes with a one-tap select action. */
@Composable
private fun SafeToCleanCard(
    safeBytes: Long,
    onSelectSafe: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = JupiterDesign.Safe.copy(alpha = 0.10f),
        ),
        border = BorderStroke(1.dp, JupiterDesign.Safe.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CleaningServices,
                contentDescription = null,
                tint = JupiterDesign.Safe,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Safe to clean",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatBytes(safeBytes) + " from duplicate copies",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterDesign.Safe,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onSelectSafe) {
                Text(text = "Select safe items")
            }
        }
    }
}

/** Initial prompt inviting the user to start a scan when no results exist. */
@Composable
private fun ScanCallToAction(onScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.CleaningServices,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Free up space",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "Scan your storage to find large files and duplicates you can safely remove.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onScan,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(text = "Start scan")
            }
        }
    }
}

/**
 * Full-screen empty-state shown when the app lacks All-Files-Access. Offers an
 * actionable button that opens the system "All files access" settings inline; on
 * return the screen re-runs the scan via the ON_RESUME recovery effect.
 */
@Composable
private fun PermissionRequiredView(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "All files access needed",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Jupiter needs all-files access to scan your storage for large " +
                "files and duplicates. Grant access, then return here to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = {
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.fromParts("package", ctx.packageName, null),
                    )
                    ctx.startActivity(intent)
                }.onFailure {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                        )
                    }
                }
            },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = "Grant All Files Access")
        }
    }
}

/** Card showing the per-category storage breakdown bars. */
@Composable
private fun StorageBreakdownCard(
    overview: StorageOverview,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = overview.volume.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatStorageBytes(overview.volume.usedBytes) + " used of " +
                    formatStorageBytes(overview.volume.totalBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            val total = overview.totalAnalyzedBytes.coerceAtLeast(1L)
            val sortedCategories = overview.categories.sortedByDescending { it.sizeBytes }
            sortedCategories.forEach { usage ->
                CategoryBar(
                    usage = usage,
                    fraction = (usage.sizeBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** A single labelled progress bar for one storage category. */
@Composable
private fun CategoryBar(
    usage: CategoryUsage,
    fraction: Float,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = labelForCategory(usage.category),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatBytes(usage.sizeBytes) + " - " + formatItemCount(usage.fileCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(JupiterDesign.PillShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(JupiterDesign.PillShape)
                    .background(colorForCategory(usage.category)),
            )
        }
    }
}

/**
 * A tappable, collapsible section header rendered as a filled Card so it clearly reads
 * as a button: tapping the whole card toggles the section. Shows the title, a summary
 * subtitle (kept visible even when collapsed) and a chevron that rotates to indicate
 * expanded state. When [expandable] is false (no rows to show) the card is inert and the
 * chevron is hidden, but the summary text still renders.
 */
@Composable
private fun CollapsibleSectionHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    expandable: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevronRotation",
    )
    // A tonal container so the header visibly reads as a tappable control (the primary
    // affordance the user asked for: "files appear when I tap the section name"). The
    // colour lifts slightly when expanded to reinforce the open state.
    val containerColor = if (expanded) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (expandable) Modifier.clickable(onClick = onToggle) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = JupiterDesign.CompactCardShape,
        border = BorderStroke(
            1.dp,
            if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expandable) {
                Text(
                    text = if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(chevronRotation),
                )
            }
        }
    }
}

/** Header summarizing a single duplicate group. */
@Composable
private fun DuplicateGroupHeader(group: DuplicateGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatItemCount(group.files.size) + " identical",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = formatBytes(group.wastedBytes) + " reclaimable",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Leading thumbnail for a cleanup row. Images and videos render a real cropped,
 * rounded thumbnail via Coil (video frames decode through the app-wide
 * VideoFrameDecoder registered in JupiterApp); the type icon serves as both
 * placeholder and error fallback. Non-media files keep their type icon.
 */
@Composable
private fun FileThumbnail(item: FileItem) {
    val thumbnailable = item.type == FileType.IMAGE || item.type == FileType.VIDEO
    Surface(
        shape = JupiterDesign.IconBadgeShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(40.dp),
    ) {
        if (thumbnailable) {
            val fallbackPainter = rememberVectorPainter(iconForFile(item))
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(item.path))
                    .crossfade(true)
                    .size(96)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = fallbackPainter,
                error = fallbackPainter,
                modifier = Modifier
                    .size(40.dp)
                    .clip(JupiterDesign.IconBadgeShape),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = iconForFile(item),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** A selectable file row with a checkbox; tapping the body opens the file. */
@Composable
private fun SelectableFileRow(
    item: FileItem,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CompactCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
            )
            Spacer(modifier = Modifier.width(8.dp))
            FileThumbnail(item = item)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Bottom bar containing the primary "Reclaim X" delete action. */
@Composable
private fun ReclaimBar(
    reclaimableBytes: Long,
    selectedCount: Int,
    onReclaim: () -> Unit,
) {
    if (selectedCount == 0) return

    // enableEdgeToEdge() draws the app behind the system bars, so this bottom bar must
    // add navigation-bar insets or it renders behind the gesture/3-button nav. The
    // surface background is applied BEFORE the inset padding so it fills the inset area
    // too, and the content is pushed above the nav buttons.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(0.dp),
        )
        Button(
            onClick = onReclaim,
            modifier = Modifier.fillMaxWidth(),
            shape = JupiterDesign.PillShape,
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Reclaim " + formatBytes(reclaimableBytes) +
                    " (" + formatItemCount(selectedCount) + ")",
            )
        }
    }
}

/** Confirmation dialog shown before deleting the selected files. */
@Composable
private fun DeleteConfirmDialog(
    count: Int,
    reclaimableBytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
            )
        },
        title = { Text(text = "Move files to Recycle Bin?") },
        text = {
            Text(
                text = "This moves " + formatItemCount(count) +
                    " to the Recycle Bin and can reclaim " + formatBytes(reclaimableBytes) +
                    ". You can restore the files until they are permanently removed from the bin.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Move to Recycle Bin",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

/** Human-readable label for a [StorageCategory]. */
private fun labelForCategory(category: StorageCategory): String = when (category) {
    StorageCategory.IMAGES -> "Images"
    StorageCategory.VIDEOS -> "Videos"
    StorageCategory.AUDIO -> "Audio"
    StorageCategory.DOCUMENTS -> "Documents"
    StorageCategory.ARCHIVES -> "Archives"
    StorageCategory.APPS -> "Apps"
    StorageCategory.DOWNLOADS -> "Downloads"
    StorageCategory.OTHER -> "Other"
}

/** Stable accent color for a [StorageCategory]'s bar. */
private fun colorForCategory(category: StorageCategory): Color = when (category) {
    StorageCategory.IMAGES -> JupiterDesign.CategoryPhoto
    StorageCategory.VIDEOS -> JupiterDesign.CategoryVideo
    StorageCategory.AUDIO -> JupiterDesign.CategoryAudio
    StorageCategory.DOCUMENTS -> JupiterDesign.CategoryDocument
    StorageCategory.ARCHIVES -> JupiterDesign.CategoryArchive
    StorageCategory.APPS -> JupiterDesign.CategoryApk
    StorageCategory.DOWNLOADS -> JupiterDesign.CategoryDownload
    StorageCategory.OTHER -> JupiterDesign.CategoryOther
}
