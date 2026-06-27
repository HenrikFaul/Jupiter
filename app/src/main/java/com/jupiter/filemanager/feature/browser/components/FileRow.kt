package com.jupiter.filemanager.feature.browser.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.ui.components.iconForFile

/**
 * A single row in the file browser list.
 *
 * Renders the type icon (via [iconForFile]), the file name, and a subtitle that
 * combines size/child-count and a relative last-modified time. When
 * [selectionMode] is active a leading [Checkbox] reflects [selected]. The whole
 * row is clickable and long-clickable via [combinedClickable].
 *
 * This composable is pure UI: it performs no IO and delegates all interaction
 * to [onClick] / [onLongClick].
 *
 * @param item the file-system entry to render.
 * @param selected whether this item is currently selected.
 * @param selectionMode whether multi-select mode is active (controls checkbox visibility).
 * @param onClick invoked on a normal tap.
 * @param onLongClick invoked on a long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = iconForFile(item),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            FileRowText(
                item = item,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The name + subtitle block of a [FileRow]. Kept separate so the row layout
 * stays readable. Pure UI; no IO.
 */
@Composable
private fun FileRowText(
    item: FileItem,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (item.isHidden) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        Text(
            text = fileRowSubtitle(item),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Builds the subtitle line for a [FileRow]: for directories it shows the child
 * count (when known), for files the human-readable size, always followed by the
 * relative last-modified time.
 */
private fun fileRowSubtitle(item: FileItem): String {
    val primary = if (item.isDirectory) {
        item.childCount?.let { formatItemCount(it) }
    } else {
        formatBytes(item.sizeBytes)
    }
    val time = formatRelativeTime(item.lastModified)
    return if (primary.isNullOrEmpty()) time else "$primary  •  $time"
}
