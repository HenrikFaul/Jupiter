package com.jupiter.filemanager.feature.cleanup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.MergeRecommendation
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.iconForFile
import kotlinx.coroutines.launch

/**
 * Smart Merge screen: for each detected duplicate group, recommends keeping a
 * single "best" copy (the highest-quality one) and removing the rest. The user
 * can change which copy to keep per group, open a copy to inspect it, copy its
 * path, then apply the merge to reclaim space. All data is real, derived from
 * [StorageAnalyticsRepository.findDuplicates] and probed media quality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartMergeScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
    viewModel: SmartMergeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }

    // When the user returns from the All-Files-Access settings screen, re-run the
    // scan ONLY if we were previously blocked on permission so a just-granted
    // permission recovers automatically.
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
            TopAppBar(
                title = { Text("Smart Merge") },
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
                        onClick = { viewModel.scan() },
                        enabled = !state.isScanning && !state.isMerging,
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
            if (state.recommendations.isNotEmpty()) {
                MergeBar(
                    removableCount = state.totalRemovableCount,
                    reclaimableBytes = state.totalReclaimableBytes,
                    isMerging = state.isMerging,
                    onMerge = { showConfirm = true },
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
                state.permissionRequired -> {
                    PermissionRequiredView(
                        onGrant = { launchAllFilesAccessSettings(context.packageName, context::startActivity) },
                    )
                }

                state.recommendations.isEmpty() && state.isScanning -> {
                    LoadingView()
                }

                state.isEmpty -> {
                    EmptyView(
                        title = "Nothing to merge",
                        message = "We didn't find any duplicate copies to merge. Your storage is already tidy.",
                        icon = Icons.Outlined.AutoFixHigh,
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
                                groupCount = state.recommendations.size,
                                removableCount = state.totalRemovableCount,
                                reclaimableBytes = state.totalReclaimableBytes,
                                isScanning = state.isScanning,
                            )
                        }
                        items(state.recommendations, key = { it.group.hash }) { recommendation ->
                            MergeGroupCard(
                                recommendation = recommendation,
                                keepPath = state.keepPathFor(
                                    recommendation.group.hash,
                                    recommendation.recommendedKeepPath,
                                ),
                                qualityLabel = { path -> state.qualityLabelFor(path) },
                                onSelectKeep = { path ->
                                    viewModel.selectKeep(recommendation.group.hash, path)
                                },
                                onOpenFile = onOpenFile,
                                onCopyPath = { path ->
                                    clipboardManager.setText(AnnotatedString(path))
                                    scope.launch { snackbarHostState.showSnackbar("Path copied") }
                                },
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
            title = { Text("Merge duplicates") },
            text = {
                Text(
                    "This keeps the selected copy in each group and permanently deletes " +
                        "the ${state.totalRemovableCount} other copy" +
                        (if (state.totalRemovableCount == 1) "" else "ies") +
                        ", reclaiming ${formatBytes(state.totalReclaimableBytes)}. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        viewModel.mergeAll()
                    },
                ) {
                    Text("Merge", color = MaterialTheme.colorScheme.error)
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

/**
 * Launches the system "All files access" settings for this app so the user can
 * grant the permission Smart Merge needs. Prefers the app-scoped action and
 * falls back to the global list; failures are swallowed so we never crash.
 */
private fun launchAllFilesAccessSettings(
    packageName: String,
    startActivity: (Intent) -> Unit,
) {
    val launched = runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", packageName, null),
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
        }
        startActivity(intent)
    }.isSuccess

    if (!launched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}

/**
 * Actionable empty-state shown when the app lacks "All files access". Explains why
 * the scan can't run and offers a button that opens the settings screen inline.
 */
@Composable
private fun PermissionRequiredView(onGrant: () -> Unit) {
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "All files access needed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Smart Merge scans your storage for duplicate copies to merge. " +
                "Grant \"All files access\" so it can read your files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGrant) {
            Text("Grant All Files Access")
        }
    }
}

@Composable
private fun SummaryCard(
    groupCount: Int,
    removableCount: Int,
    reclaimableBytes: Long,
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
                text = formatBytes(reclaimableBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "reclaimable by removing $removableCount duplicate" +
                    (if (removableCount == 1) "" else "s") +
                    " across $groupCount group" + if (groupCount == 1) "" else "s",
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
private fun MergeGroupCard(
    recommendation: MergeRecommendation,
    keepPath: String,
    qualityLabel: (String) -> String,
    onSelectKeep: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onCopyPath: (String) -> Unit,
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
                    text = "${recommendation.group.files.size} copies",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${formatBytes(recommendation.reclaimableBytes)} reclaimable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Keeping the best-quality copy — the rest will be removed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            recommendation.group.files.forEachIndexed { index, file ->
                MergeFileRow(
                    file = file,
                    isKept = file.path == keepPath,
                    isRecommended = file.path == recommendation.recommendedKeepPath,
                    qualityLabel = qualityLabel(file.path),
                    onSelect = { onSelectKeep(file.path) },
                    onOpen = { onOpenFile(file) },
                    onCopyPath = { onCopyPath(file.path) },
                )
                if (index != recommendation.group.files.lastIndex) {
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
private fun MergeFileRow(
    file: FileItem,
    isKept: Boolean,
    isRecommended: Boolean,
    qualityLabel: String,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onCopyPath: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isKept,
            onClick = onSelect,
        )
        Spacer(modifier = Modifier.width(8.dp))
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
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (isKept) {
                        append("Keeping this copy")
                    } else {
                        append(formatRelativeTime(file.lastModified))
                    }
                    append(" • ")
                    append(formatBytes(file.sizeBytes))
                    if (isRecommended && !isKept) {
                        append(" • Recommended")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isKept || isRecommended) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (qualityLabel.isNotEmpty()) {
                Text(
                    text = qualityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            )
        }
    }
}

@Composable
private fun MergeBar(
    removableCount: Int,
    reclaimableBytes: Long,
    isMerging: Boolean,
    onMerge: () -> Unit,
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
                    text = "$removableCount to remove",
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
                onClick = onMerge,
                enabled = !isMerging && removableCount > 0,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Done,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isMerging) "Merging…" else "Merge")
            }
        }
    }
}
