package com.jupiter.filemanager.feature.apps

import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.domain.model.AppStorageInfo
import com.jupiter.filemanager.domain.model.AppStorageOverview
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterFloatingBottomNavigation
import com.jupiter.filemanager.ui.components.JupiterMainTab
import com.jupiter.filemanager.ui.components.JupiterPill
import com.jupiter.filemanager.ui.components.JupiterStorageRing
import com.jupiter.filemanager.ui.components.JupiterWordmark
import com.jupiter.filemanager.ui.theme.JupiterDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Local presentation filters; they never mutate the streamed platform measurements. */
private enum class AppStorageFilter {
    AllApps,
    Largest,
    CacheHeavy,
}

private const val LARGEST_APPS_LIMIT = 10

/**
 * Per-app storage breakdown. Accounts for the app-private space (`Android/data`, APKs,
 * caches) the filesystem cannot enumerate on Android 11+, via `StorageStatsManager`.
 * Requires the Usage-access grant; when missing, prompts the user to enable it.
 *
 * Tapping an app opens an action sheet: system App-info (clear cache/data, uninstall),
 * browse the app's visible files (`Android/media/<pkg>`, when present — [onOpenPath]),
 * and uninstall. Android only lets the system Settings clear ANOTHER app's private data,
 * so the sheet deep-links there instead of pretending Jupiter could delete it directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppStorageScreen(
    onBack: () -> Unit,
    onOpenPath: (String) -> Unit,
    onMainTabSelected: (JupiterMainTab) -> Unit = {},
    showBackButton: Boolean = true,
) {
    val viewModel: AppStorageViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The app whose action sheet is open, or null when none is.
    var selectedApp by remember { mutableStateOf<AppStorageInfo?>(null) }
    var topMenuExpanded by rememberSaveable { mutableStateOf(false) }

    // Re-query every time the screen RESUMES — the first resume is already covered by the
    // ViewModel's init load, so skip it, but every later resume reloads. This catches returning
    // from the system uninstall dialog (so a just-uninstalled app drops off the list immediately,
    // since the query enumerates only currently-installed packages) as well as granting
    // Usage-access in Settings.
    var firstResume by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        // onResume() polls for a just-granted Usage-access (which can lag the return from Settings)
        // and then loads, so the scan starts on its own without leaving and re-entering the screen.
        if (firstResume) firstResume = false else viewModel.onResume()
        onPauseOrDispose {}
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    JupiterWordmark()
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load, enabled = !uiState.isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    if (!showBackButton) {
                        Box {
                            IconButton(onClick = { topMenuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = topMenuExpanded,
                                onDismissRequest = { topMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Back") },
                                    onClick = {
                                        topMenuExpanded = false
                                        onBack()
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            JupiterFloatingBottomNavigation(
                selectedTab = JupiterMainTab.MORE,
                onTabSelected = onMainTabSelected,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = "App storage",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            val modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            val overview = uiState.overview
            when {
                uiState.permissionRequired -> UsageAccessRequired(modifier)
                // Nothing to show yet (before the first apps arrive): a clear "Scanning…" view so the
                // grant prompt is gone the instant access is confirmed, even though the walk is ongoing.
                // Gated on isLoading so a scan that legitimately finds no apps falls through to the
                // (empty) content instead of spinning forever.
                overview == null || (uiState.isLoading && overview.apps.isEmpty()) ->
                    ScanningView(overview, modifier)
                else -> AppStorageContent(
                    overview = overview,
                    scanning = uiState.isLoading,
                    onAppClick = { selectedApp = it },
                    modifier = modifier,
                )
            }
        }
    }

    selectedApp?.let { app ->
        AppActionsSheet(
            app = app,
            onOpenPath = onOpenPath,
            onDismiss = { selectedApp = null },
        )
    }
}

/**
 * Bottom sheet with the actions available for one app. Everything that frees space in
 * another app's PRIVATE storage must go through the system (App info / uninstall) — that
 * is a platform rule, so the sheet deep-links to the right system screens. When the app
 * exposes files under `Android/media/<pkg>` (readable by file managers, e.g. WhatsApp
 * media), a browse action opens them directly in Jupiter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppActionsSheet(
    app: AppStorageInfo,
    onOpenPath: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // The app's shared-storage media folder, when it exists (single stat; remembered).
    val mediaDir = remember(app.packageName) {
        runCatching {
            File(
                Environment.getExternalStorageDirectory(),
                "Android/media/${app.packageName}",
            ).takeIf { it.isDirectory }
        }.getOrNull()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JupiterIconBadge(
                    icon = Icons.Filled.Android,
                    contentDescription = null,
                    size = 48.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatBytes(app.totalBytes)} · app ${formatBytes(app.appBytes)} · " +
                            "data ${formatBytes(app.dataBytesExcludingCache)} · " +
                            "cache ${formatBytes(app.cacheBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            SheetAction(
                icon = Icons.Outlined.Info,
                title = "App info · clear cache & data",
                subtitle = "Opens system settings, where cache/data can be cleared",
            ) {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", app.packageName, null),
                        ),
                    )
                }
                onDismiss()
            }
            if (mediaDir != null) {
                SheetAction(
                    icon = Icons.Outlined.Folder,
                    title = "Browse this app's files",
                    subtitle = "Android/media/${app.packageName}",
                ) {
                    onOpenPath(mediaDir.absolutePath)
                    onDismiss()
                }
            }
            if (!app.isSystemApp) {
                SheetAction(
                    icon = Icons.Outlined.Delete,
                    title = "Uninstall",
                    subtitle = "Frees the app, its data and cache (${formatBytes(app.totalBytes)})",
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_DELETE,
                                Uri.fromParts("package", app.packageName, null),
                            ),
                        )
                    }
                    onDismiss()
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Android only lets the system Settings clear another app's private " +
                    "data — Jupiter takes you straight to the right screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One tappable action row inside [AppActionsSheet]. */
