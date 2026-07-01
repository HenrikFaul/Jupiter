package com.jupiter.filemanager.domain.model

/**
 * Immutable domain representation of a single entry currently held in the
 * app-managed Recycle Bin.
 *
 * Produced by the trash layer (mapped from a persisted `TrashEntry`) and
 * consumed by the presentation/UI layers. The [deletedAt] timestamp forms the
 * audit trail for when the file was moved to the bin.
 *
 * @property id stable unique identifier of the trashed item (also the on-disk
 *   file name under the trash root and the primary key of its DB row).
 * @property originalPath absolute path the file lived at before deletion; used
 *   as the default restore location.
 * @property name display name of the file or folder.
 * @property sizeBytes size in bytes at the time of deletion.
 * @property isDirectory whether the trashed entry is a directory.
 * @property deletedAt epoch-millis timestamp of when the item was trashed.
 */
data class TrashItem(
    val id: String,
    val originalPath: String,
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val deletedAt: Long,
)
