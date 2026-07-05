package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.IndexStats
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [FileIndexRepository].
 *
 * All blocking IO is confined to [ioDispatcher]; observable reads are moved off
 * the collecting thread via [flowOn].
 */
@Singleton
class FileIndexRepositoryImpl @Inject constructor(
    private val dao: FileIndexDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FileIndexRepository {

    override fun observeChildren(parentPath: String): Flow<List<FileItem>> =
        dao.childrenOf(parentPath)
            .map { entries -> entries.map(::toFileItem) }
            .flowOn(ioDispatcher)

    override fun search(query: String): Flow<List<FileItem>> {
        val pattern = "%" + query + "%"
        return dao.searchByName(pattern)
            .map { entries -> entries.map(::toFileItem) }
            .flowOn(ioDispatcher)
    }

    override suspend fun replaceChildren(parentPath: String, items: List<FileItem>) =
        withContext(ioDispatcher) {
            if (items.isEmpty()) {
                // The directory is now empty: drop all its indexed children. (deleteStale
                // with an empty keep-list would generate an invalid SQL `NOT IN ()`.)
                dao.deleteByParent(parentPath)
                return@withContext
            }
            val now = System.currentTimeMillis()
            // Preserve a previously-computed content hash for any child whose identity
            // (size + mtime) is unchanged, so re-indexing a directory's listing (e.g. every
            // time it is browsed) never discards the hashes the survey precomputed — which
            // the downloads-dedup check and the index-backed duplicate scan both rely on.
            val existing = dao.childEntries(parentPath).associateBy { it.path }
            val entries = items.map { item ->
                val prior = existing[item.path]
                val keepHash = if (!item.isDirectory && prior != null &&
                    prior.sizeBytes == item.sizeBytes && prior.lastModified == item.lastModified
                ) {
                    prior.contentHash
                } else {
                    null
                }
                toEntry(item, now).copy(contentHash = keepHash)
            }
            dao.upsertAll(entries)
            dao.deleteStale(parentPath, items.map { it.path })
        }

    override suspend fun upsert(items: List<FileItem>) = withContext(ioDispatcher) {
        if (items.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        // Preserve an already-computed content hash for any file whose identity (size +
        // mtime) is unchanged, so a re-index (e.g. the periodic full survey) never discards
        // the hashes computed lazily for duplicate detection. One batched lookup keeps this
        // cheap. toEntry() alone would null every hash.
        val existing = dao.entriesForPaths(items.map { it.path }).associateBy { it.path }
        val entries = items.map { item ->
            val prior = existing[item.path]
            val keepHash = if (prior != null && IndexPathRewrite.identityUnchanged(
                    item.isDirectory, prior.sizeBytes, prior.lastModified,
                    item.sizeBytes, item.lastModified,
                )
            ) {
                prior.contentHash
            } else {
                null
            }
            toEntry(item, now).copy(contentHash = keepHash)
        }
        dao.upsertAll(entries)
    }

    override suspend fun indexFile(item: FileItem) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        // Preserve a known hash only when the file's identity (size + mtime) is
        // unchanged; otherwise clear it so it will be recomputed lazily.
        val preservedHash = if (item.isDirectory) {
            null
        } else {
            dao.getByPath(item.path)?.takeIf {
                it.sizeBytes == item.sizeBytes && it.lastModified == item.lastModified
            }?.contentHash
        }
        dao.upsertAll(listOf(toEntry(item, now).copy(contentHash = preservedHash)))
    }

    override suspend fun removeByPath(path: String) = withContext(ioDispatcher) {
        dao.deleteByPath(path)
        // Remove any subtree: match children under "path/" so siblings sharing a
        // path prefix (e.g. ".../foo" vs ".../foobar") are never affected. The
        // prefix is LIKE-escaped so that literal '_' / '%' in a directory name
        // (both common) cannot act as wildcards and over-match sibling folders
        // (e.g. deleting "photos_2024" must not purge "photosX2024").
        val prefix = escapeLike(path.trimEnd('/')) + "/"
        val descendants = dao.childPathsUnder(prefix)
        descendants.forEach { dao.deleteByPath(it) }
    }

    /**
     * Escapes SQL LIKE metacharacters so the value matches literally under an
     * `ESCAPE '\'` clause. Backslash must be escaped first.
     */
    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    override suspend fun onMovedOrRenamed(fromPath: String, toItem: FileItem) =
        withContext(ioDispatcher) {
            val oldRoot = fromPath.trimEnd('/')
            val descendantPrefix = escapeLike(oldRoot) + "/"
            val affected = dao.entriesUnder(oldRoot, descendantPrefix)
            if (affected.isEmpty()) {
                // Nothing indexed under the old path — just index the new location.
                indexFile(toItem)
                return@withContext
            }
            val newRoot = toItem.path.trimEnd('/')
            val now = System.currentTimeMillis()
            // Rewrite the WHOLE subtree's paths under the new root, preserving each row's
            // cached content hash (a rename/move does not change content). Previously this
            // dropped every descendant and re-indexed only the new root, losing the subtree
            // until the next full scan.
            val rewritten = affected.mapNotNull { entry ->
                val newPath = IndexPathRewrite.rewrite(oldRoot, newRoot, entry.path)
                    ?: return@mapNotNull null
                if (entry.path == oldRoot) {
                    // The moved/renamed root: adopt the authoritative new item's metadata,
                    // keeping the hash only if its identity is unchanged.
                    val keepHash = if (IndexPathRewrite.identityUnchanged(
                            toItem.isDirectory, entry.sizeBytes, entry.lastModified,
                            toItem.sizeBytes, toItem.lastModified,
                        )
                    ) entry.contentHash else null
                    toEntry(toItem, now).copy(contentHash = keepHash)
                } else {
                    entry.copy(
                        path = newPath,
                        parentPath = IndexPathRewrite.parentOf(newPath) ?: "",
                        name = IndexPathRewrite.nameOf(newPath),
                    )
                }
            }
            // Drop the old rows, then insert the rewritten ones. (Room can't UPDATE a PK.)
            affected.forEach { dao.deleteByPath(it.path) }
            dao.upsertAll(rewritten)
        }

    override suspend fun findContentDuplicates(item: FileItem): List<FileItem> =
        withContext(ioDispatcher) {
            if (item.isDirectory) return@withContext emptyList()

            val hash = dao.hashIfUnchanged(item.path, item.sizeBytes, item.lastModified)
                ?: computeHash(item.path)?.also { putHash(item, it) }
                ?: return@withContext emptyList()

            dao.byHash(hash)
                .asSequence()
                .filter { it.path != item.path }
                .map(::toFileItem)
                .toList()
        }

    /**
     * Streams the file at [path] through a chunked SHA-1 digest. Cancellable and
     * total: any IO error (missing file, permission denied) yields null rather
     * than throwing, so best-effort callers never fail on a bad file.
     */
    private suspend fun computeHash(path: String): String? {
        return try {
            val file = File(path)
            if (!file.isFile) return null
            val digest = MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(DEFAULT_HASH_BUFFER)
            file.inputStream().use { stream ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            digest.digest().toHexString()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    /** Lowercase, unseparated hex, matching the app's existing SHA-1 hash format. */
    private fun ByteArray.toHexString(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    override suspend fun hashForUnchanged(
        path: String,
        sizeBytes: Long,
        lastModified: Long,
    ): String? = withContext(ioDispatcher) {
        dao.hashIfUnchanged(path, sizeBytes, lastModified)
    }

    override suspend fun putHash(item: FileItem, hash: String) = withContext(ioDispatcher) {
        val existing = dao.getByPath(item.path)
        val base = existing ?: toEntry(item, System.currentTimeMillis())
        dao.upsertAll(listOf(base.copy(contentHash = hash, indexedAt = System.currentTimeMillis())))
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        dao.clear()
    }

    override fun stats(): Flow<IndexStats> =
        dao.count()
            .map { count ->
                IndexStats(indexedCount = count, lastIndexedAt = dao.maxIndexedAt() ?: 0L)
            }
            .flowOn(ioDispatcher)

    // --- Index-backed analytics ----------------------------------------------

    override suspend fun isPopulated(): Boolean = withContext(ioDispatcher) {
        dao.fileCount() > 0
    }

    override suspend fun largeFiles(minSizeBytes: Long, limit: Int): List<FileItem> =
        withContext(ioDispatcher) {
            dao.largeFiles(minSizeBytes, limit).map(::toFileItem)
        }

    override suspend fun allFiles(): List<FileItem> = withContext(ioDispatcher) {
        dao.allFiles().map(::toFileItem)
    }

    override suspend fun duplicateGroups(minSizeBytes: Long): List<DuplicateGroup> =
        withContext(ioDispatcher) {
            val groups = mutableListOf<DuplicateGroup>()
            for (size in dao.collidingSizes(minSizeBytes)) {
                currentCoroutineContext().ensureActive()
                val candidates = dao.filesOfSize(size)
                if (candidates.size < 2) continue

                val byHash = LinkedHashMap<String, MutableList<FileIndexEntry>>()
                for (entry in candidates) {
                    currentCoroutineContext().ensureActive()
                    val hash = hashForEntry(entry) ?: continue
                    byHash.getOrPut(hash) { mutableListOf() }.add(entry)
                }
                for ((hash, group) in byHash) {
                    if (group.size < 2) continue
                    groups += DuplicateGroup(hash = hash, files = group.map(::toFileItem))
                }
            }
            groups
        }

    override suspend fun hashCollidingSizes(minSizeBytes: Long) = withContext(ioDispatcher) {
        for (size in dao.collidingSizes(minSizeBytes)) {
            currentCoroutineContext().ensureActive()
            val candidates = dao.filesOfSize(size)
            if (candidates.size < 2) continue
            for (entry in candidates) {
                currentCoroutineContext().ensureActive()
                if (entry.contentHash != null) continue
                hashForEntry(entry) // computes and caches as a side effect
            }
        }
    }

    /**
     * Returns the content hash for an indexed [entry], safe to use for
     * duplicate-grouping that may drive a delete.
     *
     * The stored hash is trusted ONLY when the file on disk STILL matches the
     * indexed identity (same size AND mtime) — this guards against a file that
     * changed outside the app without a delta firing, which would otherwise let two
     * genuinely-different files be grouped as duplicates from a stale hash. When the
     * identity no longer matches (or no hash is stored yet) the file is re-hashed
     * from its current bytes and the index row is refreshed with the current
     * size/mtime/hash. Returns null when the file has vanished or cannot be read, so
     * a stale entry is never grouped.
     */
    private suspend fun hashForEntry(entry: FileIndexEntry): String? {
        val file = File(entry.path)
        val currentSize = runCatching { if (file.isFile) file.length() else -1L }
            .getOrDefault(-1L)
        if (currentSize < 0L) return null // vanished or not a regular file → never group
        val currentMtime = runCatching { file.lastModified() }.getOrDefault(0L)

        val identityUnchanged = currentSize == entry.sizeBytes &&
            currentMtime == entry.lastModified
        if (identityUnchanged) {
            entry.contentHash?.let { return it }
        }

        // Stale identity or no cached hash: hash the CURRENT bytes and refresh the row
        // (size/mtime/hash) so the confirmation always reflects what is on disk now.
        val computed = computeHash(entry.path) ?: return null
        dao.upsertAll(
            listOf(
                entry.copy(
                    sizeBytes = currentSize,
                    lastModified = currentMtime,
                    contentHash = computed,
                    indexedAt = System.currentTimeMillis(),
                ),
            ),
        )
        return computed
    }

    // --- Mapping -------------------------------------------------------------

    private fun toFileItem(entry: FileIndexEntry): FileItem = FileItem(
        path = entry.path,
        name = entry.name,
        isDirectory = entry.isDirectory,
        sizeBytes = entry.sizeBytes,
        lastModified = entry.lastModified,
        type = runCatching { FileType.valueOf(entry.typeName) }.getOrDefault(FileType.OTHER),
        extension = entry.extension,
        mimeType = null,
        isHidden = entry.name.startsWith("."),
        childCount = null,
    )

    private fun toEntry(item: FileItem, indexedAt: Long): FileIndexEntry = FileIndexEntry(
        path = item.path,
        parentPath = item.parentPath ?: "",
        name = item.name,
        sizeBytes = item.sizeBytes,
        lastModified = item.lastModified,
        typeName = item.type.name,
        isDirectory = item.isDirectory,
        extension = item.extension,
        contentHash = null,
        indexedAt = indexedAt,
    )

    private companion object {
        /** 64 KiB read window for streamed hashing. */
        const val DEFAULT_HASH_BUFFER = 64 * 1024

        /** Lowercase hex alphabet used by [toHexString]. */
        val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}
