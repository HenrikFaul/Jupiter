package com.jupiter.filemanager.data.index

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database backing the persistent file index and its life-cycle state.
 *
 * The index is a cache that CAN be rebuilt from the file system, but rebuilding the
 * content/perceptual fingerprints is expensive (a full photo re-analysis), so known version
 * hops migrate in place (see IndexModule.MIGRATION_4_5); only unknown hops fall back to a
 * destructive wipe. Crucially, the [IndexState] completeness row lives in THIS database, so
 * a destructive wipe resets the state to EMPTY together with the data — an empty index can
 * never appear "complete".
 */
@Database(
    entities = [FileIndexEntry::class, IndexState::class, DedupDecision::class],
    version = 6,
    exportSchema = false,
)
abstract class FileIndexDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
    abstract fun indexStateDao(): IndexStateDao
    abstract fun dedupDecisionDao(): DedupDecisionDao
}
