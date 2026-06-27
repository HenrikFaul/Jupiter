package com.jupiter.filemanager.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.SectionHeader
import com.jupiter.filemanager.ui.components.iconForFile

/**
 * Downloads screen: lists the real device Downloads folder, newest first.
 *
 * Renders an honest empty state when the folder has no files — no fabricated
 * data. Tapping a file delegates opening to the caller via [onOpenFile].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onOpenFile: (FileItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.files.isEmpty() -> {
                LoadingView(modifier = Modifier.padding(innerPadding))
            }

            uiState.error != null && uiState.files.isEmpty() -> {
                ErrorView(
                    message = uiState.error ?: "Something went wrong.",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.files.isEmpty() -> {
                EmptyView(
                    title = "No downloads yet",
                    message = "Files you download will appear here. " +
                        "Pull in something and check back.",
                    icon = Icons.Outlined.Download,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                DownloadsContent(
                    files = uiState.files,
                    onOpenFile = onOpenFile,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun DownloadsContent(
    files: List<FileItem>,
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
        item(key = "downloads-header") {
            SectionHeader(title = formatItemCount(files.size))
            Spacer(modifier = Modifier.size(4.dp))
        }
        items(
            items = files,
            key = { "file:" + it.path },
        ) { file ->
            DownloadFileCard(file = file, onClick = { onOpenFile(file) })
        }
    }
}

@Composable
private fun DownloadFileCard(
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
