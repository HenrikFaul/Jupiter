package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.IndexStats
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the persistent file index (a Room-backed cache of
 * file-system metadata) used to speed up search and, later, duplicate scans.
 *
 * The index is purely additive to the live file system: it may be stale or
 * empty, in which case callers should fall back to walking storage directly.
 * Implementations perform all IO on a background dispatcher.
 */
interface FileIndexRepository {

    /** Observes the indexed direct children of [parentPath]. */
    fun observeChildren(parentPath: String): Flow<List<FileItem>>

    /** Observes indexed entries whose name contains [query] (case-insensitive). */
    fun search(query: String): Flow<List<FileItem>>

    /**
     * Upserts [items] as the children of [parentPath] and prunes any previously
     * indexed children that are no longer present.
     */
    suspend fun replaceChildren(parentPath: String, items: List<FileItem>)

    /** Upserts [items] into the index without pruning. */
    suspend fun upsert(items: List<FileItem>)

    /**
     * Returns the cached content hash for the file at [path] when its size and
     * mtime are unchanged, or null when it must be (re)hashed.
     */
    suspend fun hashForUnchanged(path: String, sizeBytes: Long, lastModified: Long): String?

    /** Stores [hash] as the content hash of [item]. */
    suspend fun putHash(item: FileItem, hash: String)

    /** Clears the entire index. */
    suspend fun clear()

    /** Observes summary statistics about the index. */
    fun stats(): Flow<IndexStats>
}
