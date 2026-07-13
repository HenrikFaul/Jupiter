package com.jupiter.filemanager.feature.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.theme.JupiterDesign
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Jupiscan Relay screen.
 *
 * Starts and stops a real, pairing-gated local server
 * ([com.jupiter.filemanager.data.transfer.WifiTransferServer], managed by
 * [WifiTransferViewModel]) that serves the public Downloads directory over the local network.
 * While running it shows a real QR/link containing an in-memory session token.
 *
 * Honest constraint: the desktop/browser opening the URL and this phone must be on
 * the same Wi-Fi/LAN — this is surfaced explicitly in the UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiTransferScreen(
    onBack: () -> Unit,
    viewModel: WifiTransferViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "Jupiscan Relay") },
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
            HeaderBadge(running = uiState.isRunning)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Jupiscan Relay",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Start a short-lived pairing session, then scan this QR code on a computer " +
                    "on the same Wi-Fi network. Only paired browsers can view your Downloads.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            StartStopButton(
                isRunning = uiState.isRunning,
                onStart = viewModel::start,
                onStop = viewModel::stop,
            )

            Spacer(modifier = Modifier.height(20.dp))

            PairingCard(
                isRunning = uiState.isRunning,
                pairingUrl = uiState.pairingUrl,
                pairingSessionId = uiState.pairingSessionId,
                error = uiState.error,
                onCopy = { uiState.pairingUrl?.let { copyToClipboard(context, it) } },
            )

            Spacer(modifier = Modifier.height(20.dp))

            TrustedNetworkNote()
        }
    }
}

/**
 * Brand badge: a tinted circular surface with a Wi-Fi glyph that reflects the
 * running state.
 */
@Composable
private fun HeaderBadge(running: Boolean, modifier: Modifier = Modifier) {
    JupiterIconBadge(
        icon = if (running) Icons.Filled.Wifi else Icons.Filled.WifiOff,
        tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        size = 96.dp,
    )
}

/**
 * Primary Start / Stop control. Starts or stops the explicit pairing session.
 */
@Composable
private fun StartStopButton(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { if (isRunning) onStop() else onStart() },
        modifier = modifier.fillMaxWidth(),
        colors = if (isRunning) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = if (isRunning) "Stop Relay" else "Start Relay")
    }
}

/**
 * Card showing a real QR pairing code and the copyable one-time URL. The QR is generated locally
 * from the current session only; no file path or transfer metadata leaves the device to create it.
 */
@Composable
private fun PairingCard(
    isRunning: Boolean,
    pairingUrl: String?,
    pairingSessionId: String?,
    error: String?,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JupiterCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(JupiterDesign.CompactPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            InfoRow(
                icon = Icons.Filled.Router,
                label = "Relay",
                value = when {
                    isRunning -> "Paired session running"
                    error != null -> "Unavailable"
                    else -> "Stopped"
                },
            )

            if (pairingUrl != null) {
                Spacer(modifier = Modifier.height(16.dp))
                RelayQrCode(pairingUrl = pairingUrl)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Session ${pairingSessionId ?: ""} · expires automatically in 10 minutes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pairing link",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val displayUrl = pairingUrl ?: "Start Relay to create a one-time pairing link"

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
                        text = displayUrl,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = if (pairingUrl != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp),
                    )
                    IconButton(
                        onClick = onCopy,
                        enabled = pairingUrl != null,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy pairing link",
                            tint = if (pairingUrl != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            if (error != null) {
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
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelayQrCode(pairingUrl: String, modifier: Modifier = Modifier) {
    val qr = remember(pairingUrl) { createQrBitmap(pairingUrl) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "QR code for the current Jupiscan Relay pairing session",
                    modifier = Modifier.size(220.dp),
                )
            } else {
                Text(
                    text = "The pairing link is ready. Use Copy link if the QR preview is unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scan on your computer to pair",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun createQrBitmap(value: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        value,
        BarcodeFormat.QR_CODE,
        QR_SIZE_PX,
        QR_SIZE_PX,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    Bitmap.createBitmap(QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until QR_SIZE_PX) {
            for (y in 0 until QR_SIZE_PX) {
                bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF101318.toInt() else 0xFFFFFFFF.toInt())
            }
        }
    }
}.getOrNull()

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
        JupiterIconBadge(icon = icon, size = 40.dp)
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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Honest note clarifying the local-network trust boundary. Pairing prevents casual LAN access;
 * this browser-based transport is not represented as end-to-end encrypted.
 */
@Composable
private fun TrustedNetworkNote(modifier: Modifier = Modifier) {
    JupiterCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(JupiterDesign.CompactPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Use a trusted local network",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "The computer and phone must share the same Wi-Fi / LAN. A new " +
                        "random pairing token is required for every session and expires after " +
                        "10 minutes. Stop Relay when finished; do not start it on an untrusted " +
                        "public Wi-Fi network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    clipboard?.setPrimaryClip(ClipData.newPlainText("Jupiscan Relay pairing link", text))
    Toast.makeText(context, "Pairing link copied", Toast.LENGTH_SHORT).show()
}

private const val QR_SIZE_PX = 320
