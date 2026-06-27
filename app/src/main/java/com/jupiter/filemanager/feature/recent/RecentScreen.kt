package com.jupiter.filemanager.feature.recent

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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.ActivityEntry
import com.jupiter.filemanager.domain.model.ActivityType
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.SectionHeader
import com.jupiter.filemanager.ui.components.iconForFile

/**
 * Recent tab: shows recently modified files across primary storage plus the
 * recorded activity feed. Both sections render honest empty states when there is
 * nothing to show — no fabricated data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(
    onOpenFile: (FileItem) -> Unit,
    onOpenRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Recent") },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.recentFiles.isEmpty() && uiState.activity.isEmpty() -> {
                LoadingView(modifier = Modifier.padding(innerPadding))
            }

            uiState.error != null &&
                uiState.recentFiles.isEmpty() &&
                uiState.activity.isEmpty() -> {
                ErrorView(
                    message = uiState.error ?: "Something went wrong.",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.recentFiles.isEmpty() && uiState.activity.isEmpty() -> {
                EmptyView(
                    title = "Nothing recent yet",
                    message = "Files you open or modify and actions you take will " +
                        "show up here.",
                    icon = Icons.Outlined.HistoryToggleOff,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                RecentContent(
                    uiState = uiState,
                    onOpenFile = onOpenFile,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun RecentContent(
    uiState: RecentUiState,
    onOpenFile: (FileItem) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.recentFiles.isNotEmpty()) {
            item(key = "recent-header") {
                SectionHeader(title = "Recent files")
            }
            items(
                items = uiState.recentFiles,
                key = { "file:" + it.path },
            ) { file ->
                RecentFileCard(file = file, onClick = { onOpenFile(file) })
            }
        }

        item(key = "activity-header") {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = "Activity")
        }

        if (uiState.activity.isEmpty()) {
            item(key = "activity-empty") {
                EmptyActivityCard()
            }
        } else {
            items(
                items = uiState.activity,
                key = { "activity:" + it.id },
            ) { entry ->
                ActivityRow(entry = entry)
            }
        }
    }
}

@Composable
private fun RecentFileCard(
    file: FileItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForFile(file),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBytes(file.sizeBytes) +
                        "  ·  " + formatRelativeTime(file.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = iconForActivity(entry.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatRelativeTime(entry.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyActivityCard() {
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "No activity recorded yet. Move, copy, share or compress " +
                    "files and your history will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Maps an [ActivityType] to a representative Material icon. */
private fun iconForActivity(type: ActivityType): ImageVector = when (type) {
    ActivityType.MOVE -> Icons.Filled.DriveFileMove
    ActivityType.COPY -> Icons.Filled.ContentCopy
    ActivityType.DELETE -> Icons.Filled.Delete
    ActivityType.RENAME -> Icons.Filled.DriveFileRenameOutline
    ActivityType.SHARE -> Icons.Filled.Share
    ActivityType.COMPRESS -> Icons.Filled.Archive
    ActivityType.EXTRACT -> Icons.Filled.Unarchive
    ActivityType.BACKUP -> Icons.Filled.Backup
    ActivityType.SYNC -> Icons.Filled.Sync
    ActivityType.FAVORITE -> Icons.Filled.Star
}
