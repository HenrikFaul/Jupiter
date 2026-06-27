package com.jupiter.filemanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType

/**
 * Maps a [FileType] to a representative Material icon.
 */
fun iconForFileType(type: FileType): ImageVector = when (type) {
    FileType.FOLDER -> Icons.Filled.Folder
    FileType.IMAGE -> Icons.Filled.Image
    FileType.VIDEO -> Icons.Filled.Movie
    FileType.AUDIO -> Icons.Filled.MusicNote
    FileType.PDF -> Icons.Filled.PictureAsPdf
    FileType.DOCUMENT -> Icons.Filled.Description
    FileType.ARCHIVE -> Icons.Filled.FolderZip
    FileType.APK -> Icons.Filled.Android
    FileType.CODE -> Icons.Filled.Code
    FileType.OTHER -> Icons.Filled.InsertDriveFile
}

/**
 * Convenience overload that resolves the icon for a concrete [FileItem].
 */
fun iconForFile(item: FileItem): ImageVector =
    iconForFileType(if (item.isDirectory) FileType.FOLDER else item.type)