@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(JupiterDesign.CompactCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppStorageContent(
    overview: AppStorageOverview?,
    scanning: Boolean,
    onAppClick: (AppStorageInfo) -> Unit,
    modifier: Modifier,
) {
    if (overview == null) return

    var selectedFilterName by rememberSaveable { mutableStateOf(AppStorageFilter.AllApps.name) }
    val selectedFilter = AppStorageFilter.values().firstOrNull { it.name == selectedFilterName }
        ?: AppStorageFilter.AllApps
    val visibleApps = remember(overview.apps, selectedFilter) {
        appsForFilter(overview.apps, selectedFilter)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "total") {
            AppStorageSummaryCard(
                overview = overview,
                scanning = scanning,
            )
        }
        if (scanning) {
            item(key = "scanning") {
                AppStorageProgressCard(
                    overview = overview,
                    // A refresh deliberately keeps the complete previous list on screen until
                    // the new stream finishes. In that short period [overview] is not the live
                    // progress frame, so use an indeterminate treatment rather than claiming
                    // the retained 100% value is the new scan's progress.
                    hasLiveProgress = overview.scanning,
                )
            }
        }
        item(key = "filters") {
            AppStorageFilterBar(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilterName = it.name },
            )
        }

        if (visibleApps.isEmpty()) {
            item(key = "filter_empty") { AppStorageFilterEmptyState(selectedFilter) }
        } else {
            itemsIndexed(
                items = visibleApps,
                key = { _, app -> "app_${app.packageName}" },
            ) { index, app ->
                AppRow(
                    app = app,
                    rank = index + 1,
                    onClick = { onAppClick(app) },
                )
            }
        }

        // Keep the Android privacy model explanation available without crowding out the
        // actionable storage list the supplied design prioritises above the fold.
        item(key = "explainer") {
            AppPrivacyExplainer()
        }
    }
}

