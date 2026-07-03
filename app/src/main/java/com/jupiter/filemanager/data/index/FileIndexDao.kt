package com.jupiter.filemanager.data.index

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for the persistent file index.
 *
 * All mutating members are `suspend`; observable reads return [Flow] so the UI
 * updates automatically as the index is rebuilt.
 */
@Dao
interface FileIndexDao {

    /** Inserts new rows or updates existing ones (keyed on [FileIndexEntry.path]). */
    @Upsert
    suspend fun upsertAll(entries: List<FileIndexEntry>)

    /** Observes the direct children of [parentPath], directories first then by name. */
    @Query(
        "SELECT * FROM file_index WHERE parentPath = :parentPath " +
            "ORDER BY isDirectory DESC, name COLLATE NOCASE ASC",
    )
    fun childrenOf(parentPath: String): Flow<List<FileIndexEntry>>

    /** Observes entries whose [FileIndexEntry.name] matches the SQL LIKE [pattern]. */
    @Query(
        "SELECT * FROM file_index WHERE name LIKE :pattern " +
            "ORDER BY isDirectory DESC, name COLLATE NOCASE ASC LIMIT 500",
    )
    fun searchByName(pattern: String): Flow<List<FileIndexEntry>>

    /** Returns the row for [path], or null when not indexed. */
    @Query("SELECT * FROM file_index WHERE path = :path")
    suspend fun getByPath(path: String): FileIndexEntry?

    /**
     * Returns every indexed **file** (never a directory) whose content hash
     * equals [hash]. Used to surface content-duplicates regardless of name.
     */
    @Query("SELECT * FROM file_index WHERE contentHash = :hash AND isDirectory = 0")
    suspend fun byHash(hash: String): List<FileIndexEntry>

    /** Deletes the single row for [path], if present. */
    @Query("DELETE FROM file_index WHERE path = :path")
    suspend fun deleteByPath(path: String)

    /**
     * Returns the paths of every row whose path begins with [prefix], used to
     * recursively remove an indexed directory subtree. Callers pass a prefix
     * that already includes the trailing separator to avoid sibling matches.
     */
    @Query("SELECT path FROM file_index WHERE path LIKE :prefix || '%'")
    suspend fun childPathsUnder(prefix: String): List<String>

    /**
     * Returns the cached content hash for [path] only when size and mtime are
     * unchanged from what was indexed, so callers can reuse it without rehashing.
     */
    @Query(
        "SELECT contentHash FROM file_index " +
            "WHERE path = :path AND sizeBytes = :sizeBytes AND lastModified = :lastModified",
    )
    suspend fun hashIfUnchanged(path: String, sizeBytes: Long, lastModified: Long): String?

    /** Deletes every row directly under [parentPath]. */
    @Query("DELETE FROM file_index WHERE parentPath = :parentPath")
    suspend fun deleteByParent(parentPath: String)

    /** Removes all rows. */
    @Query("DELETE FROM file_index")
    suspend fun clear()

    /** Observes the total number of indexed rows. */
    @Query("SELECT COUNT(*) FROM file_index")
    fun count(): Flow<Int>

    /** Returns the most recent [FileIndexEntry.indexedAt], or null when empty. */
    @Query("SELECT MAX(indexedAt) FROM file_index")
    suspend fun maxIndexedAt(): Long?

    /**
     * Prunes rows under [parentPath] whose path is not in [keepPaths], used to
     * drop entries for files that no longer exist after a re-index.
     */
    @Query("DELETE FROM file_index WHERE parentPath = :parentPath AND path NOT IN (:keepPaths)")
    suspend fun deleteStale(parentPath: String, keepPaths: List<String>)
}
