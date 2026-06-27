package com.jupiter.filemanager.feature.version

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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.FileVersion
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView

/**
 * Version history screen for a single file.
 *
 * Lists the [FileVersion]s recorded for the file at the navigation path (read by
 * [VersionHistoryViewModel] from its [androidx.lifecycle.SavedStateHandle]). No real
 * versioning backend exists yet, so the list starts empty and the screen renders an
 * honest empty state describing that file versions will appear once versioning is
 * available — no fabricated history.
 *
 * Each version offers a "Restore" action that delegates to
 * [VersionHistoryViewModel.restore]; the repository's honest not-configured failure
 * is surfaced in a snackbar rather than pretending the restore succeeded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VersionHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface the one-shot restore result message (typically the honest
    // "Versioning not configured" failure) and then clear it.
    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Version history")
                        val subtitle = uiState.title
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.isEmpty -> {
                LoadingView(modifier = Modifier.padding(innerPadding))
            }

            uiState.error != null && uiState.isEmpty -> {
                ErrorView(
                    message = uiState.error ?: "Something went wrong.",
                    modifier = Modifier.padding(innerPadding),
                )
            }

            uiState.isEmpty -> {
                EmptyView(
                    title = "No versions yet",
                    message = "Earlier versions of this file will appear here once " +
                        "file versioning is available. Nothing is tracked yet.",
                    icon = Icons.Filled.History,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                VersionList(
                    versions = uiState.versions,
                    isRestoring = uiState.isRestoring,
                    onRestore = viewModel::restore,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun VersionList(
    versions: List<FileVersion>,
    isRestoring: Boolean,
    onRestore: (FileVersion) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = versions,
            key = { it.id },
        ) { version ->
            VersionCard(
                version = version,
                isRestoring = isRestoring,
                onRestore = { onRestore(version) },
            )
        }
    }
}

@Composable
private fun VersionCard(
    version: FileVersion,
    isRestoring: Boolean,
    onRestore: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = version.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatBytes(version.sizeBytes) +
                            "  ·  " + formatRelativeTime(version.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (version.isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Current") },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            if (!version.isCurrent) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onRestore,
                        enabled = !isRestoring,
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore")
                    }
                }
            }
        }
    }
}
