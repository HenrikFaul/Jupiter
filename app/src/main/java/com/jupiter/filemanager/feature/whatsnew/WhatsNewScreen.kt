package com.jupiter.filemanager.feature.whatsnew

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
        icon = Icons.Filled.Cloud,
        title = "Remote backends",
        description = "Connect to SFTP, SMB, and cloud storage and browse remote files " +
            "right alongside your local ones.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.FolderZip,
        title = "Archives",
        description = "Open, browse, and extract ZIP and other archives without leaving " +
            "the file manager.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.PictureAsPdf,
        title = "PDF viewer",
        description = "Preview PDF documents in place with smooth page navigation.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.Edit,
        title = "Text editor",
        description = "Edit text and code files directly, with a fast, distraction-free editor.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.PlayCircle,
        title = "Automation",
        description = "Set up rules and routines to organize and tidy your files automatically.",
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
                        text = "Here are some of the latest additions to Jupiter.",
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
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        imageVector = highlight.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
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
