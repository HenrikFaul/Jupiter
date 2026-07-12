package com.jupiter.filemanager.feature.details

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.InsertDriveFile
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatDate
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.LoadingView
import com.jupiter.filemanager.ui.components.StatRow
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Detail screen for a single file or folder.
 *
 * Shows a header with the type icon and name, a properties card (type, size,
 * modified date, path, permissions) and an action row (Share / Encrypt /
 * Add to Favorites / Details) consistent with the browser's per-item actions.
 *
 * The screen is pure UI: it resolves the file via [FileDetailsViewModel] (which
 * reads the `path` argument from `SavedStateHandle`) and delegates favorite
 * toggling to the ViewModel. Sharing is handled by the platform share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsScreen(
    onBack: () -> Unit,
    viewModel: FileDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Details") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.isLoading -> LoadingView()

                uiState.file == null -> ErrorView(
                    message = uiState.error ?: "Couldn't load this file.",
                    onRetry = { viewModel.load() },
                )

                else -> DetailsContent(
                    file = uiState.file!!,
                    isFavorite = uiState.isFavorite,
                    onShare = { shareFile(context, uiState.file!!) },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                )
            }
        }
    }
}

@Composable
private fun DetailsContent(
    file: FileItem,
    isFavorite: Boolean,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        FileHeader(file = file)

        Spacer(modifier = Modifier.height(20.dp))

        ActionRow(
            isFavorite = isFavorite,
            isDirectory = file.isDirectory,
            onShare = onShare,
            onToggleFavorite = onToggleFavorite,
        )

        Spacer(modifier = Modifier.height(20.dp))

        PropertiesCard(file = file)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FileHeader(file: FileItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JupiterIconBadge(
            icon = if (file.isDirectory) Icons.Filled.Folder else Icons.Outlined.InsertDriveFile,
            size = 80.dp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (file.isDirectory) {
                formatItemCount(file.childCount ?: 0)
            } else {
                formatBytes(file.sizeBytes)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionRow(
    isFavorite: Boolean,
    isDirectory: Boolean,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionButton(
            label = "Share",
            icon = Icons.Filled.Share,
            enabled = !isDirectory,
            onClick = onShare,
        )
        ActionButton(
            label = "Encrypt",
            icon = Icons.Filled.Lock,
            // Vault import is wired from the Vault feature; surfaced here as a
            // disabled affordance to stay honest about what this screen does.
            enabled = false,
            onClick = {},
        )
        ActionButton(
            label = if (isFavorite) "Favorited" else "Favorite",
            icon = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            enabled = true,
            onClick = onToggleFavorite,
        )
        ActionButton(
            label = "Details",
            icon = Icons.Filled.Info,
            // This screen already is the details view.
            enabled = false,
            onClick = {},
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(52.dp),
        ) {
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
        )
    }
}

@Composable
private fun PropertiesCard(file: FileItem) {
    JupiterCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = JupiterDesign.CompactPadding, vertical = 6.dp),
    ) {
        Column {
            StatRow(
                label = "Type",
                value = typeLabel(file),
                icon = Icons.Filled.Category,
            )
            StatRow(
                label = "Size",
                value = formatBytes(file.sizeBytes),
                icon = Icons.Filled.Storage,
            )
            if (file.isDirectory && file.childCount != null) {
                StatRow(
                    label = "Items",
                    value = formatItemCount(file.childCount ?: 0),
                    icon = Icons.Filled.Numbers,
                )
            }
            StatRow(
                label = "Modified",
                value = formatDate(file.lastModified),
                icon = Icons.Filled.CalendarToday,
            )
            StatRow(
                label = "Permissions",
                value = permissionsLabel(file),
                icon = Icons.Filled.Lock,
            )
            StatRow(
                label = "Path",
                value = file.path,
                icon = Icons.Filled.Folder,
            )
        }
    }
}

/** Human-readable file type label including the extension when present. */
private fun typeLabel(file: FileItem): String {
    if (file.isDirectory) return "Folder"
    val base = file.type.name.lowercase().replaceFirstChar { it.uppercase() }
    return if (file.extension.isNotBlank()) {
        "$base (.${file.extension})"
    } else {
        base
    }
}

/** "rw", "r", "w", or "none" derived from the item's read/write flags. */
private fun permissionsLabel(file: FileItem): String = when {
    file.canRead && file.canWrite -> "Read & write"
    file.canRead -> "Read only"
    file.canWrite -> "Write only"
    else -> "No access"
}

/**
 * Opens the platform share sheet for [file]. The file name and path are shared
 * as text; this avoids requiring a FileProvider configuration that is not part
 * of this screen's scope. Folders are not shareable.
 */
private fun shareFile(context: android.content.Context, file: FileItem) {
    if (file.isDirectory) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType ?: "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        putExtra(Intent.EXTRA_TEXT, "${file.name}\n${file.path}")
    }
    context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
}
