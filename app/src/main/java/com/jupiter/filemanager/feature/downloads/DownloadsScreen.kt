package com.jupiter.filemanager.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
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
import com.jupiter.filemanager.core.util.formatDate
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.SectionHeader
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.theme.JupiterDesign
import java.util.Calendar

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Downloads",
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
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
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
    // Files arrive newest-first, so grouping by day preserves chronological order.
    // groupBy keeps first-seen key order, so day sections stay newest-first too.
    val groups = files.groupBy { dayStartMillis(it.lastModified) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 20.dp,
            end = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { (dayStart, dayFiles) ->
            item(key = "header:$dayStart") {
                SectionHeader(title = dayGroupLabel(dayStart))
                Spacer(modifier = Modifier.size(4.dp))
            }
            items(
                items = dayFiles,
                key = { "file:" + it.path },
            ) { file ->
                DownloadFileCard(file = file, onClick = { onOpenFile(file) })
            }
        }
    }
}

/** Returns the epoch-millis at the start of the local calendar day for [epochMillis]. */
private fun dayStartMillis(epochMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Human label for a day-start timestamp: "Today", "Yesterday", or a formatted date. */
private fun dayGroupLabel(dayStartMillis: Long): String {
    val todayStart = dayStartMillis(System.currentTimeMillis())
    val oneDayMillis = 24L * 60L * 60L * 1000L
    return when (dayStartMillis) {
        todayStart -> "Today"
        todayStart - oneDayMillis -> "Yesterday"
        else -> formatDate(dayStartMillis)
    }
}

@Composable
private fun DownloadFileCard(
    file: FileItem,
    onClick: () -> Unit,
) {
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JupiterIconBadge(
                icon = iconForFile(file),
                tint = JupiterDesign.CategoryDownload,
                contentDescription = null,
                size = 44.dp,
            )
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
