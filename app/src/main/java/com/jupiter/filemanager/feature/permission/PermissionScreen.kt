package com.jupiter.filemanager.feature.permission

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jupiter.filemanager.data.permission.StorageAccessManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Onboarding screen that explains why Jupiter needs broad storage access and lets
 * the user grant it.
 *
 * On Android R+ the primary action launches the system "All files access" settings
 * screen via [androidx.compose.runtime.LaunchedEffect]-free intent launch; on
 * pre-R devices it requests the legacy READ/WRITE external-storage runtime
 * permissions through an activity-result launcher.
 *
 * Access is re-checked on every [Lifecycle.Event.ON_RESUME] (so returning from the
 * settings page is detected) and [onGranted] is invoked as soon as full access is
 * available — including immediately, via a [LaunchedEffect], if it was already
 * granted when the screen first appears.
 */
@Composable
fun PermissionScreen(
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // A StorageAccessManager only wraps the application context; constructing one
    // here (remembered for the screen's lifetime) lets us build the settings
    // intent / legacy permission list without coupling to the ViewModel's
    // internals. Detection of the actual access state still flows through the
    // ViewModel's uiState.
    val accessManager = remember(context) {
        StorageAccessManager(context.applicationContext)
    }

    // Legacy runtime-permission launcher (pre-R). After the user responds we ask
    // the ViewModel to re-evaluate the access state.
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        viewModel.refresh()
    }

    // Re-check access whenever the screen resumes (e.g. returning from Settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Forward to the caller as soon as full access is granted (covers both the
    // already-granted case on first composition and the post-resume update).
    LaunchedEffect(uiState.hasAccess) {
        if (uiState.hasAccess) {
            onGranted()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 28.dp, vertical = 32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Storage access needed",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Jupiter is a file manager, so it needs access to all files on " +
                "your device to browse, organize, move and clean up your storage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(28.dp))

        RationaleCard(
            icon = Icons.Outlined.Folder,
            title = "Browse everything",
            message = "See and manage files across internal storage and SD cards.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        RationaleCard(
            icon = Icons.Outlined.Search,
            title = "Search & clean up",
            message = "Find large files and duplicates to reclaim space.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        RationaleCard(
            icon = Icons.Outlined.Lock,
            title = "Stays on device",
            message = "Your files are never uploaded. Everything happens locally.",
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.startActivity(accessManager.manageAllFilesSettingsIntent())
                } else {
                    val permissions = accessManager.legacyPermissions
                    if (permissions.isNotEmpty()) {
                        legacyPermissionLauncher.launch(permissions.toTypedArray())
                    } else {
                        // Nothing to request; just re-evaluate.
                        viewModel.refresh()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    "Grant all files access"
                } else {
                    "Grant storage permission"
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You can revoke this anytime in system settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Small horizontal card describing one reason Jupiter needs storage access.
 */
@Composable
private fun RationaleCard(
    icon: ImageVector,
    title: String,
    message: String,
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
