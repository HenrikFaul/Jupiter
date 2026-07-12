package com.jupiter.filemanager.feature.cloud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteConnection
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Network / NAS connections screen.
 *
 * Lets the user define and manage real connections to local-network and remote
 * hosts (SMB, NAS, SFTP, FTP, FTPS, WebDAV, NFS). Adding a connection now runs a
 * live reachability test against the chosen protocol backend before the
 * definition is persisted; passwords are stored encrypted by the repository and
 * never inside the connection entry. Tapping a saved connection opens its remote
 * file browser via [onOpenRemote].
 *
 * Connections are grouped into a "Local Network" section (LAN-style protocols:
 * SMB / NFS / NAS) and a "Remote" section (internet-style protocols:
 * SFTP / FTP / FTPS / WebDAV).
 *
 * @param onOpenRemote invoked with a connection id when the user taps a saved
 *   connection to browse its files.
 * @param onBack invoked to navigate back.
 */
@Composable
fun NasConnectionsScreen(
    onOpenRemote: (connectionId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: NasConnectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    onOpen = onOpenRemote,
                    onRemove = viewModel::onRemoveConnection,
                    onAdd = viewModel::onAddRequested,
                )
            }
        }
    }

    if (state.showAddDialog) {
        AddConnectionDialog(
            isTesting = state.isTesting,
            errorMessage = state.testError,
            onDismiss = viewModel::onDismissAddDialog,
            onConfirm = { displayName, type, host, port, username, password, basePath ->
                viewModel.addConnection(
                    displayName = displayName,
                    type = type,
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    basePath = basePath,
                )
            },
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
    onOpen: (String) -> Unit,
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
                ConnectionCard(
                    connection = connection,
                    onOpen = { onOpen(connection.id) },
                    onRemove = { onRemove(connection.id) },
                )
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
                ConnectionCard(
                    connection = connection,
                    onOpen = { onOpen(connection.id) },
                    onRemove = { onRemove(connection.id) },
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
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(JupiterDesign.CompactPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    JupiterCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        contentPadding = PaddingValues(JupiterDesign.CompactPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JupiterIconBadge(icon = iconForType(connection.type))
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

private fun subtitleFor(connection: RemoteConnection): String {
    val protocol = connection.type.name
    val user = connection.username
    val hostPart = if (connection.port > 0) {
        "${connection.host}:${connection.port}"
    } else {
        connection.host
    }
    return if (user.isNullOrBlank()) {
        "$protocol · $hostPart"
    } else {
        "$protocol · $user@$hostPart"
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
)

/** Label used for the share/base-path field, tailored per protocol. */
private fun basePathLabelFor(type: ConnectionType): String = when (type) {
    ConnectionType.SMB, ConnectionType.NAS -> "Share name"
    ConnectionType.WEBDAV -> "Base path (e.g. /dav)"
    else -> "Base path (optional)"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddConnectionDialog(
    isTesting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (
        displayName: String,
        type: ConnectionType,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        basePath: String?,
    ) -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var basePath by rememberSaveable { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ConnectionType.SMB) }

    val canSubmit = displayName.isNotBlank() && host.isNotBlank() && !isTesting

    AlertDialog(
        onDismissRequest = { if (!isTesting) onDismiss() },
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
                            enabled = !isTesting,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host or IP address") },
                    placeholder = { Text("e.g. 192.168.1.10") },
                    singleLine = true,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { input ->
                        portText = input.filter { it.isDigit() }.take(5)
                    },
                    label = { Text("Port (blank = default)") },
                    singleLine = true,
                    enabled = !isTesting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional)") },
                    singleLine = true,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    singleLine = true,
                    enabled = !isTesting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = basePath,
                    onValueChange = { basePath = it },
                    label = { Text(basePathLabelFor(selectedType)) },
                    singleLine = true,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (isTesting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Testing connection…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        displayName,
                        selectedType,
                        host,
                        portText.toIntOrNull() ?: 0,
                        username.ifBlank { null },
                        password.ifBlank { null },
                        basePath.ifBlank { null },
                    )
                },
                enabled = canSubmit,
            ) {
                Text("Test & save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isTesting,
            ) {
                Text("Cancel")
            }
        },
    )
}
