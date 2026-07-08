package com.jupiter.filemanager.data.trash

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database backing the recoverable Recycle Bin.
 *
 * Deliberately isolated from the disposable file-index cache ("jupiter_index.db")
 * in its own store ("jupiter_trash.db"): trash metadata is authoritative and must
 * never be wiped by index-cache migrations. Migrations here must be
 * non-destructive (see `TrashModule`).
 */
@Database(entities = [TrashEntry::class], version = 1, exportSchema = false)
abstract class TrashDatabase : RoomDatabase() {
    abstract fun trashDao(): TrashDao
}
