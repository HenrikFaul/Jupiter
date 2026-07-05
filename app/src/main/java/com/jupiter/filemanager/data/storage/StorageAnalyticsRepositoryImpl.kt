package com.jupiter.filemanager.data.storage

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.file.FileSystemDataSource
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.model.StorageOverview
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Default [StorageAnalyticsRepository] backed by the local file system.
 *
 * All traversal and hashing work is delegated to the injected [IoDispatcher] so
 * the main thread is never blocked. Streaming operations ([findLargeFiles],
 * [findDuplicates]) are exposed as cold flows that emit incrementally and honor
 * cooperative cancellation while walking potentially large trees.
 */
@Singleton
class StorageAnalyticsRepositoryImpl @Inject constructor(
    private val dataSource: FileSystemDataSource,
    private val volumeProvider: StorageVolumeProvider,
    private val indexRepository: FileIndexRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : StorageAnalyticsRepository {

    /**
     * Walks the primary volume, accumulating per-[StorageCategory] usage by
     * mapping each regular file's [FileType] (and a Downloads-path heuristic)
     * into a category bucket.
     */
    override suspend fun storageOverview(preferIndex: Boolean): AppResult<StorageOverview> =
        withContext(dispatcher) {
        val volume: StorageVolumeInfo = volumeProvider.volumes().firstOrNull { it.isPrimary }
            ?: volumeProvider.volumes().firstOrNull()
            ?: return@withContext AppResult.Failure(
                AppError.Io("No storage volume is available to analyze."),
            )

        // Fast path: aggregate the overview straight from the persistent index once the
        // background survey has populated it, so opening the screen never re-walks storage.
        if (preferIndex && indexIsPopulated()) {
            val fromIndex = runCatching { overviewFromIndex(volume) }.getOrNull()
            if (fromIndex != null) return@withContext AppResult.Success(fromIndex)
            // On any index failure, fall through to the authoritative live walk below.
        }

        try {
            val accumulator = OverviewAccumulator()

            for (file in dataSource.walkTopDown(volume.rootPath)) {
                currentCoroutineContext().ensureActive()

                val path = safePath(file)
                if (isExcludedPath(path)) continue
                if (!isRegularFile(file)) continue

                // Name-only classification avoids the ~6 syscalls/file that building a
                // full FileItem costs; size is the only metadata read we still need.
                accumulator.add(
                    category = categoryForPath(path, file.name),
                    size = safeLength(file),
                )
            }

            AppResult.Success(accumulator.toOverview(volume))
        } catch (security: SecurityException) {
            AppResult.Failure(AppError.AccessDenied(volume.rootPath))
        } catch (error: Exception) {
            AppResult.Failure(
                AppError.Io(
                    detail = error.message ?: "Failed to analyze storage.",
                    cause = error,
                ),
            )
        }
    }

    /**
     * Incremental variant of [storageOverview]: walks the primary volume once and
     * emits a partial [StorageOverview] roughly every [OVERVIEW_EMIT_FILES] files
     * or [OVERVIEW_EMIT_MILLIS] ms (whichever comes first), plus a final complete
     * emission. The first partial arrives within milliseconds so the UI can clear
     * its loading state immediately and refine the breakdown progressively.
     *
     * Uses the same heavy-dir exclusions and name-only classification as
     * [storageOverview], so the final emission is equivalent to its result.
     */
    override fun observeStorageOverview(): Flow<StorageOverview> = flow {
        val volume: StorageVolumeInfo = volumeProvider.volumes().firstOrNull { it.isPrimary }
            ?: volumeProvider.volumes().firstOrNull()
            ?: return@flow

        // Fast path: once the index is populated, emit a single instant overview from it
        // and stop — no progressive walk needed. Falls through to the walk on any failure.
        if (indexIsPopulated()) {
            val fromIndex = runCatching { overviewFromIndex(volume) }.getOrNull()
            if (fromIndex != null) {
                emit(fromIndex)
                return@flow
            }
        }

        val accumulator = OverviewAccumulator()
        var sinceLastEmit = 0
        var lastEmitAt = System.currentTimeMillis()

        for (file in dataSource.walkTopDown(volume.rootPath)) {
            currentCoroutineContext().ensureActive()

            val path = safePath(file)
            if (isExcludedPath(path)) continue
            if (!isRegularFile(file)) continue

            accumulator.add(
                category = categoryForPath(path, file.name),
                size = safeLength(file),
            )

            sinceLastEmit++
            val now = System.currentTimeMillis()
            if (sinceLastEmit >= OVERVIEW_EMIT_FILES ||
                now - lastEmitAt >= OVERVIEW_EMIT_MILLIS
            ) {
                emit(accumulator.toOverview(volume))
                sinceLastEmit = 0
                lastEmitAt = now
            }
        }

        // Final, authoritative snapshot (also covers the empty-tree case).
        emit(accumulator.toOverview(volume))
    }.flowOn(dispatcher)

    /**
     * Streams every regular file under [rootPath] whose length is at least
     * [minSizeBytes], emitting matches lazily as the tree is walked.
     */
    override fun findLargeFiles(
        rootPath: String,
        minSizeBytes: Long,
        preferIndex: Boolean,
    ): Flow<FileItem> = flow {
        // Fast path: serve the large-file list from the index (no walk) once populated.
        if (preferIndex && indexIsPopulated()) {
            val indexed = runCatching {
                indexRepository.largeFiles(minSizeBytes, LARGE_FILES_LIMIT)
            }.getOrNull()
            if (indexed != null) {
                for (item in indexed) {
                    currentCoroutineContext().ensureActive()
                    emit(item)
                }
                return@flow
            }
        }

        for (file in dataSource.walkTopDown(rootPath)) {
            currentCoroutineContext().ensureActive()

            if (!isRegularFile(file)) continue
            if (safeLength(file) < minSizeBytes) continue

            emit(dataSource.toFileItem(file))
        }
    }.flowOn(dispatcher)

    /**
     * Detects duplicate files under [rootPath].
     *
     * Files are first bucketed by size (a cheap necessary condition for equality),
     * then within each size bucket are hashed with streamed SHA-1 to confirm
     * identical content. Only groups containing two or more files are emitted.
     */
    override fun findDuplicates(rootPath: String, preferIndex: Boolean): Flow<DuplicateGroup> = flow {
        // Fast path: build duplicate groups from the index. Candidate size-buckets come
        // from the DB (no filesystem walk) and are confirmed by content hash, reusing the
        // hashes precomputed by the survey. Falls through to the live walk on any failure.
        if (preferIndex && indexIsPopulated()) {
            val indexed = runCatching {
                indexRepository.duplicateGroups(MIN_DUPLICATE_SIZE)
            }.getOrNull()
            if (indexed != null) {
                for (group in indexed) {
                    currentCoroutineContext().ensureActive()
                    emit(group)
                }
                return@flow
            }
        }

        // First pass: group candidate files by size. Files with a unique size cannot
        // be duplicates and are discarded immediately to bound hashing work. Tiny
        // files and heavy/noise directories are excluded to keep the work focused
        // on the buckets that actually matter for reclaiming space.
        val bySize = LinkedHashMap<Long, MutableList<File>>()
        for (file in dataSource.walkTopDown(rootPath)) {
            currentCoroutineContext().ensureActive()

            val path = safePath(file)
            if (isExcludedPath(path)) continue
            if (!isRegularFile(file)) continue

            val size = safeLength(file)
            if (size < MIN_DUPLICATE_SIZE) continue // skip tiny/empty: not worth hashing

            bySize.getOrPut(size) { mutableListOf() }.add(file)
        }

        // Second pass: resolve each size bucket independently and emit its duplicate
        // groups as soon as they are confirmed, so the UI gets results progressively
        // instead of waiting for every bucket to finish.
        for ((size, candidates) in bySize) {
            currentCoroutineContext().ensureActive()
            if (candidates.size < 2) continue

            resolveBucket(size, candidates, this)
        }
    }.flowOn(dispatcher)

    /**
     * Confirms duplicates within a single same-size [candidates] bucket and emits
     * each resolved [DuplicateGroup] into [collector].
     *
     * To avoid full-hashing files that only coincidentally share a size, candidates
     * are first split by a cheap prefix pre-hash (first [PREHASH_BYTES] bytes). Only
     * prefix groups with a genuine collision (two or more members) are escalated to
     * a full streamed SHA-1; singleton prefix groups are discarded without ever
     * reading the whole file.
     */
    private suspend fun resolveBucket(
        size: Long,
        candidates: List<File>,
        collector: FlowCollector<DuplicateGroup>,
    ) {
        // Optimization: a bucket of exactly two files can skip the prefix stage and
        // go straight to full hashing (the prefix would just duplicate that work).
        val byPrefix: Map<String, List<File>> = if (candidates.size == 2) {
            mapOf("" to candidates)
        } else {
            val grouped = LinkedHashMap<String, MutableList<File>>()
            for (file in candidates) {
                currentCoroutineContext().ensureActive()
                val prefix = prefixHash(file, size) ?: continue
                grouped.getOrPut(prefix) { mutableListOf() }.add(file)
            }
            grouped
        }

        for ((_, prefixGroup) in byPrefix) {
            currentCoroutineContext().ensureActive()
            if (prefixGroup.size < 2) continue // unique prefix -> cannot be a duplicate

            val byHash = LinkedHashMap<String, MutableList<File>>()
            for (file in prefixGroup) {
                currentCoroutineContext().ensureActive()
                // Reuse a cached full-content hash from the persistent index when the
                // file is byte-for-byte unchanged (matched on path + size + mtime), so
                // repeat scans skip re-reading every candidate. On a cache miss, hash
                // once and write it back for next time. The cache is best-effort — any
                // index failure falls through to a direct hash.
                val item = dataSource.toFileItem(file)
                val cached = runCatching {
                    indexRepository.hashForUnchanged(item.path, item.sizeBytes, item.lastModified)
                }.getOrNull()
                val hash = cached ?: run {
                    val computed = hashFile(file) ?: return@run null
                    runCatching { indexRepository.putHash(item, computed) }
                    computed
                } ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(file)
            }

            for ((hash, group) in byHash) {
                if (group.size < 2) continue
                collector.emit(
                    DuplicateGroup(
                        hash = hash,
                        files = group.map { dataSource.toFileItem(it) },
                    ),
                )
            }
        }
    }

    // region Internals -----------------------------------------------------------

    /** True when the persistent index has files to serve; false on any probe failure. */
    private suspend fun indexIsPopulated(): Boolean =
        runCatching { indexRepository.isPopulated() }.getOrDefault(false)

    /**
     * Aggregates a [StorageOverview] from the persistent index instead of walking
     * storage: every indexed non-directory file is bucketed with the SAME
     * [categoryForPath] classification and [isExcludedPath] filter used by the live
     * walk, so the result is equivalent to a fresh scan (modulo index freshness).
     */
    private suspend fun overviewFromIndex(volume: StorageVolumeInfo): StorageOverview {
        val accumulator = OverviewAccumulator()
        for (item in indexRepository.allFiles()) {
            currentCoroutineContext().ensureActive()
            if (item.isDirectory) continue
            val path = item.path
            if (isExcludedPath(path)) continue
            accumulator.add(
                category = categoryForPath(path, item.name),
                size = item.sizeBytes,
            )
        }
        return accumulator.toOverview(volume)
    }

    /**
     * Mutable, order-preserving accumulator of per-[StorageCategory] usage. Shared
     * by [storageOverview] and [observeStorageOverview] so both produce identical
     * breakdowns; [toOverview] snapshots the current state into an immutable
     * [StorageOverview] (callable repeatedly for incremental emissions).
     */
    private class OverviewAccumulator {
        private val sizeByCategory = LinkedHashMap<StorageCategory, Long>()
        private val countByCategory = LinkedHashMap<StorageCategory, Int>()
        private var totalAnalyzedBytes = 0L

        fun add(category: StorageCategory, size: Long) {
            sizeByCategory[category] = (sizeByCategory[category] ?: 0L) + size
            countByCategory[category] = (countByCategory[category] ?: 0) + 1
            totalAnalyzedBytes += size
        }

        fun toOverview(volume: StorageVolumeInfo): StorageOverview {
            // Emit a stable, complete set of categories (zero-usage ones included)
            // so consumers can render a deterministic breakdown.
            val categories = StorageCategory.entries.map { category ->
                CategoryUsage(
                    category = category,
                    sizeBytes = sizeByCategory[category] ?: 0L,
                    fileCount = countByCategory[category] ?: 0,
                )
            }
            return StorageOverview(
                volume = volume,
                categories = categories,
                totalAnalyzedBytes = totalAnalyzedBytes,
            )
        }
    }

    /**
     * Maps a file to its [StorageCategory] using only its [path] and [name]
     * (no syscalls), preferring an explicit Downloads-path heuristic and otherwise
     * translating its name-only [FileType] classification.
     */
    private fun categoryForPath(path: String, name: String): StorageCategory {
        if (isInDownloads(path)) return StorageCategory.DOWNLOADS

        return when (dataSource.fileTypeFor(name, isDirectory = false)) {
            FileType.IMAGE -> StorageCategory.IMAGES
            FileType.VIDEO -> StorageCategory.VIDEOS
            FileType.AUDIO -> StorageCategory.AUDIO
            FileType.DOCUMENT, FileType.PDF -> StorageCategory.DOCUMENTS
            FileType.ARCHIVE -> StorageCategory.ARCHIVES
            FileType.APK -> StorageCategory.APPS
            FileType.FOLDER, FileType.CODE, FileType.OTHER -> StorageCategory.OTHER
        }
    }

    /**
     * Heuristic: treat any file located within a path segment named "Download" or
     * "Downloads" as belonging to the DOWNLOADS category.
     */
    private fun isInDownloads(path: String): Boolean {
        if (path.isEmpty()) return false
        val lower = path.lowercase()
        return lower.contains("/download/") || lower.contains("/downloads/")
    }

    /** Best-effort absolute path; empty string when it cannot be resolved. */
    private fun safePath(file: File): String =
        runCatching { file.absolutePath }.getOrDefault("")

    /**
     * True when [path] lies under a heavy or noise directory that analytics should
     * skip (app-private sandboxes and system-managed caches). Matching is done on
     * path segments to avoid false positives on similarly-named user folders.
     */
    private fun isExcludedPath(path: String): Boolean {
        if (path.isEmpty()) return false
        val lower = path.lowercase()
        return EXCLUDED_SEGMENTS.any { lower.contains(it) }
    }

    /** True when [file] is an ordinary, readable, non-directory file. */
    private fun isRegularFile(file: File): Boolean = try {
        file.isFile
    } catch (_: SecurityException) {
        false
    }

    /** Best-effort file length, treating inaccessible files as zero bytes. */
    private fun safeLength(file: File): Long = try {
        file.length()
    } catch (_: SecurityException) {
        0L
    }

    /**
     * Computes a streamed SHA-1 hash over the full contents of [file], reading in
     * fixed-size chunks so memory stays bounded regardless of file size. Returns
     * null when the file cannot be read.
     */
    private suspend fun hashFile(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            file.inputStream().use { stream ->
                val buffer = ByteArray(HASH_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            digest.digest().toHexString()
        } catch (_: SecurityException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    /**
     * Computes a cheap SHA-1 over only the first [PREHASH_BYTES] bytes of [file]
     * (bounded by [size]), used to split same-size candidates before committing to
     * a full-content hash. Returns null when the file cannot be read.
     *
     * For files no larger than the prefix window this reads the whole file, which is
     * acceptable since such files are tiny by definition.
     */
    private suspend fun prefixHash(file: File, size: Long): String? {
        return try {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            val limit = if (size in 1 until PREHASH_BYTES.toLong()) size.toInt() else PREHASH_BYTES
            val buffer = ByteArray(limit)
            file.inputStream().use { stream ->
                var offset = 0
                while (offset < limit) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer, offset, limit - offset)
                    if (read < 0) break
                    offset += read
                }
                if (offset > 0) digest.update(buffer, 0, offset)
            }
            digest.digest().toHexString()
        } catch (_: SecurityException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    /** Lowercase hexadecimal rendering of a digest byte array. */
    private fun ByteArray.toHexString(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    // endregion

    private companion object {
        const val HASH_ALGORITHM = "SHA-1"
        const val HASH_BUFFER_SIZE = 64 * 1024

        /** Bytes of file prefix used for the cheap pre-hash that splits same-size buckets. */
        const val PREHASH_BYTES = 8 * 1024

        /** Files smaller than this are never considered for duplicate detection. */
        const val MIN_DUPLICATE_SIZE = 4 * 1024L

        /** Upper bound on index-served large-file rows (matches the DAO query cap). */
        const val LARGE_FILES_LIMIT = 500

        /** Emit a partial overview at least every this many newly-counted files. */
        const val OVERVIEW_EMIT_FILES = 400

        /** Emit a partial overview at least this often (ms) during the walk. */
        const val OVERVIEW_EMIT_MILLIS = 250L

        /**
         * Lowercased path fragments whose presence marks a file as living in a
         * heavy/noise directory analytics should skip.
         */
        val EXCLUDED_SEGMENTS: List<String> = listOf(
            "/android/data/",
            "/android/obb/",
            "/.thumbnails/",
            "/.trashed",
        )

        val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}
