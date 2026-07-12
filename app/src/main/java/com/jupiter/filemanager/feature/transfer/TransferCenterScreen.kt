package com.jupiter.filemanager.feature.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.domain.model.TransferDirection
import com.jupiter.filemanager.domain.model.TransferStatus
import com.jupiter.filemanager.domain.model.TransferTask
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterPill
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Transfer Center.
 *
 * Hub for peer-to-peer and local-network file transfers. Two quick-start
 * actions launch the Nearby and Wi-Fi transfer flows, and a tabbed list shows
 * active transfers and finished history sourced from the real
 * [com.jupiter.filemanager.domain.repository.TransferRepository].
 *
 * Wi-Fi sharing routes to the live local server. Nearby discovery remains an explicitly-labelled
 * preview until its transport exists. The transfer-history repository starts empty and this screen
 * renders honest states instead of fabricating progress.
 *
 * @param onOpenRoute navigates to another destination route (Nearby / Wi-Fi).
 * @param onBack pops the current screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferCenterScreen(
    onOpenRoute: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TransferCenterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Transfer Center",
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
                    if (uiState.selectedTab == TransferCenterTab.HISTORY &&
                        uiState.hasClearableHistory
                    ) {
                        TextButton(onClick = viewModel::clearCompleted) {
                            Text(text = "Clear")
                        }
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            QuickStartRow(
                onNearby = { onOpenRoute(Destination.NearbyTransfer.route) },
                onWifi = { onOpenRoute(Destination.WifiTransfer.route) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            JupiterCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JupiterPill(
                        selected = uiState.selectedTab == TransferCenterTab.TRANSFERS,
                        onClick = { viewModel.selectTab(TransferCenterTab.TRANSFERS) },
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Text(text = "Transfers", style = MaterialTheme.typography.labelLarge)
                    }
                    JupiterPill(
                        selected = uiState.selectedTab == TransferCenterTab.HISTORY,
                        onClick = { viewModel.selectTab(TransferCenterTab.HISTORY) },
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Text(text = "History", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            val visible = when (uiState.selectedTab) {
                TransferCenterTab.TRANSFERS -> uiState.activeTransfers
                TransferCenterTab.HISTORY -> uiState.historyTransfers
            }

            if (visible.isEmpty()) {
                when (uiState.selectedTab) {
                    TransferCenterTab.TRANSFERS -> EmptyView(
                        title = "No active transfers",
                        message = "Start a Nearby or Wi-Fi transfer to send and " +
                            "receive files between devices. Transfers in progress " +
                            "will appear here.",
                        icon = Icons.Filled.SwapVert,
                    )

                    TransferCenterTab.HISTORY -> EmptyView(
                        title = "No transfer history",
                        message = "Completed and failed transfers will be listed " +
                            "here so you can review what was sent or received.",
                        icon = Icons.Filled.History,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = visible, key = { it.id }) { task ->
                        TransferTaskCard(task = task)
                    }
                }
            }
        }
    }
}

/**
 * Two prominent quick-start actions that launch the Nearby and Wi-Fi transfer
 * flows.
 */
@Composable
private fun QuickStartRow(
    onNearby: () -> Unit,
    onWifi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickStartCard(
            icon = Icons.Filled.NearMe,
            title = "Nearby",
            subtitle = "Discovery preview · transport coming soon",
            onClick = onNearby,
            modifier = Modifier.weight(1f),
        )
        QuickStartCard(
            icon = Icons.Filled.Wifi,
            title = "Wi-Fi",
            subtitle = "Share over local network",
            onClick = onWifi,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A single rounded quick-start tile with a tinted glyph, title and subtitle.
 */
@Composable
private fun QuickStartCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            JupiterIconBadge(icon = icon, contentDescription = null, size = 44.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Start")
            }
        }
    }
}

/**
 * A card representing a single [TransferTask], showing direction, file name,
 * status, byte progress, and a progress bar for in-flight transfers.
 */
@Composable
private fun TransferTaskCard(
    task: TransferTask,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = JupiterDesign.CompactCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JupiterIconBadge(
                    icon = directionIcon(task.direction),
                    tint = MaterialTheme.colorScheme.secondary,
                    contentDescription = null,
                    size = 40.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitleFor(task),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                StatusBadge(status = task.status)
            }

            if (task.status == TransferStatus.IN_PROGRESS ||
                task.status == TransferStatus.PAUSED ||
                task.status == TransferStatus.PENDING
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { task.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(JupiterDesign.PillShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${formatBytes(task.transferredBytes)} / " +
                        formatBytes(task.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Small status chip whose color and glyph reflect the transfer's lifecycle state.
 */
@Composable
private fun StatusBadge(status: TransferStatus) {
    val (label, icon, tint) = when (status) {
        TransferStatus.PENDING -> Triple(
            "Pending",
            Icons.Filled.History,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TransferStatus.IN_PROGRESS -> Triple(
            "Sending",
            Icons.Filled.SwapVert,
            MaterialTheme.colorScheme.primary,
        )

        TransferStatus.PAUSED -> Triple(
            "Paused",
            Icons.Filled.Pause,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TransferStatus.COMPLETED -> Triple(
            "Done",
            Icons.Filled.CheckCircle,
            MaterialTheme.colorScheme.primary,
        )

        TransferStatus.FAILED -> Triple(
            "Failed",
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.error,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

private fun directionIcon(direction: TransferDirection): ImageVector = when (direction) {
    TransferDirection.SEND -> Icons.Filled.CloudUpload
    TransferDirection.RECEIVE -> Icons.Filled.CloudDownload
}

private fun subtitleFor(task: TransferTask): String {
    val verb = when (task.direction) {
        TransferDirection.SEND -> "To"
        TransferDirection.RECEIVE -> "From"
    }
    val peer = task.peerName
    return if (peer != null) {
        "$verb $peer · ${formatBytes(task.sizeBytes)}"
    } else {
        formatBytes(task.sizeBytes)
    }
}
