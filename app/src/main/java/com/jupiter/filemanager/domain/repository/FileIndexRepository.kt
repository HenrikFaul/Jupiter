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
     * Upserts a single [item] with fresh metadata. Any previously-known content
     * hash is preserved when the file's size and mtime are unchanged; otherwise
     * the hash is cleared so it will be recomputed on demand.
     */
    suspend fun indexFile(item: FileItem)

    /**
     * Removes the entry for [path] and, when [path] denotes a directory, every
     * entry beneath it. Safe to call for paths that are not indexed.
     */
    suspend fun removeByPath(path: String)

    /**
     * Reflects a move/rename in the index: drops the old [fromPath] subtree and
     * indexes [toItem] at its new location.
     */
    suspend fun onMovedOrRenamed(fromPath: String, toItem: FileItem)

    /**
     * Returns other indexed files that share [item]'s content hash (same bytes,
     * regardless of name or format), excluding [item] itself. Computes the hash
     * on demand via a chunked SHA-1 when it is not already cached. Returns an
     * empty list for directories, hash failures, or when there is no duplicate.
     */
    suspend fun findContentDuplicates(item: FileItem): List<FileItem>

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
