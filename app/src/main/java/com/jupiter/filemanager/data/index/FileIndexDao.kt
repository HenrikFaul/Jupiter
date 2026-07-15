package com.jupiter.filemanager.data.index

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    /** Returns the rows for [paths] (used to preserve hashes across a batched re-index). */
    @Query("SELECT * FROM file_index WHERE path IN (:paths)")
    suspend fun entriesForPaths(paths: List<String>): List<FileIndexEntry>

    /**
     * Returns [exactPath]'s row plus every descendant row (paths beginning with
     * [descendantPrefix]), used to rewrite a whole subtree on a directory move/rename
     * without dropping its descendants. [descendantPrefix] MUST be LIKE-escaped by the
     * caller and include the trailing separator; `ESCAPE '\'` keeps a literal `_`/`%`
     * in a folder name from acting as a wildcard.
     */
    @Query(
        "SELECT * FROM file_index WHERE path = :exactPath " +
            "OR path LIKE :descendantPrefix || '%' ESCAPE '\\'",
    )
    suspend fun entriesUnder(exactPath: String, descendantPrefix: String): List<FileIndexEntry>

    /**
     * Returns the direct children of [parentPath] as a one-shot list (the suspend
     * counterpart of [childrenOf]). Used to preserve already-computed content hashes
     * when re-indexing a directory's listing.
     */
    @Query("SELECT * FROM file_index WHERE parentPath = :parentPath")
    suspend fun childEntries(parentPath: String): List<FileIndexEntry>

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
     *
     * The prefix MUST have its LIKE metacharacters (`\`, `%`, `_`) escaped by the
     * caller (see [FileIndexRepositoryImpl.escapeLike]); the `ESCAPE '\'` clause
     * makes those escapes literal so a real `_` in a directory name (very common)
     * cannot act as a single-character wildcard and over-match sibling folders.
     */
    @Query("SELECT path FROM file_index WHERE path LIKE :prefix || '%' ESCAPE '\\'")
    suspend fun childPathsUnder(prefix: String): List<String>

    /**
     * Returns the cached content hash for [path] only when size and mtime are
     * unchanged from what was indexed, so callers can reuse it without rehashing.
     * Mtime is compared at SECOND precision: MediaStore reports whole seconds while a
     * filesystem stat reports millis, and treating that rounding as "changed" silently
     * invalidated perfectly good hashes.
     */
    @Query(
        "SELECT contentHash FROM file_index " +
            "WHERE path = :path AND sizeBytes = :sizeBytes " +
            "AND lastModified / 1000 = :lastModified / 1000",
    )
    suspend fun hashIfUnchanged(path: String, sizeBytes: Long, lastModified: Long): String?

    /**
     * Refreshes ONLY the hash-related columns of an existing row, deliberately leaving
     * `lastSeenGeneration` untouched. Hash back-fill can run concurrently with a survey
     * (hashing takes seconds); a whole-row upsert built from a pre-hash snapshot would
     * REVERT the survey's fresh generation stamp and get the row wrongly swept as stale.
     * A no-op when [path] is not indexed (0 rows updated).
     */
    @Query(
        "UPDATE file_index SET contentHash = :hash, sizeBytes = :sizeBytes, " +
            "lastModified = :lastModified, indexedAt = :indexedAt WHERE path = :path",
    )
    suspend fun updateHash(
        path: String,
        sizeBytes: Long,
        lastModified: Long,
        hash: String,
        indexedAt: Long,
    )

    /**
     * Stores an image's perceptual (dHash) fingerprint. Targeted UPDATE for the same reason
     * as [updateHash]: it must never disturb a concurrent survey's generation stamp.
     */
    @Query("UPDATE file_index SET perceptualHash = :hash WHERE path = :path")
    suspend fun updatePerceptualHash(path: String, hash: Long)

    /**
     * Stores the FULL stacked perceptual fingerprint (dHash + pHash + aHash) in one targeted
     * UPDATE — same generation-stamp-preserving rationale as [updateHash].
     */
    @Query(
        "UPDATE file_index SET perceptualHash = :dhash, phash = :phash, ahash = :ahash " +
            "WHERE path = :path",
    )
    suspend fun updatePerceptualFingerprint(path: String, dhash: Long, phash: Long, ahash: Long)

    /**
     * Stores the lazy head+tail quick hash. Targeted UPDATE; never touches the generation stamp.
     */
    @Query("UPDATE file_index SET quickHash = :quickHash WHERE path = :path")
    suspend fun updateQuickHash(path: String, quickHash: String)

    /**
     * Path + all stacked perceptual layers of every fingerprinted file (comparison set for
     * near-dups). phash/ahash may be null on rows fingerprinted before those layers existed;
     * the comparison falls back to dHash-only for them.
     */
    @Query(
        "SELECT path, perceptualHash, phash, ahash FROM file_index " +
            "WHERE perceptualHash IS NOT NULL AND perceptualHash != :unhashable " +
            "AND isDirectory = 0",
    )
    suspend fun allPerceptualHashes(unhashable: Long): List<PathPerceptualHash>

    /**
     * Image rows that still need (any layer of) the perceptual fingerprint — the backfill work
     * list. Includes legacy dHash-only rows (phash IS NULL) so the library upgrades to the full
     * stack; rows already marked UNHASHABLE are excluded (phash is stamped together with dhash,
     * so an unhashable row never reappears here).
     */
    @Query(
        "SELECT * FROM file_index WHERE (perceptualHash IS NULL OR phash IS NULL) " +
            "AND isDirectory = 0 AND typeName = :imageTypeName LIMIT :limit",
    )
    suspend fun imagesMissingPerceptualHash(imageTypeName: String, limit: Int): List<FileIndexEntry>

    /** How many image rows still need a perceptual fingerprint (backfill progress denominator). */
    @Query(
        "SELECT COUNT(*) FROM file_index WHERE (perceptualHash IS NULL OR phash IS NULL) " +
            "AND isDirectory = 0 AND typeName = :imageTypeName",
    )
    suspend fun countImagesMissingPerceptualHash(imageTypeName: String): Int

    /**
     * Stores a text/archive structural fingerprint. Targeted UPDATE for the same reason as
     * [updateHash]: it must never disturb a concurrent survey's generation stamp.
     */
    @Query(
        "UPDATE file_index SET structuralHash = :hash, structuralSignature = NULL, " +
            "structuralExtent = NULL, structuralVersion = 0 WHERE path = :path",
    )
    suspend fun updateStructuralHash(path: String, hash: Long)

    /** Atomically stores every component of a versioned media descriptor. */
    @Query(
        "UPDATE file_index SET structuralHash = :hash, structuralSignature = :signature, " +
            "structuralExtent = :extent, structuralVersion = :version WHERE path = :path",
    )
    suspend fun updateMediaFingerprint(
        path: String,
        hash: Long,
        signature: String,
        extent: Long?,
        version: Int,
    )

    /**
     * Path + structural hash of every fingerprinted file of the given [typeNames] (the comparison
     * set for text near-dups / archive same-contents). Excludes the UNHASHABLE sentinel so unrelated
     * uncomparable files never match. Comparing only within one type keeps the shared column safe.
     */
    @Query(
        "SELECT path, structuralHash AS structuralHash, structuralSignature, " +
            "structuralExtent, structuralVersion FROM file_index " +
            "WHERE structuralHash IS NOT NULL AND structuralHash != :unhashable " +
            "AND isDirectory = 0 AND typeName IN (:typeNames)",
    )
    suspend fun structuralHashesOfTypes(
        typeNames: List<String>,
        unhashable: Long,
    ): List<PathStructuralHash>

    /** Rows of the given [typeNames] that still need a structural fingerprint (backfill work list). */
    @Query(
        "SELECT * FROM file_index WHERE structuralHash IS NULL AND isDirectory = 0 " +
            "AND typeName IN (:typeNames) LIMIT :limit",
    )
    suspend fun filesMissingStructuralHash(typeNames: List<String>, limit: Int): List<FileIndexEntry>

    /** Deletes every row directly under [parentPath]. */
    @Query("DELETE FROM file_index WHERE parentPath = :parentPath")
    suspend fun deleteByParent(parentPath: String)

    /** Removes all rows. */
    @Query("DELETE FROM file_index")
    suspend fun clear()

    /**
     * Global stale sweep: after a full survey stamps every row it saw with the current
     * [generation], this deletes rows that a PREVIOUS survey saw ([lastSeenGeneration] > 0)
     * but this one did not (< [generation]) — i.e. files that no longer exist. Rows written
     * outside a survey (deltas, browse self-heal; generation 0) are never swept here.
     */
    @Query(
        "DELETE FROM file_index WHERE lastSeenGeneration != 0 AND lastSeenGeneration < :generation",
    )
    suspend fun deleteStaleGenerations(generation: Long)

    /**
     * Atomically replaces a directory's whole indexed subtree on a move/rename: deletes the
     * old-path rows and inserts the rewritten ones in ONE transaction, so a process death
     * between the two can never leave the subtree missing from the index.
     */
    @Transaction
    suspend fun replaceSubtree(oldPaths: List<String>, newEntries: List<FileIndexEntry>) {
        oldPaths.forEach { deleteByPath(it) }
        upsertAll(newEntries)
    }

    /** Observes the total number of indexed rows. */
    @Query("SELECT COUNT(*) FROM file_index")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM file_index WHERE isDirectory = 0")
    suspend fun countFiles(): Int

    @Query("SELECT COUNT(*) FROM file_index WHERE isDirectory = 0 AND contentHash IS NOT NULL")
    suspend fun countFilesWithContentHash(): Int

    @Query("SELECT COUNT(*) FROM file_index WHERE isDirectory = 0 AND typeName = :imageTypeName")
    suspend fun countImages(imageTypeName: String): Int

    @Query(
        "SELECT COUNT(*) FROM file_index WHERE isDirectory = 0 AND typeName = :imageTypeName " +
            "AND perceptualHash IS NOT NULL AND phash IS NOT NULL AND ahash IS NOT NULL",
    )
    suspend fun countImagesWithDescriptors(imageTypeName: String): Int

    @Query("SELECT COUNT(*) FROM file_index WHERE isDirectory = 0 AND typeName IN (:typeNames)")
    suspend fun countStructuralCandidates(typeNames: List<String>): Int

    @Query(
        "SELECT COUNT(*) FROM file_index WHERE isDirectory = 0 AND typeName IN (:typeNames) " +
            "AND structuralHash IS NOT NULL",
    )
    suspend fun countStructuralReady(typeNames: List<String>): Int

    /**
     * Number of indexed **files** (directories excluded). Used as a fast, one-shot
     * "has the survey run yet?" probe so read paths can serve instant results from
     * the index instead of re-walking storage on every open.
     */
    @Query("SELECT COUNT(*) FROM file_index WHERE isDirectory = 0")
    suspend fun fileCount(): Int

    /**
     * The largest indexed files (never a directory) of at least [minSize] bytes,
     * biggest first, capped at [limit]. Backs the Cleanup "Large files" list without
     * a filesystem walk once the survey has run.
     */
    @Query(
        "SELECT * FROM file_index WHERE isDirectory = 0 AND sizeBytes >= :minSize " +
            "ORDER BY sizeBytes DESC LIMIT :limit",
    )
    suspend fun largeFiles(minSize: Long, limit: Int): List<FileIndexEntry>

    /**
     * Every indexed non-directory file. Used to aggregate the storage overview
     * (per-category totals) directly from the index rather than walking storage.
     */
    @Query("SELECT * FROM file_index WHERE isDirectory = 0")
    suspend fun allFiles(): List<FileIndexEntry>

    /**
     * File sizes (>= [minSize]) shared by two or more non-directory files. Identical
     * size is a cheap necessary condition for byte-equality, so these are the only
     * duplicate candidates worth hashing — computed in SQL with no filesystem walk.
     */
    @Query(
        "SELECT sizeBytes FROM file_index WHERE isDirectory = 0 AND sizeBytes >= :minSize " +
            "GROUP BY sizeBytes HAVING COUNT(*) > 1",
    )
    suspend fun collidingSizes(minSize: Long): List<Long>

    /** All non-directory files whose size is exactly [size] (a duplicate candidate bucket). */
    @Query("SELECT * FROM file_index WHERE isDirectory = 0 AND sizeBytes = :size")
    suspend fun filesOfSize(size: Long): List<FileIndexEntry>

    /** Every indexed path (files and directories) — used to skip already-indexed entries
     * during the reconciliation walk so it only stats/writes what MediaStore missed. */
    @Query("SELECT path FROM file_index")
    suspend fun allPaths(): List<String>

    /** Paths already written by the CURRENT survey generation (its MediaStore seed). The
     * reconciliation walk skips only these — rows from an interrupted prior generation are
     * re-stamped (re-seen) so a resumed survey doesn't sweep away its own earlier progress. */
    @Query("SELECT path FROM file_index WHERE lastSeenGeneration = :generation")
    suspend fun pathsAtGeneration(generation: Long): List<String>

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

/**
 * Lean projection for the near-duplicate comparison set: comparing a new image against tens
 * of thousands of fingerprints only needs (path, hash) — full rows are fetched afterwards
 * for the few matches only.
 */
data class PathPerceptualHash(
    val path: String,
    val perceptualHash: Long,
    /** DCT pHash layer; null on rows fingerprinted before the stacked layers existed. */
    val phash: Long? = null,
    /** Average-hash layer; same null semantics as [phash]. */
    val ahash: Long? = null,
)

/**
 * Lean projection for the text/archive near-duplicate comparison set: only (path, structuralHash)
 * is needed to scan tens of thousands of fingerprints; full rows are fetched for matches only.
 */
data class PathStructuralHash(
    val path: String,
    val structuralHash: Long,
    val structuralSignature: String? = null,
    val structuralExtent: Long? = null,
    val structuralVersion: Int = 0,
)
