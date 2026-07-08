package com.jupiter.filemanager.feature.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.CloudDone
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.domain.model.CloudAccount
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.StorageBar

/**
 * Cloud Hub screen. Lists the user's linked cloud storage accounts
 * (Google Drive, Dropbox, OneDrive, iCloud, Box, WebDAV) and lets them add a new
 * link entry via a bottom sheet.
 *
 * No live provider authentication or quota backend is wired yet, so accounts are
 * surfaced honestly as "Not connected" with a "Connect" affordance that is not
 * yet functional ("Coming soon"). Account link entries themselves are persisted
 * so they survive process death.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudHubScreen(
    onBack: () -> Unit,
    viewModel: CloudHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val snackbarHostState = remember { SnackbarHostState() }

    // The account whose connect flow is awaiting consent; remembered so the
    // consent launcher callback can resume the flow with the right account.
    var consentingAccount by remember { mutableStateOf<CloudAccount?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val pending = consentingAccount
        consentingAccount = null
        if (pending != null && activity != null) {
            viewModel.onConsentResult(activity, result.data, pending)
        }
    }

    // When the VM exposes a pending consent intent, launch it exactly once.
    LaunchedEffect(state.pendingConsent) {
        val sender = state.pendingConsent
        if (sender != null) {
            // The connecting account is the one mid-flight.
            consentingAccount = state.accounts.firstOrNull {
                it.id == state.connectingAccountId
            }
            viewModel.onConsentLaunched()
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // Surface transient errors via the snackbar, then clear them.
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Cloud Hub") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAddRequested) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add cloud account",
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> LoadingView()

                state.isEmpty -> EmptyView(
                    title = "No cloud accounts",
                    message = "Link a cloud account to keep it within reach. " +
                        "Provider sign-in and sync are coming soon.",
                    icon = Icons.Outlined.CloudQueue,
                )

                else -> CloudAccountList(
                    accounts = state.accounts,
                    connectingAccountId = state.connectingAccountId,
                    onRemove = viewModel::onRemoveAccount,
                    onConnect = { account ->
                        if (activity != null) viewModel.connect(activity, account)
                    },
                )
            }
        }
    }

    if (state.showAddSheet) {
        AddCloudAccountSheet(
            onDismiss = viewModel::onDismissAddSheet,
            onConfirm = viewModel::onAddAccount,
        )
    }
}

/** Walks the [ContextWrapper] chain to find the hosting [Activity], if any. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun CloudAccountList(
    accounts: List<CloudAccount>,
    connectingAccountId: String?,
    onRemove: (String) -> Unit,
    onConnect: (CloudAccount) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = accounts, key = { it.id }) { account ->
            CloudAccountCard(
                account = account,
                isConnecting = account.id == connectingAccountId,
                onRemove = { onRemove(account.id) },
                onConnect = { onConnect(account) },
            )
        }
    }
}

@Composable
private fun CloudAccountCard(
    account: CloudAccount,
    isConnecting: Boolean,
    onRemove: () -> Unit,
    onConnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = providerLabel(account.provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.LinkOff,
                        contentDescription = "Remove account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            if (account.isConnected) {
                // Authenticated account: surface the real email and, when a
                // quota limit is known, the usage via the shared StorageBar.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = account.accountEmail ?: "Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (account.totalBytes > 0L) {
                    Spacer(modifier = Modifier.size(12.dp))
                    StorageBar(
                        label = formatBytes(account.usedBytes) +
                            " of " + formatBytes(account.totalBytes),
                        usedBytes = account.usedBytes,
                        totalBytes = account.totalBytes,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Not connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (account.provider == CloudProvider.GOOGLE_DRIVE) {
                        // Live Google sign-in: each user authenticates with
                        // their own account via the system chooser.
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(onClick = onConnect) {
                                Text(text = "Connect")
                            }
                        }
                    } else {
                        // Other providers have no auth backend yet.
                        TextButton(onClick = {}, enabled = false) {
                            Text(text = "Connect (coming soon)")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCloudAccountSheet(
    onDismiss: () -> Unit,
    onConfirm: (provider: CloudProvider, displayName: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedProvider by remember { mutableStateOf(CloudProvider.GOOGLE_DRIVE) }
    var displayName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "Add cloud account",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "Choose a provider and name this account. Provider sign-in " +
                    "is coming soon.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(20.dp))
            Text(
                text = "Provider",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(8.dp))
            CloudProvider.entries.forEach { provider ->
                ProviderRow(
                    provider = provider,
                    selected = provider == selectedProvider,
                    onSelect = { selectedProvider = provider },
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Account name") },
                placeholder = { Text(providerLabel(selectedProvider)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.size(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val name = displayName.ifBlank { providerLabel(selectedProvider) }
                        onConfirm(selectedProvider, name)
                    },
                ) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: CloudProvider,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .selectable(selected = selected, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onSelect)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = providerLabel(provider),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/** Human-readable label for a [CloudProvider]. */
private fun providerLabel(provider: CloudProvider): String = when (provider) {
    CloudProvider.GOOGLE_DRIVE -> "Google Drive"
    CloudProvider.DROPBOX -> "Dropbox"
    CloudProvider.ONEDRIVE -> "OneDrive"
    CloudProvider.ICLOUD -> "iCloud"
    CloudProvider.BOX -> "Box"
    CloudProvider.WEBDAV -> "WebDAV"
}
