package com.jupiter.filemanager.data.index

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database backing the persistent file index and its life-cycle state.
 *
 * The index is a cache: it can always be rebuilt from the file system, so schema
 * migrations use destructive fallback (see IndexModule). Crucially, the [IndexState]
 * completeness row lives in THIS database, so a destructive wipe resets the state to
 * EMPTY together with the data — an empty index can never appear "complete".
 */
@Database(entities = [FileIndexEntry::class, IndexState::class], version = 3, exportSchema = false)
abstract class FileIndexDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
    abstract fun indexStateDao(): IndexStateDao
}
