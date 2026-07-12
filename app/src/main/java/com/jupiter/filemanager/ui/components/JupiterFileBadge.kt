package com.jupiter.filemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.ui.theme.JupiterDesign

/** Compact, extension-aware file tile matching the colourful reference rows. */
@Composable
fun JupiterFileBadge(
    item: FileItem,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
) {
    val tint = colorForFile(item)
    Surface(
        modifier = modifier.size(size),
        shape = JupiterDesign.IconBadgeShape,
        color = tint.copy(alpha = 0.22f),
        contentColor = tint,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = iconForFile(item),
                contentDescription = null,
                modifier = Modifier.size(if (item.isDirectory) size * 0.52f else size * 0.40f),
            )
            if (!item.isDirectory) {
                Text(
                    text = fileBadgeLabel(item),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

private fun fileBadgeLabel(item: FileItem): String = when {
    item.extension.isNotBlank() -> item.extension.uppercase().take(4)
    item.type == FileType.PDF -> "PDF"
    item.type == FileType.APK -> "APK"
    else -> item.type.name.take(4)
}

private fun colorForFile(item: FileItem): Color = when (item.type) {
    FileType.FOLDER -> JupiterDesign.CategoryArchive
    FileType.IMAGE -> JupiterDesign.CategoryPhoto
    FileType.VIDEO -> JupiterDesign.CategoryVideo
    FileType.AUDIO -> JupiterDesign.CategoryAudio
    FileType.DOCUMENT, FileType.CODE -> JupiterDesign.CategoryDocument
    FileType.PDF -> JupiterDesign.CategoryPdf
    FileType.ARCHIVE -> JupiterDesign.CategoryArchive
    FileType.APK -> JupiterDesign.CategoryApk
    FileType.OTHER -> JupiterDesign.CategoryOther
}
