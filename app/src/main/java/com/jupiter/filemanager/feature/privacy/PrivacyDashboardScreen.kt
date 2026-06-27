package com.jupiter.filemanager.feature.privacy

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.StatRow
import com.jupiter.filemanager.ui.navigation.Destination

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
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
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
                    onViewDetails = { onOpenRoute(Destination.Vault.route) },
                )
            }
        }
    }
}

@Composable
private fun PrivacyContent(
    report: PrivacyReport,
    onViewDetails: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        PrivacyHealthHeader(report = report)

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                    value = report.sharedLinks.toString(),
                    icon = Icons.Filled.Share,
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow(
                    label = "Apps with Access",
                    value = report.appsWithAccess.toString(),
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
            onClick = onViewDetails,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Details")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Shared links and third-party app access aren't tracked on this device yet, so they're reported as zero.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivacyHealthHeader(report: PrivacyReport) {
    val accent = levelColor(report.level)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.12f),
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
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.18f),
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = levelIcon(report.level),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
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
    PrivacyLevel.GOOD -> Color(0xFF16A34A)
    PrivacyLevel.FAIR -> Color(0xFFD97706)
    PrivacyLevel.AT_RISK -> MaterialTheme.colorScheme.error
}

private fun exposureValue(level: PrivacyLevel): String = when (level) {
    PrivacyLevel.GOOD -> "Low"
    PrivacyLevel.FAIR -> "Moderate"
    PrivacyLevel.AT_RISK -> "High"
}
