package com.jupiter.filemanager.feature.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.domain.model.PrivacyLevel
import com.jupiter.filemanager.domain.model.PrivacyReport
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.StatRow
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Privacy Dashboard: surfaces an overall privacy posture (Good / Fair / At risk)
 * derived from real vault and settings signals, plus a breakdown of contributing
 * stats. "View Details" routes to the encrypted vault.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen(
    onOpenRoute: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PrivacyDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy & Recovery",
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
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                        )
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> LoadingView()
                uiState.errorMessage != null -> ErrorView(
                    message = uiState.errorMessage!!,
                    onRetry = viewModel::refresh,
                )
                uiState.report != null -> PrivacyContent(
                    report = uiState.report!!,
                    onOpenRoute = onOpenRoute,
                )
                else -> EmptyView(
                    title = "No privacy data",
                    message = "We couldn't gather your privacy posture yet. Pull to refresh to try again.",
                    icon = Icons.Outlined.PrivacyTip,
                )
            }
        }
    }
}

@Composable
private fun PrivacyContent(
    report: PrivacyReport,
    onOpenRoute: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        PrivacyHealthHeader(report = report)

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                StatRow(
                    label = "Encrypted Files",
                    value = formatItemCount(report.encryptedFiles),
                    icon = Icons.Filled.Lock,
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow(
                    label = "Hidden Files",
                    value = formatItemCount(report.hiddenFiles),
                    icon = Icons.Filled.VisibilityOff,
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow(
                    label = "Shared Links",
                    value = "Review providers",
                    icon = Icons.Filled.Share,
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow(
                    label = "Apps with Access",
                    value = "Review Android settings",
                    icon = Icons.Filled.Apps,
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow(
                    label = "Data Exposed",
                    value = exposureValue(report.level),
                    icon = Icons.Filled.Shield,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { onOpenRoute(Destination.Vault.route) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open secure Vault")
        }

        Spacer(modifier = Modifier.height(24.dp))

        RecoveryCenter(onOpenRoute = onOpenRoute)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Sharing and third-party app permissions are not inferred from missing data. " +
                "Review them in their providers and Android settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Single, action-oriented entry point for the places where a user can protect or recover data.
 * Every tile routes to an existing, functioning surface rather than promising an opaque audit.
 */
@Composable
private fun RecoveryCenter(onOpenRoute: (String) -> Unit) {
    Text(
        text = "Privacy & Recovery Center",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Protect sensitive files, review connected services, and recover reviewed cleanups.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))

    RecoveryAction(
        icon = Icons.Filled.Lock,
        title = "Secure Vault",
        detail = "Encrypted files protected by your Vault PIN.",
        action = "Open Vault",
        onClick = { onOpenRoute(Destination.Vault.route) },
    )
    Spacer(modifier = Modifier.height(10.dp))
    RecoveryAction(
        icon = Icons.Filled.Apps,
        title = "Connected cloud & NAS",
        detail = "Review configured network connections and remove access you no longer need.",
        action = "Manage connections",
        onClick = { onOpenRoute(Destination.NasConnections.route) },
    )
    Spacer(modifier = Modifier.height(10.dp))
    RecoveryAction(
        icon = Icons.Filled.Shield,
        title = "Recycle Bin & recovery",
        detail = "Restore files from reviewed cleanups before the bin is emptied.",
        action = "Open Recycle Bin",
        onClick = { onOpenRoute(Destination.Trash.route) },
    )
    Spacer(modifier = Modifier.height(10.dp))
    RecoveryAction(
        icon = Icons.Filled.Share,
        title = "Jupiscan Relay sessions",
        detail = "Start or stop the short-lived paired local sharing session.",
        action = "Open Relay",
        onClick = { onOpenRoute(Destination.WifiTransfer.route) },
    )
    Spacer(modifier = Modifier.height(10.dp))
    RecoveryAction(
        icon = Icons.Filled.VisibilityOff,
        title = "Permissions & data transparency",
        detail = "See what Jupiscan uses locally and why.",
        action = "Review data use",
        onClick = { onOpenRoute(Destination.DataTransparency.route) },
    )
}

@Composable
private fun RecoveryAction(
    icon: ImageVector,
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = JupiterDesign.CompactCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JupiterIconBadge(icon = icon, size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun PrivacyHealthHeader(report: PrivacyReport) {
    val accent = levelColor(report.level)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.12f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            JupiterIconBadge(
                icon = levelIcon(report.level),
                tint = accent,
                contentDescription = null,
                size = 56.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = levelTitle(report.level),
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = report.dataExposure,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun levelTitle(level: PrivacyLevel): String = when (level) {
    PrivacyLevel.GOOD -> "Good"
    PrivacyLevel.FAIR -> "Fair"
    PrivacyLevel.AT_RISK -> "At Risk"
}

private fun levelIcon(level: PrivacyLevel): ImageVector = when (level) {
    PrivacyLevel.GOOD -> Icons.Filled.GppGood
    PrivacyLevel.FAIR -> Icons.Filled.GppMaybe
    PrivacyLevel.AT_RISK -> Icons.Outlined.PrivacyTip
}

@Composable
private fun levelColor(level: PrivacyLevel): Color = when (level) {
    PrivacyLevel.GOOD -> JupiterDesign.Safe
    PrivacyLevel.FAIR -> JupiterDesign.Warning
    PrivacyLevel.AT_RISK -> MaterialTheme.colorScheme.error
}

private fun exposureValue(level: PrivacyLevel): String = when (level) {
    PrivacyLevel.GOOD -> "Low"
    PrivacyLevel.FAIR -> "Moderate"
    PrivacyLevel.AT_RISK -> "High"
}
