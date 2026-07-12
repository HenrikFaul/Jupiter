package com.jupiter.filemanager.feature.browser.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * The set of contextual actions that can be performed on a single [FileItem]
 * from the file browser's actions sheet.
 */
enum class FileAction {
    OPEN,
    SHARE,
    RENAME,
    DELETE,
    COPY,
    MOVE,
    COMPRESS,
    DETAILS,
    ADD_BOOKMARK,
    COPY_PATH,
    OPEN_WITH,
}

/**
 * Modal bottom sheet presenting the available [FileAction]s for [item].
 *
 * A header shows the item's icon, name, and a short subtitle (size or child
 * count plus relative modification time). Tapping an action invokes [onAction]
 * with the corresponding [FileAction] and then [onDismiss]. Some actions are
 * only meaningful for files versus directories (e.g. OPEN/SHARE are hidden for
 * directories) — the list is built accordingly.
 *
 * Pure UI: no IO, no business logic; everything is delegated to the callbacks.
 *
 * @param item the file-system entry the actions apply to.
 * @param onAction invoked with the chosen action.
 * @param onDismiss invoked when the sheet should be hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsSheet(
    item: FileItem,
    onAction: (FileAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = JupiterDesign.HeroCardShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            FileActionsHeader(item = item)

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            actionsFor(item).forEach { action ->
                ActionRow(
                    icon = iconFor(action),
                    label = labelFor(action),
                    destructive = action == FileAction.DELETE,
                    onClick = {
                        onAction(action)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Header block of the actions sheet: item icon, name, and a size/time subtitle.
 */
@Composable
private fun FileActionsHeader(
    item: FileItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JupiterIconBadge(
            icon = iconForFile(item),
            contentDescription = item.name,
            tint = MaterialTheme.colorScheme.primary,
            size = 48.dp,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = headerSubtitle(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A single tappable action entry in the sheet.
 */
@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * Builds the ordered list of actions appropriate for [item]. OPEN and SHARE are
 * omitted for directories since they apply to file content.
 */
private fun actionsFor(item: FileItem): List<FileAction> = buildList {
    if (!item.isDirectory) {
        add(FileAction.OPEN)
        add(FileAction.OPEN_WITH)
        add(FileAction.SHARE)
        if (item.type == com.jupiter.filemanager.domain.model.FileType.IMAGE ||
            item.type == com.jupiter.filemanager.domain.model.FileType.VIDEO
        ) {
            add(FileAction.COMPRESS)
        }
    }
    add(FileAction.RENAME)
    add(FileAction.COPY)
    add(FileAction.COPY_PATH)
    add(FileAction.MOVE)
    add(FileAction.ADD_BOOKMARK)
    add(FileAction.DETAILS)
    add(FileAction.DELETE)
}

private fun headerSubtitle(item: FileItem): String {
    val primary = if (item.isDirectory) {
        item.childCount?.let { formatItemCount(it) }
    } else {
        formatBytes(item.sizeBytes)
    }
    val time = formatRelativeTime(item.lastModified)
    return if (primary.isNullOrEmpty()) time else "$primary  •  $time"
}

private fun iconFor(action: FileAction): ImageVector = when (action) {
    FileAction.OPEN -> Icons.Filled.OpenInNew
    FileAction.SHARE -> Icons.Filled.Share
    FileAction.RENAME -> Icons.Filled.DriveFileRenameOutline
    FileAction.DELETE -> Icons.Filled.Delete
    FileAction.COPY -> Icons.Filled.ContentCopy
    FileAction.MOVE -> Icons.AutoMirrored.Filled.DriveFileMove
    FileAction.COMPRESS -> Icons.Filled.FolderZip
    FileAction.DETAILS -> Icons.Filled.Info
    FileAction.ADD_BOOKMARK -> Icons.Filled.BookmarkAdd
    FileAction.COPY_PATH -> Icons.Filled.ContentCopy
    FileAction.OPEN_WITH -> Icons.Filled.OpenInNew
}

private fun labelFor(action: FileAction): String = when (action) {
    FileAction.OPEN -> "Open"
    FileAction.SHARE -> "Share"
    FileAction.RENAME -> "Rename"
    FileAction.DELETE -> "Delete"
    FileAction.COPY -> "Copy to…"
    FileAction.MOVE -> "Move to…"
    FileAction.COMPRESS -> "Compress"
    FileAction.DETAILS -> "Details"
    FileAction.ADD_BOOKMARK -> "Add bookmark"
    FileAction.COPY_PATH -> "Copy path"
    FileAction.OPEN_WITH -> "Open with…"
}
