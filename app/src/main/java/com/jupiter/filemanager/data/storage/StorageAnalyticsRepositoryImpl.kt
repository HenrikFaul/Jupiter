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
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
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
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : StorageAnalyticsRepository {

    /**
     * Walks the primary volume, accumulating per-[StorageCategory] usage by
     * mapping each regular file's [FileType] (and a Downloads-path heuristic)
     * into a category bucket.
     */
    override suspend fun storageOverview(): AppResult<StorageOverview> = withContext(dispatcher) {
        val volume: StorageVolumeInfo = volumeProvider.volumes().firstOrNull { it.isPrimary }
            ?: volumeProvider.volumes().firstOrNull()
            ?: return@withContext AppResult.Failure(
                AppError.Io("No storage volume is available to analyze."),
            )

        try {
            // Accumulators keyed by category; preserves enum declaration order on output.
            val sizeByCategory = LinkedHashMap<StorageCategory, Long>()
            val countByCategory = LinkedHashMap<StorageCategory, Int>()
            var totalAnalyzedBytes = 0L

            for (file in dataSource.walkTopDown(volume.rootPath)) {
                currentCoroutineContext().ensureActive()

                if (!isRegularFile(file)) continue

                val size = safeLength(file)
                val category = categoryFor(file)

                sizeByCategory[category] = (sizeByCategory[category] ?: 0L) + size
                countByCategory[category] = (countByCategory[category] ?: 0) + 1
                totalAnalyzedBytes += size
            }

            // Emit a stable, complete set of categories (zero-usage ones included)
            // so consumers can render a deterministic breakdown.
            val categories = StorageCategory.entries.map { category ->
                CategoryUsage(
                    category = category,
                    sizeBytes = sizeByCategory[category] ?: 0L,
                    fileCount = countByCategory[category] ?: 0,
                )
            }

            AppResult.Success(
                StorageOverview(
                    volume = volume,
                    categories = categories,
                    totalAnalyzedBytes = totalAnalyzedBytes,
                ),
            )
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
     * Streams every regular file under [rootPath] whose length is at least
     * [minSizeBytes], emitting matches lazily as the tree is walked.
     */
    override fun findLargeFiles(rootPath: String, minSizeBytes: Long): Flow<FileItem> = flow {
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
    override fun findDuplicates(rootPath: String): Flow<DuplicateGroup> = flow {
        // First pass: group candidate files by size. Files with a unique size cannot
        // be duplicates and are discarded immediately to bound hashing work.
        val bySize = LinkedHashMap<Long, MutableList<File>>()
        for (file in dataSource.walkTopDown(rootPath)) {
            currentCoroutineContext().ensureActive()

            if (!isRegularFile(file)) continue
            val size = safeLength(file)
            if (size <= 0L) continue // skip empties; trivially "equal" but not useful

            bySize.getOrPut(size) { mutableListOf() }.add(file)
        }

        // Second pass: within each size bucket, confirm equality by content hash.
        for ((_, candidates) in bySize) {
            currentCoroutineContext().ensureActive()
            if (candidates.size < 2) continue

            val byHash = LinkedHashMap<String, MutableList<File>>()
            for (file in candidates) {
                currentCoroutineContext().ensureActive()
                val hash = hashFile(file) ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(file)
            }

            for ((hash, group) in byHash) {
                if (group.size < 2) continue
                emit(
                    DuplicateGroup(
                        hash = hash,
                        files = group.map { dataSource.toFileItem(it) },
                    ),
                )
            }
        }
    }.flowOn(dispatcher)

    // region Internals -----------------------------------------------------------

    /**
     * Maps a file to its [StorageCategory], preferring an explicit Downloads-path
     * heuristic and otherwise translating its [FileType] classification.
     */
    private fun categoryFor(file: File): StorageCategory {
        if (isInDownloads(file)) return StorageCategory.DOWNLOADS

        return when (fileTypeOf(file)) {
            FileType.IMAGE -> StorageCategory.IMAGES
            FileType.VIDEO -> StorageCategory.VIDEOS
            FileType.AUDIO -> StorageCategory.AUDIO
            FileType.DOCUMENT, FileType.PDF -> StorageCategory.DOCUMENTS
            FileType.ARCHIVE -> StorageCategory.ARCHIVES
            FileType.APK -> StorageCategory.APPS
            FileType.FOLDER, FileType.CODE, FileType.OTHER -> StorageCategory.OTHER
        }
    }

    /** Derives the [FileType] of [file] via the shared data-source mapping. */
    private fun fileTypeOf(file: File): FileType =
        runCatching { dataSource.toFileItem(file).type }.getOrDefault(FileType.OTHER)

    /**
     * Heuristic: treat any file located within a path segment named "Download" or
     * "Downloads" as belonging to the DOWNLOADS category.
     */
    private fun isInDownloads(file: File): Boolean {
        val path = runCatching { file.absolutePath }.getOrDefault("")
        if (path.isEmpty()) return false
        val lower = path.lowercase()
        return lower.contains("/download/") || lower.contains("/downloads/")
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
        val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}
