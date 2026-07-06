package com.jupiter.filemanager.feature.analytics

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.model.StorageOverview
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.SectionHeader
import com.jupiter.filemanager.ui.components.StorageBar
import com.jupiter.filemanager.ui.navigation.Destination

/**
 * Storage Analytics screen.
 *
 * Renders a real, category-by-category breakdown of the primary storage volume
 * (drawn as a donut chart plus a labelled list), a total-usage bar, and a
 * "Large files" entry that hands off to the Cleanup destination where the actual
 * large-file scan + reclamation lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyticsScreen(
    onOpenRoute: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: StorageAnalyticsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Recover automatically once the user returns from the All-Files-Access
    // settings screen having granted permission: only re-run when we were blocked.
    LifecycleResumeEffect(uiState.permissionRequired) {
        if (uiState.permissionRequired) {
            viewModel.analyze()
        }
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Storage Analytics") },
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
                        onClick = viewModel::analyze,
                        enabled = !uiState.isLoading && !uiState.isScanning,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            // Permission gate takes precedence over an empty scan: don't spin,
            // show an actionable CTA that opens All-Files-Access settings.
            uiState.permissionRequired && uiState.overview == null -> {
                PermissionRequiredView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            uiState.isLoading && uiState.overview == null -> {
                LoadingView(modifier = Modifier.padding(innerPadding))
            }

            uiState.error != null && uiState.overview == null -> {
                ErrorView(
                    message = uiState.error ?: "Unable to analyze storage.",
                    onRetry = viewModel::analyze,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.overview != null -> {
                AnalyticsContent(
                    overview = uiState.overview!!,
                    isScanning = uiState.isScanning,
                    fromIndex = uiState.fromIndex,
                    indexedCount = uiState.indexedCount,
                    onOpenLargeFiles = { onOpenRoute(Destination.Cleanup.route) },
                    onOpenAppStorage = { onOpenRoute(Destination.AppStorage.route) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}

/** Scrolling body: donut breakdown, total-usage bar, per-category list, large-files entry. */
@Composable
private fun AnalyticsContent(
    overview: StorageOverview,
    isScanning: Boolean,
    fromIndex: Boolean,
    indexedCount: Int,
    onOpenLargeFiles: () -> Unit,
    onOpenAppStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedCategories = overview.categories
        .filter { it.sizeBytes > 0L }
        .sortedByDescending { it.sizeBytes }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isScanning) {
            item(key = "scanning-chip") {
                ScanningChip()
            }
        }

        if (fromIndex && !isScanning) {
            item(key = "index-chip") {
                IndexedFromCard(indexedCount = indexedCount)
            }
        }

        item(key = "donut") {
            BreakdownCard(
                overview = overview,
                categories = sortedCategories,
            )
        }

        item(key = "total-usage") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StorageBar(
                        label = overview.volume.label,
                        usedBytes = overview.volume.usedBytes,
                        totalBytes = overview.volume.totalBytes,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatBytes(overview.volume.availableBytes) + " free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "app-storage-entry") {
            AppStorageEntry(
                usedBytes = overview.volume.usedBytes,
                analyzedBytes = overview.totalAnalyzedBytes,
                onClick = onOpenAppStorage,
            )
        }

        item(key = "categories-header") {
            SectionHeader(title = "By category")
        }

        if (sortedCategories.isEmpty()) {
            item(key = "categories-empty") {
                Text(
                    text = "No files were found to categorize on this volume.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        } else {
            val total = overview.totalAnalyzedBytes.coerceAtLeast(1L)
            items(sortedCategories, key = { it.category.name }) { usage ->
                CategoryRow(
                    usage = usage,
                    fraction = (usage.sizeBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                )
            }
        }

        item(key = "large-files-header") {
            SectionHeader(title = "Reclaim space")
        }

        item(key = "large-files-entry") {
            LargeFilesEntry(onClick = onOpenLargeFiles)
        }
    }
}

/** Card showing the donut chart with a centered total, plus a color legend. */
@Composable
private fun BreakdownCard(
    overview: StorageOverview,
    categories: List<CategoryUsage>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Storage breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DonutChart(
                    categories = categories,
                    totalAnalyzedBytes = overview.totalAnalyzedBytes,
                    centerLabel = formatBytes(overview.totalAnalyzedBytes),
                    modifier = Modifier.size(140.dp),
                )
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (categories.isEmpty()) {
                        Text(
                            text = "No data",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        categories.forEach { usage ->
                            LegendItem(usage = usage)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A Canvas-drawn donut chart of category proportions. */
@Composable
private fun DonutChart(
    categories: List<CategoryUsage>,
    totalAnalyzedBytes: Long,
    centerLabel: String,
    modifier: Modifier = Modifier,
) {
    val total = totalAnalyzedBytes.coerceAtLeast(1L)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.16f
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Background track for empty/unaccounted space.
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )

            var startAngle = -90f
            categories.forEach { usage ->
                val sweep = (usage.sizeBytes.toFloat() / total.toFloat()) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = colorForCategory(usage.category),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth),
                    )
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.DonutLarge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "analyzed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A single legend entry: colored dot, category name, and its size. */
@Composable
private fun LegendItem(usage: CategoryUsage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(colorForCategory(usage.category)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = labelForCategory(usage.category),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatBytes(usage.sizeBytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Full-width row for one category with a proportional progress bar. */
@Composable
private fun CategoryRow(
    usage: CategoryUsage,
    fraction: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(colorForCategory(usage.category)),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = labelForCategory(usage.category),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = formatItemCount(usage.fileCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatBytes(usage.sizeBytes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Tappable card that routes to the Cleanup destination for large-file scanning. */
@Composable
private fun LargeFilesEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FindInPage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Find large files",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Scan for big files and duplicates to reclaim space",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Entry to the per-app storage breakdown. Files on the volume ([analyzedBytes]) are usually
 * far less than the total [usedBytes] because most of a full phone is app-private storage
 * (games, app data, caches) the filesystem can't list — this card surfaces that gap and
 * links to the StorageStatsManager-backed breakdown.
 */
@Composable
private fun AppStorageEntry(usedBytes: Long, analyzedBytes: Long, onClick: () -> Unit) {
    val appish = (usedBytes - analyzedBytes).coerceAtLeast(0L)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "App storage",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (appish > 0) {
                        "~" + formatBytes(appish) + " is app data & caches the filesystem can't show"
                    } else {
                        "See per-app storage usage"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Lightweight "still scanning…" indicator shown above the breakdown while the
 * incremental walk continues. It never blocks the already-rendered content.
 */
@Composable
private fun ScanningChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Still scanning…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Compact chip shown when the breakdown was served instantly from the persistent file
 * index (no filesystem walk). Mirrors the Cleanup screen's index affordance so the user
 * can see the indexed survey is doing the work.
 */
@Composable
private fun IndexedFromCard(indexedCount: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.FindInPage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (indexedCount > 0) {
                "Instant from index · " + formatItemCount(indexedCount)
            } else {
                "Instant from index"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Actionable empty-state shown when the app lacks broad storage access. Rather
 * than spinning forever on a scan that can read nothing, it explains the gap and
 * launches the All-Files-Access settings inline so the user can grant it; the
 * screen re-runs the scan automatically on resume.
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "All files access needed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To analyze how your storage is used, Jupiter needs permission " +
                "to access all files on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
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
        ) {
            Text(text = "Grant All Files Access")
        }
    }
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

/** Stable accent color for a [StorageCategory]. */
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
