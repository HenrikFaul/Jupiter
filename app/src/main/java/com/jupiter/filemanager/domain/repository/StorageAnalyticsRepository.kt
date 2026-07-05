package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageOverview
import kotlinx.coroutines.flow.Flow

/**
 * Provides storage analytics: an overview of how space is used, discovery of
 * large files, and detection of duplicate files for cleanup purposes.
 */
interface StorageAnalyticsRepository {

    /**
     * Produces a categorized overview of the primary storage volume, grouping
     * usage by file type into [com.jupiter.filemanager.domain.model.CategoryUsage] entries.
     *
     * When [preferIndex] is true (the default) and the persistent file index has been
     * populated by the background survey, the overview is aggregated instantly from the
     * index instead of re-walking storage. Pass false to force a fresh filesystem walk
     * (e.g. a user-initiated rescan).
     */
    suspend fun storageOverview(preferIndex: Boolean = true): AppResult<StorageOverview>

    /**
     * Streams a [StorageOverview] incrementally: emits a partial overview early
     * and progressively as the primary volume is walked, with the final emission
     * representing the complete, fully-walked result.
     *
     * Unlike [storageOverview] this never front-loads the entire walk before the
     * first value, so consumers can render a meaningful breakdown within
     * milliseconds and refine it as more of the tree is analyzed. When the index is
     * already populated a single instant overview is emitted from it (no walk).
     */
    fun observeStorageOverview(): Flow<StorageOverview>

    /**
     * Streams files under [rootPath] whose size is at least [minSizeBytes],
     * emitting each match as it is found.
     *
     * When [preferIndex] is true (the default) and the index is populated, matches are
     * served from the index with no filesystem walk. Pass false to force a fresh walk.
     */
    fun findLargeFiles(
        rootPath: String,
        minSizeBytes: Long,
        preferIndex: Boolean = true,
    ): Flow<FileItem>

    /**
     * Streams groups of duplicate files discovered under [rootPath]. Files are
     * grouped first by size and then by content hash; each emitted
     * [DuplicateGroup] contains two or more identical files.
     *
     * When [preferIndex] is true (the default) and the index is populated, candidate
     * size-buckets come from the index (no filesystem walk) and are confirmed by hash.
     * Pass false to force a fresh walk.
     */
    fun findDuplicates(rootPath: String, preferIndex: Boolean = true): Flow<DuplicateGroup>
}
