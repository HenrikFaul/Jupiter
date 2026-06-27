package com.jupiter.filemanager.domain.model

/**
 * A single point-in-time version of a file.
 *
 * No real versioning backend exists yet; the repository starts empty and
 * surfaces honest empty states.
 */
data class FileVersion(
    val id: String,
    val path: String,
    val label: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val isCurrent: Boolean = false,
)
