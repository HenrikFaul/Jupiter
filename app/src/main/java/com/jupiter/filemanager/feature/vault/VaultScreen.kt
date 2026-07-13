package com.jupiter.filemanager.feature.vault

import android.content.Context
import android.content.ContextWrapper
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterFileBadge
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Encrypted Vault with mandatory runtime authentication.
 *
 * System biometric/device-credential success and the salted local PIN verifier are the only two
 * paths that can start a session. Leaving the screen or moving the activity to the background
 * locks immediately; the persisted inactivity window is enforced by [VaultViewModel]. SAF import,
 * real Downloads export and named permanent-delete confirmation remain available only while an
 * authenticated session is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findFragmentActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<FileItem?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // The ViewModel accepts this callback only after beginDocumentPicker(), keeps a returned
        // URI in memory, and requires fresh authentication before the repository can read it.
        viewModel.onDocumentPickerResult(uri)
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onHostStarted()
                Lifecycle.Event.ON_STOP -> viewModel.onHostStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onHostStarted()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // A configuration recreation disposes this composition while Android retains/redelivers
            // the Activity Result registration. Keep only the non-sensitive picker launch marker in
            // that case; every normal navigation/disposal remains a hard lock boundary.
            if (hostActivity?.isChangingConfigurations != true) viewModel.lock()
        }
    }

    LaunchedEffect(uiState.error, uiState.infoMessage) {
        val message = uiState.error ?: uiState.infoMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            if (uiState.infoMessage != null) viewModel.dismissInfoMessage()
        }
    }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) {
            showPinDialog = false
            pendingDelete = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Private Vault", fontWeight = FontWeight.SemiBold)
                        if (uiState.isUnlocked) {
                            Text(
                                text = "Auto-locks after ${uiState.autoLockMinutes} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.lock()
                            onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back and lock Vault",
                        )
                    }
                },
                actions = {
                    if (uiState.isUnlocked) {
                        IconButton(onClick = viewModel::lock) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Lock Vault now",
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.isUnlocked && !uiState.isLoading) {
                FloatingActionButton(
                    onClick = {
                        if (viewModel.beginDocumentPicker()) {
                            runCatching { documentPicker.launch(arrayOf("*/*")) }
                                .onFailure {
                                    viewModel.onDocumentPickerResult(null)
                                    viewModel.reportError("Android's document picker is unavailable")
                                }
                        }
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Import file")
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(viewModel, uiState.isUnlocked) {
                    if (uiState.isUnlocked) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            viewModel.recordUserInteraction()
                        }
                    }
                },
        ) {
            when {
                !uiState.isUnlocked -> LockedContent(
                    policyLoaded = uiState.securityPolicyLoaded,
                    pinConfigured = uiState.pinConfigured,
                    deviceCredentialEnabled = uiState.deviceCredentialUnlockEnabled,
                    isAuthenticating = uiState.isAuthenticating,
                    authenticationError = uiState.authenticationError,
                    pendingImportAwaitingAuthentication =
                        uiState.pendingImportAwaitingAuthentication,
                    onDeviceCredentialRequested = {
                        viewModel.dismissAuthenticationError()
                        if (viewModel.beginDeviceAuthentication()) {
                            authenticateThenUnlock(
                                context = context,
                                onAuthenticated = viewModel::onDeviceAuthenticationSucceeded,
                                onAuthenticationTerminated = viewModel::onDeviceAuthenticationFailed,
                            )
                        }
                    },
                    onPinRequested = {
                        viewModel.dismissAuthenticationError()
                        showPinDialog = true
                    },
                    onCancelPendingImport = viewModel::cancelPendingImport,
                    modifier = Modifier.fillMaxSize(),
                )

                uiState.isLoading && uiState.items.isEmpty() ->
                    LoadingView(modifier = Modifier.fillMaxSize())

                uiState.error != null && uiState.items.isEmpty() -> ErrorView(
                    message = uiState.error ?: "Something went wrong.",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                )

                uiState.items.isEmpty() -> EmptyVaultContent(modifier = Modifier.fillMaxSize())

                else -> VaultList(
                    items = uiState.items,
                    isBusy = uiState.isLoading,
                    onExport = { item ->
                        val destination = existingPublicDownloadsDirectory()
                        if (destination == null) {
                            viewModel.reportError("Downloads folder is unavailable")
                        } else {
                            viewModel.exportItem(item, destination)
                        }
                    },
                    onDelete = { item ->
                        viewModel.recordUserInteraction()
                        pendingDelete = item
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showPinDialog && !uiState.isUnlocked) {
        VaultPinDialog(
            isVerifying = uiState.isAuthenticating,
            error = uiState.authenticationError,
            onDismiss = {
                if (!uiState.isAuthenticating) {
                    showPinDialog = false
                    viewModel.dismissAuthenticationError()
                }
            },
            onSubmit = viewModel::verifyVaultPin,
        )
    }

    val deleteTarget = pendingDelete
    if (deleteTarget != null && uiState.isUnlocked) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isLoading) pendingDelete = null
            },
            title = { Text(text = "Delete \"${deleteTarget.name}\" permanently?") },
            text = {
                Text(
                    text = "This permanently removes ${deleteTarget.name} from the encrypted " +
                        "Vault. This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isLoading,
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteItem(deleteTarget)
                    },
                ) {
                    Text(text = "Delete permanently", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isLoading,
                    onClick = { pendingDelete = null },
                ) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

/** Returns the real public Downloads path only when it already exists as a directory. */
private fun existingPublicDownloadsDirectory(): String? {
    @Suppress("DEPRECATION")
    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return directory.takeIf { it.exists() && it.isDirectory }?.absolutePath
}

@Composable
private fun LockedContent(
    policyLoaded: Boolean,
    pinConfigured: Boolean,
    deviceCredentialEnabled: Boolean,
    isAuthenticating: Boolean,
    authenticationError: String?,
    pendingImportAwaitingAuthentication: Boolean,
    onDeviceCredentialRequested: () -> Unit,
    onPinRequested: () -> Unit,
    onCancelPendingImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        JupiterCard(
            modifier = Modifier.fillMaxWidth(),
            shape = JupiterDesign.HeroCardShape,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 30.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Vault is locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            Text(
                text = if (pendingImportAwaitingAuthentication) {
                    "Document selected. Authenticate again before Jupiscan reads and encrypts it."
                } else {
                    "Encrypted files stay private until your configured credential is verified. " +
                        "Jupiscan never unlocks this screen on a normal button press."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            if (!policyLoaded) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp),
                )
                Text(
                    text = "Loading security policy…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
                )
            } else {
                if (deviceCredentialEnabled) {
                    Button(
                        onClick = onDeviceCredentialRequested,
                        enabled = !isAuthenticating,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Use device security",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                if (pinConfigured) {
                    OutlinedButton(
                        onClick = onPinRequested,
                        enabled = !isAuthenticating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (deviceCredentialEnabled) 10.dp else 24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Password,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(text = "Use Vault PIN", modifier = Modifier.padding(start = 8.dp))
                    }
                }

                if (isAuthenticating) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(text = "Verifying…", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!authenticationError.isNullOrBlank()) {
                    Text(
                        text = authenticationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }

                if (pendingImportAwaitingAuthentication && !isAuthenticating) {
                    TextButton(
                        onClick = onCancelPendingImport,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
                    ) {
                        Text(text = "Cancel pending import")
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultPinDialog(
    isVerifying: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) pin = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Enter Vault PIN") },
        text = {
            Column {
                Text(
                    text = "Enter the 4–12 digit PIN configured in Settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        pin = value.filter(Char::isDigit).take(MAX_VAULT_PIN_LENGTH)
                    },
                    enabled = !isVerifying,
                    singleLine = true,
                    label = { Text(text = "Vault PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = !error.isNullOrBlank(),
                    supportingText = error?.takeIf(String::isNotBlank)?.let { message ->
                        { Text(text = message) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length in MIN_VAULT_PIN_LENGTH..MAX_VAULT_PIN_LENGTH && !isVerifying,
                onClick = {
                    val transientPin = pin.toCharArray()
                    try {
                        onSubmit(transientPin)
                    } finally {
                        transientPin.fill('\u0000')
                        pin = ""
                    }
                },
            ) {
                Text(text = if (isVerifying) "Verifying…" else "Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isVerifying) { Text(text = "Cancel") }
        },
    )
}

@Composable
private fun EmptyVaultContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        JupiterCard(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Outlined.FolderOff,
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Vault is empty",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Text(
                text = "Use + to choose a document. It is imported through Android's secure " +
                    "document picker and encrypted on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun VaultList(
    items: List<FileItem>,
    isBusy: Boolean,
    onExport: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Encrypted on this device",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${items.size} private ${if (items.size == 1) "item" else "items"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
        items(items = items, key = { it.path }) { item ->
            VaultRow(
                item = item,
                isBusy = isBusy,
                onExport = { onExport(item) },
                onDelete = { onDelete(item) },
            )
        }
        item { Spacer(modifier = Modifier.height(84.dp)) }
    }
}

@Composable
private fun VaultRow(
    item: FileItem,
    isBusy: Boolean,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JupiterCard(
        modifier = modifier.fillMaxWidth(),
        shape = JupiterDesign.CompactCardShape,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JupiterFileBadge(item = item, size = 48.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            IconButton(onClick = onExport, enabled = !isBusy) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Export ${item.name} to Downloads",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete, enabled = !isBusy) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete ${item.name} from Vault permanently",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Requires a successful system biometric/device-credential callback. Every terminal failure is
 * returned to the ViewModel, which invalidates the in-flight attempt and keeps the Vault locked.
 */
private fun authenticateThenUnlock(
    context: Context,
    onAuthenticated: () -> Unit,
    onAuthenticationTerminated: (String?) -> Unit,
) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        onAuthenticationTerminated(
            "Secure authentication is unavailable on this screen. Vault stays locked.",
        )
        return
    }

    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(context).canAuthenticate(authenticators) !=
        BiometricManager.BIOMETRIC_SUCCESS
    ) {
        onAuthenticationTerminated(
            "No device lock is set up. Add a screen lock to use the Vault.",
        )
        return
    }

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onAuthenticationTerminated(errString.toString())
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
            onAuthenticationTerminated("Unable to start authentication. Vault stays locked.")
        }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

private const val MIN_VAULT_PIN_LENGTH = 4
private const val MAX_VAULT_PIN_LENGTH = 12