@Composable
private fun AppStorageSummaryCard(
    overview: AppStorageOverview,
    scanning: Boolean,
) {
    val measurementLabel = when {
        scanning && overview.scanning && overview.totalCount > 0 ->
            "${overview.scannedCount} of ${overview.totalCount} apps measured"
        scanning -> "Refreshing app storage"
        else -> "used by ${overview.apps.size} apps"
    }

    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // This ring represents the measured app-storage category, not a percentage of the
            // phone: AppStorageOverview intentionally has no device-capacity denominator. A full
            // ring once a non-zero category is measured avoids inventing a misleading capacity.
            JupiterStorageRing(
                fraction = if (overview.totalBytes > 0L) 1f else 0f,
                size = 112.dp,
                strokeWidth = 12.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatBytes(overview.totalBytes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = "used",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Total app storage",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    JupiterIconBadge(
                        icon = Icons.Filled.PieChart,
                        contentDescription = null,
                        size = 40.dp,
                    )
                }
                Text(
                    text = formatBytes(overview.totalBytes),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = measurementLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (overview.cacheBytes > 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = "${formatBytes(overview.cacheBytes)} clearable cache",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The determinate, on-device scan treatment shown above any streamed partial results. */
@Composable
private fun AppStorageProgressCard(
    overview: AppStorageOverview,
    hasLiveProgress: Boolean,
) {
    val hasTotal = hasLiveProgress && overview.totalCount > 0
    val progress = if (hasTotal) overview.progress else 0f
    val progressPercent = (progress * 100).roundToInt()

    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JupiterStorageRing(
                fraction = progress,
                size = 88.dp,
                strokeWidth = 8.dp,
            ) {
                Text(
                    text = if (hasTotal) "$progressPercent%" else "…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (hasTotal) {
                        "${overview.scannedCount} / ${overview.totalCount} apps scanned"
                    } else {
                        "Refreshing app storage"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (hasLiveProgress) {
                        "Scanning app storage"
                    } else {
                        "Keeping the last complete results visible"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasTotal) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(JupiterDesign.PillShape),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(JupiterDesign.PillShape),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Scanning safely on device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppStorageFilterBar(
    selectedFilter: AppStorageFilter,
    onFilterSelected: (AppStorageFilter) -> Unit,
) {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppStorageFilter.values().forEach { filter ->
                AppStorageFilterChip(
                    filter = filter,
                    selected = filter == selectedFilter,
                    onClick = { onFilterSelected(filter) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AppStorageFilterChip(
    filter: AppStorageFilter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (label, icon) = when (filter) {
        AppStorageFilter.AllApps -> "All apps" to Icons.Filled.Apps
        AppStorageFilter.Largest -> "Largest" to Icons.Filled.TrendingUp
        AppStorageFilter.CacheHeavy -> "Cache-heavy" to Icons.Filled.Cached
    }
    JupiterPill(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Applies only local, reversible presentation filtering to the already-streamed real values. */
private fun appsForFilter(
    apps: List<AppStorageInfo>,
    filter: AppStorageFilter,
): List<AppStorageInfo> = when (filter) {
    AppStorageFilter.AllApps -> apps.sortedByDescending(AppStorageInfo::totalBytes)
    // A bounded top set gives this chip a genuinely different, useful view while keeping every
    // installed package available under All apps.
    AppStorageFilter.Largest -> apps.sortedByDescending(AppStorageInfo::totalBytes).take(LARGEST_APPS_LIMIT)
    AppStorageFilter.CacheHeavy -> apps
        .filter { it.cacheBytes > 0L }
        .sortedWith(
            compareByDescending<AppStorageInfo> { it.cacheBytes }
                .thenByDescending { it.totalBytes },
        )
}

@Composable
private fun AppStorageFilterEmptyState(filter: AppStorageFilter) {
    val message = when (filter) {
        AppStorageFilter.AllApps -> "No installed apps could be measured yet."
        AppStorageFilter.Largest -> "No measured apps are available yet."
        AppStorageFilter.CacheHeavy -> "No apps with clearable cache were found."
    }
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JupiterIconBadge(
                icon = Icons.Filled.Android,
                contentDescription = null,
                size = 44.dp,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: AppStorageInfo,
    rank: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // The app's launcher icon, resolved OFF the main thread (getApplicationIcon decodes a
    // drawable — doing it on the composition thread janks the frame when the list first appears).
    val icon by produceState<android.graphics.drawable.Drawable?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
        }
    }
    val fallback = rememberVectorPainter(Icons.Filled.Android)

    JupiterCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model = ImageRequest.Builder(context).data(icon).crossfade(true).build(),
                contentDescription = null,
                placeholder = fallback,
                error = fallback,
                modifier = Modifier
                    .size(52.dp)
                    .clip(JupiterDesign.IconBadgeShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // "Data" excludes cache (platform dataBytes includes it), matching Settings.
                Text(
                    text = "Data ${formatBytes(app.dataBytesExcludingCache)} · " +
                        "Cache ${formatBytes(app.cacheBytes)} · APK ${formatBytes(app.appBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatBytes(app.totalBytes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${app.label} actions",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Honest explanation of the Android storage model. Full "All files access" lets Jupiter browse
 * everything in shared storage, but two kinds of app-private space stay off-limits to every file
 * manager: since Android 11, other apps' `Android/data` and `Android/obb` folders are walled off
 * even with All files access; and installed APKs (in `/data/app`) plus app caches were never
 * reachable by a non-root file manager on any Android version. On a typical phone that hidden
 * space is the *largest* part of "used" storage. Only a rooted device can open those files, so
 * Jupiter accounts for their size per app here instead.
 */
@Composable
private fun AppPrivacyExplainer() {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            JupiterIconBadge(
                icon = Icons.Outlined.Folder,
                contentDescription = null,
                size = 42.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Why files here can't be browsed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Since Android 11, other apps' Android/data and Android/obb folders are " +
                        "sealed off from every file manager — even with All files access. Installed " +
                        "APKs and app caches aren't browsable by any non-root file manager either. On " +
                        "most phones that hidden space is the biggest part of what's used. No app can " +
                        "open those files (only a rooted device can), so Jupiter measures their size " +
                        "per app here instead of listing them as files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Shown from the moment access is confirmed until the first apps arrive — makes the several-second
 * per-app scan legible ("Scanning…" + a determinate progress bar) instead of leaving the grant
 * prompt or a blank screen up. Falls back to an indeterminate bar until the package total is known.
 */
@Composable
private fun ScanningView(overview: AppStorageOverview?, modifier: Modifier) {
    val total = overview?.totalCount ?: 0
    val scanned = overview?.scannedCount ?: 0
    val hasProgress = total > 0
    val progress = overview?.progress ?: 0f

    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        JupiterCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                JupiterStorageRing(
                    fraction = progress,
                    size = 112.dp,
                    strokeWidth = 10.dp,
                ) {
                    Text(
                        text = if (hasProgress) "${(progress * 100).roundToInt()}%" else "…",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Scanning app storage…",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Measuring how much space each installed app uses. The biggest apps appear first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                )
                if (hasProgress) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(JupiterDesign.PillShape),
                    )
                    Text(
                        text = "$scanned / $total apps scanned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(JupiterDesign.PillShape),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Scanning safely on device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageAccessRequired(modifier: Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        JupiterCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                JupiterIconBadge(
                    icon = Icons.Filled.Apps,
                    contentDescription = null,
                    size = 64.dp,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "See what apps are using",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Most of a full phone is app storage (games, app data and caches) that " +
                        "Android hides from file managers. Grant Jupiter \"Usage access\" to show a " +
                        "per-app breakdown and account for that space.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                ) {
                    Text("Grant Usage access")
                }
            }
        }
    }
}
