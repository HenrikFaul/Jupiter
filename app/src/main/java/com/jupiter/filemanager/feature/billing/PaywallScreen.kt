package com.jupiter.filemanager.feature.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterWordmark
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * A single Pro benefit advertised on the paywall.
 */
private data class ProBenefit(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val ProBenefits: List<ProBenefit> = listOf(
    ProBenefit(
        icon = Icons.Filled.Cloud,
        title = "Remote backends",
        description = "Connect SFTP, SMB and cloud storage and browse them like local folders.",
    ),
    ProBenefit(
        icon = Icons.Filled.SmartToy,
        title = "AI assistant",
        description = "Use the configured AI integration for supported assistant workflows.",
    ),
    ProBenefit(
        icon = Icons.Filled.ViewColumn,
        title = "Dual-pane browsing",
        description = "Move and compare files side by side with the dual-pane layout.",
    ),
    ProBenefit(
        icon = Icons.Filled.CleaningServices,
        title = "Advanced cleanup",
        description = "Reclaim space with deep duplicate and junk analysis.",
    ),
    ProBenefit(
        icon = Icons.Filled.Palette,
        title = "Personalization",
        description = "Custom accent colors, AMOLED-black and dynamic theming.",
    ),
)

/**
 * Honest Jupiter Pro paywall.
 *
 * Lists the Pro benefits and offers a "Go Pro" action that — because no Play Billing
 * product is configured yet — surfaces a coming-soon / restore state rather than
 * charging the user. A note makes clear that every feature is currently free, and a
 * developer-only toggle can flip the unlocked state to exercise entitlement gating.
 * This screen never claims that a purchase has happened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
) {
    val viewModel: BillingViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "Jupiter Pro") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PaywallHeader()

            EverythingFreeNote()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProBenefits.forEach { benefit ->
                    BenefitRow(benefit = benefit)
                }
            }

            GoProCard(onGoPro = { /* No billing product configured; coming soon. */ })

            DeveloperToggleCard(
                proUnlocked = uiState.proUnlocked,
                onToggle = viewModel::setProUnlocked,
            )
        }
    }
}

@Composable
private fun PaywallHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JupiterWordmark()
        JupiterIconBadge(icon = Icons.Filled.WorkspacePremium, size = 72.dp)
        Text(
            text = "Jupiter Pro preview",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Paid plans are not available yet; current features remain free.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EverythingFreeNote() {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(JupiterDesign.CompactPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = "Good news: every feature below is currently free for everyone. " +
                    "Pro is how you'll support Jupiter once paid plans arrive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun BenefitRow(benefit: ProBenefit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(icon = benefit.icon)
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = benefit.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = benefit.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GoProCard(onGoPro: () -> Unit) {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onGoPro,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Go Pro — coming soon")
            }
            OutlinedButton(
                onClick = onGoPro,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Restore purchase")
            }
            Text(
                text = "In-app purchases aren't available yet, so nothing will be charged. " +
                    "We'll let you know when Pro plans go live.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeveloperToggleCard(
    proUnlocked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(JupiterDesign.CompactPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Developer: Pro unlocked",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Toggle the entitlement locally to test gating. " +
                        "This is not a real purchase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Switch(
                checked = proUnlocked,
                onCheckedChange = onToggle,
            )
        }
    }
}
