package com.jupiter.filemanager.feature.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Wi-Fi transfer guidance screen.
 *
 * Pure-UI screen (no ViewModel) that helps the user understand how a future
 * "transfer over the local network" feature will work. It surfaces the device's
 * current LAN (IPv4) address so the user can see where a companion server would
 * be reachable, exposes a read-only, copyable URL field, and renders a QR-code
 * placeholder.
 *
 * Honesty: the desktop companion HTTP server that would actually serve files is
 * NOT implemented yet. Nothing here starts a server or transfers data — the URL
 * is illustrative guidance and is clearly labelled as a future capability. The
 * LAN address itself is real (read from [NetworkInterface]) so the guidance is
 * accurate for the current device.
 *
 * Reading network interfaces is performed off the main thread inside a
 * [LaunchedEffect] dispatched to [Dispatchers.IO].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiTransferScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // null = still resolving; "" never used — absence of address is represented by null.
    var lanAddress by remember { mutableStateOf<String?>(null) }
    var resolved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lanAddress = withContext(Dispatchers.IO) { findLanIpv4Address() }
        resolved = true
    }

    // The port is illustrative: it documents the intended companion-server endpoint.
    val previewPort = 8088
    val shareUrl = lanAddress?.let { "http://$it:$previewPort" } ?: "http://0.0.0.0:$previewPort"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Wi-Fi Transfer") },
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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeaderBadge()

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Transfer over Wi-Fi",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Open the address below in a browser on a computer that's on the " +
                    "same Wi-Fi network to browse and copy files. Both devices must share " +
                    "the same network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // QR placeholder — encodes nothing yet; clearly framed as a preview.
            QrPlaceholder()

            Spacer(modifier = Modifier.height(20.dp))

            AddressCard(
                resolved = resolved,
                lanAddress = lanAddress,
                shareUrl = shareUrl,
                onCopy = { copyToClipboard(context, shareUrl) },
            )

            Spacer(modifier = Modifier.height(20.dp))

            ComingSoonNote()
        }
    }
}

/**
 * Brand badge: a tinted circular surface with a Wi-Fi glyph.
 */
@Composable
private fun HeaderBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * A square QR-code placeholder. It does not encode a live endpoint — the
 * companion server is not running — so it is intentionally rendered as a
 * labelled preview rather than a scannable code.
 */
@Composable
private fun QrPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(180.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "QR pairing preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Card showing the current LAN address and a read-only, copyable URL.
 *
 * Three states:
 *  - resolving (still reading interfaces)
 *  - no Wi-Fi address available (likely offline / not on Wi-Fi)
 *  - address found
 */
@Composable
private fun AddressCard(
    resolved: Boolean,
    lanAddress: String?,
    shareUrl: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = 16.dp, vertical = 16.dp)),
        ) {
            InfoRow(
                icon = Icons.Filled.Router,
                label = "This device",
                value = when {
                    !resolved -> "Checking network…"
                    lanAddress != null -> lanAddress
                    else -> "Not connected to Wi-Fi"
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Companion URL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Read-only URL field with an inline copy action.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = shareUrl,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp),
                    )
                    IconButton(
                        onClick = onCopy,
                        enabled = lanAddress != null,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy URL",
                            tint = if (lanAddress != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            if (resolved && lanAddress == null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect to a Wi-Fi network to get a usable address.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCopy,
                enabled = lanAddress != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Copy address")
            }
        }
    }
}

/**
 * A leading-icon / label / value row used inside [AddressCard].
 */
@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp).clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Honest "coming soon" note clarifying that the desktop companion server that
 * would host these transfers is a planned, not-yet-available capability.
 */
@Composable
private fun ComingSoonNote(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Companion server coming soon",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "The on-device web server that serves your files to a desktop " +
                        "browser isn't running yet. The address above is real and shown so " +
                        "you can confirm your network, but opening it won't connect until " +
                        "this feature ships in a future update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * Copies [text] to the system clipboard and shows a short confirmation toast.
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Wi-Fi transfer URL", text))
    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
}

/**
 * Returns the device's current site-local IPv4 LAN address (e.g. "192.168.x.x"),
 * or null if none can be determined (e.g. not connected to Wi-Fi). Loopback and
 * down interfaces are skipped; the first non-loopback site-local IPv4 address is
 * returned, preferring it over other reachable IPv4 addresses.
 *
 * Must be called off the main thread — performs a synchronous interface scan.
 */
private fun findLanIpv4Address(): String? {
    return try {
        var fallback: String? = null
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            for (address in networkInterface.inetAddresses) {
                if (address.isLoopbackAddress) continue
                if (address !is Inet4Address) continue
                val host = address.hostAddress ?: continue
                if (address.isSiteLocalAddress) {
                    return host
                }
                if (fallback == null) {
                    fallback = host
                }
            }
        }
        fallback
    } catch (_: Exception) {
        null
    }
}
