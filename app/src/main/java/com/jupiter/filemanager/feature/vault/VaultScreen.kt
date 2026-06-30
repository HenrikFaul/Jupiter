package com.jupiter.filemanager.feature.vault

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.iconForFile

/**
 * Vault screen.
 *
 * While locked it presents a lock icon and an "Unlock" button. Tapping it requires a
 * successful [BiometricPrompt] authentication (biometric or device credential) before the
 * vault is unlocked. Once unlocked it shows the list of encrypted vault entries with an
 * import affordance (the real file picker is a placeholder for now) and per-item delete.
 *
 * Security note: authentication is mandatory. If the hosting activity is not a
 * [FragmentActivity] (so no [BiometricPrompt] can be attached), the vault stays LOCKED and
 * an error is surfaced rather than granting access without authentication.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val viewModel: VaultViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Vault") },
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
            if (uiState.isUnlocked) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        // Placeholder: a real implementation would launch a system file
                        // picker (ACTION_OPEN_DOCUMENT) and pass the resolved path to
                        // viewModel.importFile(...). Until that picker is wired up we
                        // simply surface a hint so the action is discoverable.
                        Toast.makeText(
                            context,
                            "Pick a file to import (file picker placeholder).",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Import file")
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                !uiState.isUnlocked -> {
                    LockedContent(
                        onUnlockRequested = {
                            authenticateThenUnlock(
                                context = context,
                                onAuthenticated = viewModel::unlock,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                uiState.isLoading && uiState.items.isEmpty() -> {
                    LoadingView(modifier = Modifier.fillMaxSize())
                }

                uiState.error != null && uiState.items.isEmpty() -> {
                    ErrorView(
                        message = uiState.error ?: "Something went wrong.",
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                uiState.items.isEmpty() -> {
                    EmptyVaultContent(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    VaultList(
                        items = uiState.items,
                        onDelete = viewModel::deleteItem,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * Locked state: a prominent lock icon, an explanatory line and an "Unlock" button.
 */
@Composable
private fun LockedContent(
    onUnlockRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(PaddingValues(horizontal = 32.dp, vertical = 24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Vault is locked",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Your private files are encrypted on this device. " +
                "Unlock to view, import, or remove them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onUnlockRequested,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = "Unlock", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * Empty (but unlocked) state.
 */
@Composable
private fun EmptyVaultContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(PaddingValues(horizontal = 32.dp, vertical = 24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Vault is empty",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Use the + button to import a file into the encrypted vault.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Unlocked state: the list of vault entries, each with a delete action.
 */
@Composable
private fun VaultList(
    items: List<FileItem>,
    onDelete: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(items = items, key = { it.path }) { item ->
            VaultRow(item = item, onDelete = { onDelete(item) })
        }
    }
}

/**
 * A single vault entry row showing its icon, name and size, plus a delete button.
 */
@Composable
private fun VaultRow(
    item: FileItem,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Icon(
                imageVector = iconForFile(item),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(text = formatBytes(item.sizeBytes))
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete from vault",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

/**
 * Requires successful authentication before invoking [onAuthenticated].
 *
 * Behavior:
 *  - If the host is not a [FragmentActivity], no prompt can be shown: the vault stays
 *    locked, an error is surfaced, and [onAuthenticated] is NOT invoked.
 *  - On a successful prompt, [onAuthenticated] is invoked.
 *  - On user cancellation, [onAuthenticated] is NOT invoked (the vault stays locked).
 */
private fun authenticateThenUnlock(
    context: Context,
    onAuthenticated: () -> Unit,
) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        // No FragmentActivity host to attach a BiometricPrompt to. We CANNOT
        // authenticate, so the vault MUST stay locked rather than silently
        // granting access. Surface an error and return without unlocking.
        Toast.makeText(
            context,
            "Cannot authenticate: secure unlock is unavailable on this screen. Vault stays locked.",
            Toast.LENGTH_LONG,
        ).show()
        return
    }

    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val canAuthenticate = biometricManager.canAuthenticate(authenticators)
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        // No usable biometric/device-credential method (the device has no secure lock
        // enrolled at all). There is no fallback we can trust, so keep the vault LOCKED
        // instead of granting access without authentication.
        Toast.makeText(
            context,
            "No device lock is set up. Add a screen lock to use the vault. Vault stays locked.",
            Toast.LENGTH_LONG,
        ).show()
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Any authentication error (cancellation, lockout, hardware) keeps the
                // vault LOCKED. We never unlock without a successful authentication.
                Toast.makeText(context, errString, Toast.LENGTH_SHORT).show()
            }
        },
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Vault")
        .setSubtitle("Authenticate to access your private files")
        .setAllowedAuthenticators(authenticators)
        .build()

    runCatching { prompt.authenticate(promptInfo) }
        .onFailure {
            // Failed to even show the prompt: keep the vault locked.
            Toast.makeText(
                context,
                "Unable to start authentication. Vault stays locked.",
                Toast.LENGTH_LONG,
            ).show()
        }
}

/**
 * Walks the [ContextWrapper] chain to find the hosting [FragmentActivity], or null when
 * the context is not ultimately backed by one.
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Small reusable inline progress row kept available for future busy-state composition.
 * Currently the screen relies on full-screen [LoadingView]; this helper exists so an
 * unlocked-but-busy overlay can be added without changing the public API.
 */
@Composable
private fun InlineBusyRow(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Box(modifier = Modifier.width(12.dp))
            Text(
                text = "Working...",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Suppresses the unused-helper warning for the optional [InlineBusyRow]. */
@Suppress("unused")
private val keepInlineBusyRowReferenced: @Composable () -> Unit = { InlineBusyRow() }
