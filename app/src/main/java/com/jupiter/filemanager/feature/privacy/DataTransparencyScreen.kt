package com.jupiter.filemanager.feature.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * A static, honest "Your data & privacy in Jupiscan" trust surface.
 *
 * This screen exists to close the Play-preference gap: users are wary of file
 * managers that request broad permissions without explaining why. It plainly
 * documents which permissions Jupiter uses and for what, where data lives (on
 * device, no ads, no third-party trackers), how sensitive material is
 * encrypted, that analytics are opt-in and off by default, and that deletes
 * land in a recoverable Recycle Bin.
 *
 * The screen is pure UI: it reads no state and owns no [androidx.lifecycle.ViewModel].
 *
 * @param onBack invoked when the user dismisses the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataTransparencyScreen(
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "Your data & privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            Text(
                text = "Your data & privacy in Jupiscan",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Jupiscan is a native, on-device file manager. Here is exactly " +
                    "what it can access, why, and what happens to your data — in plain " +
                    "language, with nothing hidden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TrustCard(
                icon = Icons.Filled.Security,
                title = "Permissions & why",
            ) {
                PermissionLine(
                    name = "All-files access",
                    purpose = "To browse and manage the files and folders you choose.",
                )
                PermissionLine(
                    name = "Photos, video & audio (READ_MEDIA_*)",
                    purpose = "Powers the fast category browser for your media.",
                )
                PermissionLine(
                    name = "Biometric (USE_BIOMETRIC)",
                    purpose = "Unlocks the encrypted Vault with your fingerprint or face.",
                )
                PermissionLine(
                    name = "Internet (INTERNET)",
                    purpose = "For remote/cloud connections, Wi-Fi transfer, and optional " +
                        "AI requests you explicitly configure and submit.",
                )
                PermissionLine(
                    name = "Notifications (POST_NOTIFICATIONS)",
                    purpose = "Shows progress for transfers and file indexing.",
                )
            }

            TrustCard(
                icon = Icons.Filled.PhoneAndroid,
                title = "Where your data lives",
            ) {
                BulletLine("Everything stays on your device by default.")
                BulletLine("Jupiscan has no ads and no third-party trackers.")
                BulletLine(
                    "Nothing is uploaded anywhere except to the cloud or remote " +
                        "servers you explicitly connect.",
                )
            }

            TrustCard(
                icon = Icons.Filled.Lock,
                title = "Encryption",
            ) {
                BulletLine(
                    "The Vault is encrypted at rest on your device (Android " +
                        "EncryptedFile).",
                )
                BulletLine(
                    "Remote and cloud passwords, and the Drive access token, are " +
                        "stored in EncryptedSharedPreferences.",
                )
                BulletLine("Your credentials and keys are never written to logs.")
            }

            TrustCard(
                icon = Icons.Filled.Analytics,
                title = "Analytics",
            ) {
                BulletLine("Anonymous usage analytics are off by default.")
                BulletLine(
                    "They are fully opt-in — you can turn them on or off any time " +
                        "under Settings → Privacy.",
                )
            }

            TrustCard(
                icon = Icons.Filled.DeleteOutline,
                title = "Recycle Bin",
            ) {
                BulletLine(
                    "Deleting a file moves it to an app-private Recycle Bin, not to " +
                        "immediate permanent loss.",
                )
                BulletLine("You can restore items from the Recycle Bin until you empty it.")
            }

            TrustCard(
                icon = Icons.Filled.VerifiedUser,
                title = "In short",
            ) {
                BulletLine(
                    "Your files are yours. Jupiscan keeps them on your device, " +
                        "encrypts what's sensitive, and only reaches the network for " +
                        "the connections you choose.",
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "No account required. No data leaves your phone unless you " +
                        "connect a remote or cloud service yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * A titled card grouping related trust statements, introduced by a leading icon.
 */
@Composable
private fun TrustCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JupiterIconBadge(
                    icon = icon,
                    contentDescription = null,
                    size = 44.dp,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            content()
        }
    }
}

/**
 * A single permission row: the permission name in emphasis with a one-line
 * plain-language purpose beneath it.
 */
@Composable
private fun PermissionLine(
    name: String,
    purpose: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = purpose,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A simple bulleted statement line.
 */
@Composable
private fun BulletLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
