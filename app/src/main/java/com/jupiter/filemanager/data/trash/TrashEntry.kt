package com.jupiter.filemanager.data.trash

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent Room row describing a single file/folder held in the Recycle Bin.
 *
 * Unlike the disposable file-index cache, trash metadata is authoritative: it
 * records where a file came from ([originalPath]) and where it now physically
 * lives inside the app-private trash root ([trashedPath]) so it can be restored
 * or permanently deleted. It must survive across app upgrades, hence its own DB
 * with no destructive migration.
 *
 * @property id stable unique id; also the file name under the trash root.
 * @property originalPath absolute path the file occupied before deletion.
 * @property trashedPath absolute path of the file inside the trash root.
 * @property name display name of the file or folder.
 * @property sizeBytes size in bytes captured at deletion time.
 * @property isDirectory whether the entry is a directory.
 * @property deletedAt epoch-millis timestamp of when the item was trashed.
 */
@Entity(tableName = "trash")
data class TrashEntry(
    @PrimaryKey val id: String,
    val originalPath: String,
    val trashedPath: String,
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val deletedAt: Long,
)
