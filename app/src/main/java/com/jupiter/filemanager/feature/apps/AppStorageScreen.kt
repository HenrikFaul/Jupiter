package com.jupiter.filemanager.feature.apps

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Android
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.jupiter.filemanager.domain.model.AppStorageInfo

/**
 * Per-app storage breakdown. Accounts for the app-private space (`Android/data`, APKs,
 * caches) the filesystem cannot enumerate on Android 11+, via `StorageStatsManager`.
 * Requires the Usage-access grant; when missing, prompts the user to enable it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppStorageScreen(onBack: () -> Unit) {
    val viewModel: AppStorageViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-query when returning to the screen (e.g. after granting Usage-access in Settings).
    LifecycleResumeEffect(Unit) {
        if (uiState.permissionRequired && viewModel.hasUsageAccess()) {
            viewModel.load()
        }
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load, enabled = !uiState.isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when {
            uiState.permissionRequired -> UsageAccessRequired(modifier)
            uiState.isLoading && uiState.overview == null -> LoadingView(modifier)
            else -> AppStorageContent(
                overview = uiState.overview,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun AppStorageContent(
    overview: com.jupiter.filemanager.domain.model.AppStorageOverview?,
    modifier: Modifier,
) {
    if (overview == null) return
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "total") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = formatBytes(overview.totalBytes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "used by ${overview.apps.size} apps" +
                            if (overview.cacheBytes > 0) {
                                " · ${formatBytes(overview.cacheBytes)} clearable cache"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        item(key = "explainer") {
            AppPrivacyExplainer()
        }
        val max = overview.apps.firstOrNull()?.totalBytes?.coerceAtLeast(1L) ?: 1L
        items(overview.apps, key = { it.packageName }) { app ->
            AppRow(app = app, fractionOfMax = app.totalBytes.toFloat() / max.toFloat())
        }
    }
}

@Composable
private fun AppRow(app: AppStorageInfo, fractionOfMax: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val context = LocalContext.current
            // The app's launcher icon (resolved lazily; only visible rows compose).
            val icon = remember(app.packageName) {
                runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
            }
            val fallback = rememberVectorPainter(Icons.Filled.Android)
            AsyncImage(
                model = ImageRequest.Builder(context).data(icon).crossfade(true).build(),
                contentDescription = null,
                placeholder = fallback,
                error = fallback,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fractionOfMax.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    text = "app ${formatBytes(app.appBytes)} · data ${formatBytes(app.dataBytes)} · " +
                        "cache ${formatBytes(app.cacheBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = formatBytes(app.totalBytes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

@Composable
private fun LoadingView(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun UsageAccessRequired(modifier: Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Android,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "See what apps are using",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Most of a full phone is app storage (games, app data and caches) that " +
                "Android hides from file managers. Grant Jupiter \"Usage access\" to show a " +
                "per-app breakdown and account for that space.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Grant Usage access")
        }
    }
}
