package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the app-managed, recoverable Recycle Bin.
 *
 * The overriding invariant enforced by every implementation is **NO DATA LOSS**:
 * a file is only ever removed from its original location once it has been
 * safely relocated into the trash. If the move cannot be completed safely the
 * source is left untouched and the operation reports failure.
 */
interface TrashRepository {

    /**
     * Moves [item] from its original location into the Recycle Bin.
     *
     * Returns `true` only when the file is now safely stored in the trash and a
     * corresponding audit row has been persisted. On any failure the source is
     * preserved intact and `false` is returned — the file is never hard-deleted.
     */
    suspend fun moveToTrash(item: FileItem): Boolean

    /** Observes all trashed items, most-recently deleted first. */
    fun observeTrash(): Flow<List<TrashItem>>

    /** Observes the number of items currently in the Recycle Bin. */
    fun count(): Flow<Int>

    /**
     * Restores the trashed item with the given [id] back to its original path.
     *
     * Parent directories are recreated as needed. If the original path is now
     * occupied the item is restored alongside as `"name (restored)"`; an
     * existing file is never overwritten. The audit row is removed on success.
     */
    suspend fun restore(id: String): AppResult<Unit>

    /** Permanently deletes the trashed item with the given [id] and its row. */
    suspend fun deletePermanently(id: String): AppResult<Unit>

    /** Permanently empties the entire Recycle Bin. */
    suspend fun emptyAll(): AppResult<Unit>

    /**
     * Permanently deletes every item trashed strictly before [cutoffMillis] (epoch millis) — the
     * retention/auto-delete sweep. Returns the number of items purged. Never throws; an item whose
     * payload can't be removed is left in place (and not counted).
     */
    suspend fun purgeOlderThan(cutoffMillis: Long): Int
}
