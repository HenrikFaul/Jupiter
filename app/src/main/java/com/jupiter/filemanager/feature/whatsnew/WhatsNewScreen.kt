package com.jupiter.filemanager.feature.whatsnew

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.JupiterWordmark
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * A single "What's New" highlight: an icon, a title, and a short description of a
 * recently shipped capability.
 */
private data class WhatsNewHighlight(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

/**
 * Static list of recent highlights. Update this list (and bump the app version) when
 * shipping notable features so returning users see what changed.
 */
private val whatsNewHighlights: List<WhatsNewHighlight> = listOf(
    WhatsNewHighlight(
        icon = Icons.Filled.ColorLens,
        title = "A calmer Jupiter design",
        description = "Core screens now share the midnight-and-teal layout, compact cards, " +
            "clearer hierarchy, and consistent controls.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.ContentCopy,
        title = "Safer duplicate review",
        description = "Exact and similar results stay visibly separate. Select all only " +
            "targets the results you can see and never selects the protected keeper.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.Security,
        title = "Stronger Vault controls",
        description = "Vault access can use device authentication or a local PIN, supports " +
            "automatic locking, and confirms permanent deletion before changing data.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.Tune,
        title = "Useful browsing preferences",
        description = "File grouping, delete confirmation, default sorting, language, and Vault " +
            "lock timing are now explicit settings instead of decorative switches.",
    ),
)

/**
 * "What's New" screen listing recent highlights of the Jupiter app.
 *
 * Purely static, informational content shown after an update so returning users can
 * discover newly shipped capabilities. It has no ViewModel and makes no claims beyond
 * the features that are actually available. [onDismiss] is invoked from both the top
 * bar navigation icon and the trailing "Got it" button to close the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(
    onDismiss: () -> Unit,
) {
    val highlights = remember { whatsNewHighlights }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "What's new") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    JupiterWordmark()
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Recent highlights",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 0.51 focuses on visual consistency and safer file actions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            items(highlights) { highlight ->
                HighlightCard(highlight = highlight)
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Got it")
                }
            }
        }
    }
}

/**
 * A rounded card rendering a single [WhatsNewHighlight] with a tinted leading icon, a
 * title, and a short description.
 */
@Composable
private fun HighlightCard(
    highlight: WhatsNewHighlight,
    modifier: Modifier = Modifier,
) {
    JupiterCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(JupiterDesign.CompactPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JupiterIconBadge(icon = highlight.icon)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlight.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = highlight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
