package com.jupiter.filemanager.domain.model

/**
 * Represents a local-vs-remote sync conflict for a file.
 *
 * No real sync backend exists yet; the repository starts empty.
 */
data class SyncConflict(
    val id: String,
    val path: String,
    val source: String,
    val localModified: Long,
    val remoteModified: Long,
    val localSizeBytes: Long,
    val remoteSizeBytes: Long,
)
