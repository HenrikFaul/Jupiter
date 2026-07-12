package com.jupiter.filemanager.feature.transfer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.DevicesOther
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterWordmark
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Nearby transfer screen.
 *
 * Pure-UI screen (no ViewModel) that presents an honest preview of nearby transfer.
 * The transport layer for peer-to-peer transfers (Wi-Fi Direct / Bluetooth)
 * is not yet implemented, so the radar animation is decorative and the
 * device list stays empty with a clear, non-fabricated empty state. A "How it works"
 * note explains the intended flow without implying the capability is live.
 *
 * When the transport is built, replace the empty placeholder with discovered devices and
 * wire device taps to initiate a transfer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyTransferScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "Nearby Transfer") },
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
            JupiterWordmark()

            Spacer(modifier = Modifier.height(18.dp))

            ScanningRadar()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Nearby transfer preview",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Device discovery and the peer-to-peer transport are not available " +
                    "in this build yet. This screen previews the intended private transfer flow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Honest empty state: no fake devices are shown. Discovery requires a
            // transport layer that is not yet implemented.
            NoDevicesCard()

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "How it works",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            HowItWorksStep(
                icon = Icons.Filled.Wifi,
                title = "Direct connection",
                description = "Devices connect peer-to-peer over Wi-Fi Direct, so transfers " +
                    "stay fast and never touch your mobile data.",
            )

            Spacer(modifier = Modifier.height(12.dp))

            HowItWorksStep(
                icon = Icons.Filled.Bluetooth,
                title = "Bluetooth fallback",
                description = "When Wi-Fi Direct is unavailable, nearby devices can pair over " +
                    "Bluetooth for quick, low-power transfers.",
            )

            Spacer(modifier = Modifier.height(12.dp))

            HowItWorksStep(
                icon = Icons.Filled.QrCode2,
                title = "Scan to pair",
                description = "Scan a QR code on the other device to connect instantly without " +
                    "manual setup.",
            )
        }
    }
}

/**
 * Decorative radar badge: a pulsing ring behind a steady device glyph. The nearby
 * transport is not active; the surrounding copy states that explicitly.
 */
@Composable
private fun ScanningRadar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulseScale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Expanding pulse ring.
        Surface(
            modifier = Modifier
                .size(112.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {}

        // Steady center badge.
        Surface(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.PhonelinkRing,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Empty-state card shown while no devices have been discovered. This is intentionally
 * honest: it does not fabricate device entries.
 */
@Composable
private fun NoDevicesCard(modifier: Modifier = Modifier) {
    JupiterCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.DevicesOther,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Device discovery isn't available yet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This build does not scan for or list nearby devices. The list will " +
                    "become active only after the peer-to-peer transport is implemented.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A single "How it works" row with a leading icon, title and supporting description.
 */
@Composable
private fun HowItWorksStep(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    JupiterCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(JupiterDesign.CompactPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JupiterIconBadge(icon = icon)
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
