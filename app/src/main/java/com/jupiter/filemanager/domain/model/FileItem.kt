package com.jupiter.filemanager.domain.model

/**
 * Immutable representation of a single file-system entry (file or directory).
 *
 * Instances are produced by the data layer (e.g. [com.jupiter.filemanager.data.file.FileSystemDataSource])
 * and consumed by the domain, presentation, and UI layers.
 */
data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val type: FileType,
    val extension: String,
    val mimeType: String? = null,
    val isHidden: Boolean = false,
    val childCount: Int? = null,
    val canRead: Boolean = true,
    val canWrite: Boolean = true,
) {
    /** Parent directory path, or null when there is no parent. */
    val parentPath: String?
        get() = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }
}
