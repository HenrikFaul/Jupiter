package com.jupiter.filemanager.data.index

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent Room row representing a single indexed file-system entry.
 *
 * Rows are populated by the background indexing pass and read back by search
 * (and, later, duplicate scans) so callers can avoid re-walking storage.
 *
 * @property path absolute path; primary key.
 * @property parentPath absolute path of the containing directory.
 * @property typeName the [com.jupiter.filemanager.domain.model.FileType] name.
 * @property contentHash optional content hash, populated by a later pass.
 * @property indexedAt epoch-millis timestamp of when this row was written.
 */
@Entity(
    tableName = "file_index",
    indices = [Index("parentPath"), Index("name")],
)
data class FileIndexEntry(
    @PrimaryKey val path: String,
    val parentPath: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val typeName: String,
    val isDirectory: Boolean,
    val extension: String,
    val contentHash: String? = null,
    val indexedAt: Long,
)
