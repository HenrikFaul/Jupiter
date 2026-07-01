package com.jupiter.filemanager.data.index

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database backing the persistent file index.
 *
 * The index is a cache: it can always be rebuilt from the file system, so
 * schema migrations use destructive fallback (see IndexModule).
 */
@Database(entities = [FileIndexEntry::class], version = 1, exportSchema = false)
abstract class FileIndexDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
}
