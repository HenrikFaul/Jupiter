package com.jupiter.filemanager.data.index

/**
 * Source of files newer than a checkpoint, for [DedupReconciler]. Abstracted from
 * [MediaStoreIndexSource] so the reconciler's checkpoint/loop logic can be tested against a
 * scripted source without a real MediaStore.
 */
interface NewFileSource {

    /** The highest MediaStore `_id` currently observable, or 0 when empty/unreadable. */
    suspend fun maxObservedId(): Long

    /** Files with `_id` strictly greater than [sinceId], id-ascending, capped at [limit]. */
    suspend fun queryNewSince(sinceId: Long, limit: Int): List<MediaStoreIndexSource.NewFile>
}
