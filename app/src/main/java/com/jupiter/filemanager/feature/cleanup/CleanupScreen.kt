package com.jupiter.filemanager.feature.cleanup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOff
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.model.StorageOverview
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.iconForFile

/**
 * Cleanup screen: shows a categorized storage breakdown, a selectable list of large
 * files and duplicate groups, an optional AI explanation card and a "Reclaim X"
 * delete button guarded by a confirmation dialog.
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
        topBar = {
            TopAppBar(
                title = { Text(text = "Cleanup") },
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
                        onClick = viewModel::scan,
                        enabled = !uiState.isScanning,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Rescan",
                        )
                    }
                },
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
                aiExplanation = uiState.aiExplanation,
                inlineError = uiState.error,
                onToggleSelection = viewModel::toggleSelection,
                onOpenFile = onOpenFile,
                onScan = viewModel::scan,
                onExplainWithAi = viewModel::explainWithAi,
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
 * Scrolling body of the screen composed of the storage breakdown, the optional AI
 * card, large files and duplicate groups.
 */
@Composable
private fun CleanupContent(
    modifier: Modifier,
    isScanning: Boolean,
    overview: StorageOverview?,
    largeFiles: List<FileItem>,
    duplicateGroups: List<DuplicateGroup>,
    selectedForDeletion: Set<String>,
    aiExplanation: String?,
    inlineError: String?,
    onToggleSelection: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onScan: () -> Unit,
    onExplainWithAi: () -> Unit,
) {
    val hasScanned = overview != null || isScanning ||
        largeFiles.isNotEmpty() || duplicateGroups.isNotEmpty()
    val potentialReclaimBytes = largeFiles.sumOf { it.sizeBytes } +
        duplicateGroups.sumOf { it.wastedBytes }
    val safeToCleanBytes = duplicateGroups.sumOf { it.wastedBytes }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isScanning) {
            item(key = "scanning") {
                ScanningCard()
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
                StorageBreakdownCard(
                    overview = overview,
                    aiExplanation = aiExplanation,
                    onExplainWithAi = onExplainWithAi,
                )
            }
        }

        if (hasScanned) {
            item(key = "large-header") {
                SectionHeader(
                    title = "Large files",
                    subtitle = if (largeFiles.isEmpty() && !isScanning) {
                        "No large files found"
                    } else {
                        formatItemCount(largeFiles.size)
                    },
                )
            }
            items(largeFiles, key = { "large-" + it.path }) { file ->
                SelectableFileRow(
                    item = file,
                    selected = file.path in selectedForDeletion,
                    onToggle = { onToggleSelection(file.path) },
                    onOpen = { onOpenFile(file) },
                )
            }

            item(key = "dup-header") {
                SectionHeader(
                    title = "Duplicate files",
                    subtitle = if (duplicateGroups.isEmpty() && !isScanning) {
                        "No duplicates found"
                    } else {
                        val wasted = duplicateGroups.sumOf { it.wastedBytes }
                        duplicateGroups.size.toString() + " groups - " + formatBytes(wasted) + " wasted"
                    },
                )
            }
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
    Card(modifier = Modifier.fillMaxWidth()) {
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

/** Prominent hero summarizing the total space that could potentially be freed. */
@Composable
private fun PotentialReclaimHero(totalBytes: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.CleaningServices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatBytes(totalBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "can be freed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE6F4EA),
        ),
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
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Safe to clean",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B5E20),
                )
                Text(
                    text = formatBytes(safeBytes) + " from duplicate copies",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
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
    Card(modifier = Modifier.fillMaxWidth()) {
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

/** Card showing the per-category storage breakdown bars and the optional AI card. */
@Composable
private fun StorageBreakdownCard(
    overview: StorageOverview,
    aiExplanation: String?,
    onExplainWithAi: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = overview.volume.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatBytes(overview.volume.usedBytes) + " used of " +
                    formatBytes(overview.volume.totalBytes),
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

            AiExplanationSection(
                aiExplanation = aiExplanation,
                onExplainWithAi = onExplainWithAi,
            )
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
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorForCategory(usage.category)),
            )
        }
    }
}

/** Optional AI explanation block embedded in the breakdown card. */
@Composable
private fun AiExplanationSection(
    aiExplanation: String?,
    onExplainWithAi: () -> Unit,
) {
    if (aiExplanation != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = aiExplanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = onExplainWithAi,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Explain with AI")
        }
    }
}

/** A section header with a title and a supporting subtitle. */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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

/** A selectable file row with a checkbox; tapping the body opens the file. */
@Composable
private fun SelectableFileRow(
    item: FileItem,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
            Icon(
                imageVector = iconForFile(item),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
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
        title = { Text(text = "Delete files?") },
        text = {
            Text(
                text = "This will permanently delete " + formatItemCount(count) +
                    " and free up " + formatBytes(reclaimableBytes) +
                    ". This action cannot be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete",
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
    StorageCategory.IMAGES -> Color(0xFF42A5F5)
    StorageCategory.VIDEOS -> Color(0xFFEF5350)
    StorageCategory.AUDIO -> Color(0xFFAB47BC)
    StorageCategory.DOCUMENTS -> Color(0xFF26A69A)
    StorageCategory.ARCHIVES -> Color(0xFFFFA726)
    StorageCategory.APPS -> Color(0xFF66BB6A)
    StorageCategory.DOWNLOADS -> Color(0xFF5C6BC0)
    StorageCategory.OTHER -> Color(0xFF8D6E63)
}
