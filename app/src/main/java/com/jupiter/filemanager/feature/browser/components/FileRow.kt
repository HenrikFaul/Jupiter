package com.jupiter.filemanager.feature.browser.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.core.util.formatItemCount
import com.jupiter.filemanager.core.util.formatRelativeTime
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.ui.components.iconForFile
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * A single row in the file browser list.
 *
 * Renders the type icon (via [iconForFile]) — or, for [FileType.IMAGE] and
 * [FileType.VIDEO], a cropped Coil thumbnail of the underlying file with the
 * type icon as both placeholder and error fallback — followed by the file name
 * and a subtitle that combines size/child-count and a relative last-modified
 * time. When [selectionMode] is active a leading [Checkbox] reflects [selected].
 * The whole row is clickable and long-clickable via [combinedClickable]. When
 * not in selection mode a trailing overflow button surfaces the per-item actions.
 *
 * The [dense] flag produces a tighter row (smaller padding, smaller leading
 * slot, no overflow button) for use in space-constrained layouts such as the
 * dual-pane screen. When [dense] is false the row is visually identical to its
 * historical single-pane appearance.
 *
 * This composable is pure UI: it performs no IO of its own (Coil handles
 * thumbnail loading) and delegates all interaction to [onClick] / [onLongClick]
 * / [onOverflowClick].
 *
 * @param item the file-system entry to render.
 * @param selected whether this item is currently selected.
 * @param selectionMode whether multi-select mode is active (controls checkbox visibility).
 * @param onClick invoked on a normal tap.
 * @param onLongClick invoked on a long press.
 * @param onOverflowClick invoked when the trailing overflow button is tapped.
 * @param dense when true, renders a tighter row without the trailing overflow button.
 * @param dragHandle optional trailing drag-handle content. When non-null it is rendered
 *   in the trailing area (in place of the overflow button); single-pane callers omit it
 *   so the row stays visually identical to its historical appearance.
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
    onOverflowClick: () -> Unit = {},
    dense: Boolean = false,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    val horizontalPadding = if (dense) 8.dp else 16.dp
    val verticalPadding = if (dense) 8.dp else 12.dp
    val leadingSize = if (dense) 32.dp else 40.dp
    val textGap = if (dense) 8.dp else 16.dp
    val surfaceModifier = if (dense) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    }

    Surface(
        shape = if (dense) RoundedCornerShape(0.dp) else JupiterDesign.CompactCardShape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = surfaceModifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            FileRowLeading(
                item = item,
                leadingSize = leadingSize,
                dense = dense,
            )

            Spacer(modifier = Modifier.width(textGap))

            FileRowText(
                item = item,
                modifier = Modifier.weight(1f),
            )

            if (dragHandle != null) {
                dragHandle()
            } else if (!selectionMode && !dense) {
                IconButton(onClick = onOverflowClick) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The leading visual of a [FileRow]. For images and videos it renders a cropped
 * Coil thumbnail of the file, falling back to the type icon (via [iconForFile])
 * while loading or on error so a missing/unreadable file never shows blank and
 * never crashes. For all other types it renders the type icon directly.
 */
@Composable
private fun FileRowLeading(
    item: FileItem,
    leadingSize: androidx.compose.ui.unit.Dp,
    dense: Boolean,
) {
    val isThumbnailable = item.type == FileType.IMAGE || item.type == FileType.VIDEO
    if (isThumbnailable) {
        val fallbackPainter = rememberVectorPainter(iconForFile(item))
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(java.io.File(item.path))
                .crossfade(true)
                .size(if (dense) 64 else 96)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            modifier = Modifier
                .size(leadingSize)
                .clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Icon(
            imageVector = iconForFile(item),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(leadingSize),
        )
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
