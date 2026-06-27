package com.jupiter.filemanager.feature.cloud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteConnection
import com.jupiter.filemanager.ui.components.LoadingView

/**
 * Network / NAS connections screen.
 *
 * Lets the user define and manage connections to local-network and remote hosts
 * (SMB, NAS, SFTP, FTP, FTPS, WebDAV, NFS). Connection definitions are persisted
 * so they survive process death, but no live protocol I/O backend is wired yet,
 * so every connection is presented honestly as "Offline" with a disabled
 * "Connect (coming soon)" affordance — no fake live reachability is fabricated.
 *
 * Connections are grouped into a "Local Network" section (LAN-style protocols:
 * SMB / NFS / NAS) and a "Remote" section (internet-style protocols:
 * SFTP / FTP / FTPS / WebDAV).
 */
@Composable
fun NasConnectionsScreen(
    onBack: () -> Unit,
    viewModel: NasConnectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network & NAS") },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::onAddRequested,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add connection") },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> LoadingView()
                else -> NasConnectionsContent(
                    connections = state.connections,
                    onRemove = viewModel::onRemoveConnection,
                    onAdd = viewModel::onAddRequested,
                )
            }
        }
    }

    if (state.showAddDialog) {
        AddConnectionDialog(
            onDismiss = viewModel::onDismissAddDialog,
            onConfirm = viewModel::onAddConnection,
        )
    }
}

/** Protocols treated as "Local Network" shares. */
private val LocalTypes = setOf(
    ConnectionType.SMB,
    ConnectionType.NFS,
    ConnectionType.NAS,
)

@Composable
private fun NasConnectionsContent(
    connections: List<RemoteConnection>,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val local = connections.filter { it.type in LocalTypes }
    val remote = connections.filter { it.type !in LocalTypes }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HonestNoticeCard()
        }

        item {
            SectionLabel(
                icon = Icons.Filled.Lan,
                title = "Local Network",
            )
        }
        if (local.isEmpty()) {
            item {
                SectionEmpty(
                    title = "No local shares",
                    message = "Add an SMB, NAS or NFS share on your network to browse it here.",
                    actionLabel = "Add local share",
                    onAction = onAdd,
                )
            }
        } else {
            items(local, key = { it.id }) { connection ->
                ConnectionCard(connection = connection, onRemove = { onRemove(connection.id) })
            }
        }

        item {
            SectionLabel(
                icon = Icons.Filled.Cloud,
                title = "Remote",
            )
        }
        if (remote.isEmpty()) {
            item {
                SectionEmpty(
                    title = "No remote servers",
                    message = "Add an SFTP, FTP, FTPS or WebDAV server to reach files over the internet.",
                    actionLabel = "Add remote server",
                    onAction = onAdd,
                )
            }
        } else {
            items(remote, key = { it.id }) { connection ->
                ConnectionCard(connection = connection, onRemove = { onRemove(connection.id) })
            }
        }
    }
}

@Composable
private fun HonestNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Router,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connections are saved locally",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Live browsing over SMB, SFTP, FTP and WebDAV is coming soon. " +
                        "Saved servers show as offline until then.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(
    icon: ImageVector,
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionEmpty(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAction) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: RemoteConnection,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForType(connection.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connection.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleFor(connection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusChip(isOnline = connection.isOnline)
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove connection",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(isOnline: Boolean) {
    // No live protocol I/O backend exists yet, so connections are honestly
    // surfaced as offline rather than fabricating a reachable state.
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(if (isOnline) "Online" else "Offline · Connect coming soon")
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

private fun subtitleFor(connection: RemoteConnection): String {
    val protocol = connection.type.name
    val user = connection.username
    return if (user.isNullOrBlank()) {
        "$protocol · ${connection.host}"
    } else {
        "$protocol · $user@${connection.host}"
    }
}

private fun iconForType(type: ConnectionType): ImageVector = when (type) {
    ConnectionType.SMB -> Icons.Filled.Folder
    ConnectionType.NFS -> Icons.Filled.Storage
    ConnectionType.NAS -> Icons.Filled.Dns
    ConnectionType.SFTP -> Icons.Filled.Cloud
    ConnectionType.FTP -> Icons.Filled.Cloud
    ConnectionType.FTPS -> Icons.Filled.Cloud
    ConnectionType.WEBDAV -> Icons.Filled.Cloud
}

/** Protocols offered in the add dialog, in a sensible order. */
private val SelectableTypes = listOf(
    ConnectionType.SMB,
    ConnectionType.NAS,
    ConnectionType.SFTP,
    ConnectionType.WEBDAV,
    ConnectionType.FTP,
    ConnectionType.FTPS,
    ConnectionType.NFS,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddConnectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        displayName: String,
        type: ConnectionType,
        host: String,
        username: String,
    ) -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ConnectionType.SMB) }

    val canSubmit = displayName.isNotBlank() && host.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add connection") },
        text = {
            Column {
                Text(
                    text = "Protocol",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SelectableTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.name) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host or IP address") },
                    placeholder = { Text("e.g. 192.168.1.10") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(displayName, selectedType, host, username) },
                enabled = canSubmit,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
