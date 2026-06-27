package com.jupiter.filemanager.feature.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FileOperationType
import com.jupiter.filemanager.domain.model.OperationState

/**
 * A card that surfaces the state of an in-flight (or just-finished) file
 * operation: copy, move, delete, compress, or extract.
 *
 * Shows an operation icon, a title, the current file name, byte/item progress,
 * and a determinate [LinearProgressIndicator] driven by
 * [FileOperationProgress.fraction]. While the operation is [OperationState.RUNNING]
 * a Cancel action is offered. On completion, failure, or cancellation the card
 * reflects the terminal state instead.
 *
 * Pure UI: it performs no IO and delegates cancellation to [onCancel].
 *
 * @param progress the latest progress snapshot to render.
 * @param onCancel invoked when the user taps Cancel (only meaningful while running).
 */
@Composable
fun OperationProgressCard(
    progress: FileOperationProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = progress.state == OperationState.RUNNING

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(progress = progress)

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleFor(progress),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitleFor(progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isRunning) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = byteProgressLabel(progress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCancel) {
                        Text(text = "Cancel")
                    }
                }
            } else if (progress.state == OperationState.FAILED && progress.errorMessage != null) {
                Text(
                    text = progress.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Leading status icon: a spinner while running, otherwise a terminal-state glyph
 * tinted to match success/failure/cancellation.
 */
@Composable
private fun StatusIcon(progress: FileOperationProgress) {
    when (progress.state) {
        OperationState.RUNNING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }

        OperationState.COMPLETED -> {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }

        OperationState.FAILED -> {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
        }

        OperationState.CANCELLED -> {
            Icon(
                imageVector = iconForOperationType(progress.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = COLOR_DISABLED_ALPHA),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Maps an operation type to a representative Material icon. */
private fun iconForOperationType(type: FileOperationType): ImageVector = when (type) {
    FileOperationType.COPY -> Icons.Filled.ContentCopy
    FileOperationType.MOVE -> Icons.Filled.DriveFileMove
    FileOperationType.DELETE -> Icons.Filled.Delete
    FileOperationType.COMPRESS -> Icons.Filled.Archive
    FileOperationType.EXTRACT -> Icons.Filled.Unarchive
}

/** Human-readable verb for the operation type. */
private fun verbFor(type: FileOperationType): String = when (type) {
    FileOperationType.COPY -> "Copying"
    FileOperationType.MOVE -> "Moving"
    FileOperationType.DELETE -> "Deleting"
    FileOperationType.COMPRESS -> "Compressing"
    FileOperationType.EXTRACT -> "Extracting"
}

/** Past-tense / terminal description used for finished operations. */
private fun doneVerbFor(type: FileOperationType): String = when (type) {
    FileOperationType.COPY -> "Copied"
    FileOperationType.MOVE -> "Moved"
    FileOperationType.DELETE -> "Deleted"
    FileOperationType.COMPRESS -> "Compressed"
    FileOperationType.EXTRACT -> "Extracted"
}

/**
 * Card title reflecting both the operation type and its lifecycle state, e.g.
 * "Copying", "Copied", "Copy failed", "Copy cancelled".
 */
private fun titleFor(progress: FileOperationProgress): String = when (progress.state) {
    OperationState.RUNNING -> verbFor(progress.type)
    OperationState.COMPLETED -> doneVerbFor(progress.type)
    OperationState.FAILED -> "${capitalizedNoun(progress.type)} failed"
    OperationState.CANCELLED -> "${capitalizedNoun(progress.type)} cancelled"
}

/** Capitalised noun form of an operation, used in terminal-state titles. */
private fun capitalizedNoun(type: FileOperationType): String = when (type) {
    FileOperationType.COPY -> "Copy"
    FileOperationType.MOVE -> "Move"
    FileOperationType.DELETE -> "Delete"
    FileOperationType.COMPRESS -> "Compress"
    FileOperationType.EXTRACT -> "Extract"
}

/**
 * Secondary line under the title. While running it shows the current file name;
 * for terminal states it summarises the processed item count.
 */
private fun subtitleFor(progress: FileOperationProgress): String = when (progress.state) {
    OperationState.RUNNING -> progress.currentFileName.ifEmpty {
        itemProgressLabel(progress)
    }

    else -> {
        val count = progress.processedItems
        if (count == 1) "1 item" else "$count items"
    }
}

/** "processedItems / totalItems items" label for in-flight item progress. */
private fun itemProgressLabel(progress: FileOperationProgress): String {
    return if (progress.totalItems > 0) {
        "${progress.processedItems} / ${progress.totalItems} items"
    } else {
        "${progress.processedItems} items"
    }
}

/** "processedBytes / totalBytes" label using human-readable byte sizes. */
private fun byteProgressLabel(progress: FileOperationProgress): String {
    return if (progress.totalBytes > 0L) {
        "${formatBytes(progress.processedBytes)} / ${formatBytes(progress.totalBytes)}"
    } else {
        formatBytes(progress.processedBytes)
    }
}

/** Alpha applied to the icon tint when an operation was cancelled. */
private const val COLOR_DISABLED_ALPHA: Float = 0.6f
