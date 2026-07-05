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
            val now = System.currentTimeMillis()
            dao.upsertAll(items.map { toEntry(it, now) })
            dao.deleteStale(parentPath, items.map { it.path })
        }

    override suspend fun upsert(items: List<FileItem>) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        dao.upsertAll(items.map { toEntry(it, now) })
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
            removeByPath(fromPath)
            indexFile(toItem)
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
     * Returns the content hash for an indexed [entry], preferring the value already
     * stored on the row, then the cached hash for the file's current identity, and
     * finally a freshly computed streamed SHA-1 (which is written back to the index).
     * Null when the file cannot be read.
     */
    private suspend fun hashForEntry(entry: FileIndexEntry): String? {
        entry.contentHash?.let { return it }
        dao.hashIfUnchanged(entry.path, entry.sizeBytes, entry.lastModified)?.let { return it }
        val computed = computeHash(entry.path) ?: return null
        dao.upsertAll(
            listOf(entry.copy(contentHash = computed, indexedAt = System.currentTimeMillis())),
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
