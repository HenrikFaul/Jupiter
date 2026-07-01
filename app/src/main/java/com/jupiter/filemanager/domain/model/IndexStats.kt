package com.jupiter.filemanager.domain.model

/**
 * Snapshot of the persistent file index for display in settings.
 *
 * @property indexedCount number of entries currently indexed.
 * @property lastIndexedAt epoch-millis of the most recent index write, or 0 when empty.
 */
data class IndexStats(
    val indexedCount: Int,
    val lastIndexedAt: Long,
)
