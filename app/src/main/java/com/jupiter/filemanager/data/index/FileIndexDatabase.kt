package com.jupiter.filemanager.data.index

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database backing the persistent file index and its life-cycle state.
 *
 * The index is a cache that CAN be rebuilt from the file system, but rebuilding the
 * content/perceptual fingerprints is expensive (a full photo re-analysis), so known version
 * hops migrate in place. No destructive fallback is enabled: an unsupported hop must fail
 * explicitly instead of silently discarding expensive fingerprints. The [IndexState] completeness
 * row lives in this database so lifecycle and content remain transactionally aligned.
 */
@Database(
    entities = [FileIndexEntry::class, IndexState::class, DedupDecision::class],
    version = 11,
    exportSchema = true,
)
abstract class FileIndexDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
    abstract fun indexStateDao(): IndexStateDao
    abstract fun dedupDecisionDao(): DedupDecisionDao
}
