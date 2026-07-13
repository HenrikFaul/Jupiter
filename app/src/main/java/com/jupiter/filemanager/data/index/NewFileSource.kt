package com.jupiter.filemanager.data.index

/**
 * Source of files newer than a checkpoint, for [DedupReconciler]. Abstracted from
 * [MediaStoreIndexSource] so the reconciler's checkpoint/loop logic can be tested against a
 * scripted source without a real MediaStore.
 */
interface NewFileSource {

    /** Opaque MediaStore database version, available on Android 11+. */
    suspend fun currentVersion(): String? = null

    /** Current MediaStore generation marker, available on Android 11+. */
    suspend fun currentGeneration(): Long? = null

    /**
     * Finalized rows modified after [sinceGeneration]. Unlike `_ID`, the generation changes when a
     * pending download row is finalized, so the completed bytes cannot be skipped. Implementations
     * that cap a page at [limit] must include every row tied at the last returned generation;
     * generation alone is the persisted cursor, so splitting a tie would skip the remainder.
     */
    suspend fun queryChangedSinceGeneration(
        sinceGeneration: Long,
        limit: Int,
    ): List<MediaStoreIndexSource.NewFile> = emptyList()

    /** The highest MediaStore `_id` currently observable, or 0 when empty/unreadable. */
    suspend fun maxObservedId(): Long

    /** Files with `_id` strictly greater than [sinceId], id-ascending, capped at [limit]. */
    suspend fun queryNewSince(sinceId: Long, limit: Int): List<MediaStoreIndexSource.NewFile>
}
