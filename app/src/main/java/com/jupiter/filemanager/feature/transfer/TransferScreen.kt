package com.jupiter.filemanager.feature.transfer

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.NearbyError
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Transfer screen.
 *
 * Placeholder for an upcoming peer-to-peer transfer feature (Wi-Fi Direct / nearby
 * device transfer). It is a pure-UI screen with no ViewModel: it simply communicates
 * what the feature will do and presents a disabled-looking call to action so the entry
 * point is discoverable without implying the capability is live yet.
 *
 * The "Coming soon" CTA is intentionally non-functional (enabled = false). When the
 * transport layer lands, wire the button's onClick to start device discovery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Transfer") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroIcon()

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Nearby transfer is coming soon",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Send and receive files directly between devices over Wi-Fi " +
                    "Direct and Bluetooth — no internet connection or cables required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            FeatureCard(
                icon = Icons.Filled.Wifi,
                title = "Fast Wi-Fi Direct",
                description = "Transfer large files at local-network speeds without using " +
                    "your mobile data.",
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureCard(
                icon = Icons.Filled.Bluetooth,
                title = "Bluetooth fallback",
                description = "Pair with nearby devices for quick, low-power transfers when " +
                    "Wi-Fi Direct is unavailable.",
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureCard(
                icon = Icons.Filled.QrCode2,
                title = "Scan to connect",
                description = "Scan a QR code to pair instantly and start sending in seconds.",
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Disabled call to action: the entry point is visible but intentionally
            // inert until the transfer transport is implemented.
            Button(
                onClick = { /* Placeholder: discovery will start here once implemented. */ },
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Start a transfer")
            }

            Spacer(modifier = Modifier.height(12.dp))

            AssistChip(
                onClick = { /* No-op: status indicator only. */ },
                enabled = false,
                label = { Text(text = "Coming soon") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.NearbyError,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/**
 * Large rounded hero badge holding the primary transfer glyph.
 */
@Composable
private fun HeroIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(112.dp)
            .clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * A single feature highlight card with a leading icon, title and description.
 */
@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = 16.dp, vertical = 16.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
