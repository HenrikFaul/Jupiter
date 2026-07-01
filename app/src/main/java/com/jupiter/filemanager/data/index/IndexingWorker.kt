package com.jupiter.filemanager.data.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.jupiter.filemanager.data.file.FileSystemDataSource
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * Background worker that (re)builds the persistent Room file index by walking the
 * primary external-storage tree once and upserting each entry's metadata.
 *
 * Enqueued explicitly via [IndexingScheduler.rebuildNow] under a unique name, so a
 * rebuild never stacks or runs on a hidden schedule. The walk is delegated to
 * [FileSystemDataSource.walkTopDown] (which already skips unreadable directories and
 * guards symlink loops), and each readable entry is mapped to a [FileItem] via
 * [FileSystemDataSource.toFileItem] and flushed to [FileIndexRepository.upsert] in
 * bounded batches so memory stays flat even on very large trees.
 *
 * The worker only indexes metadata here — content hashing is intentionally left to a
 * future pass, and any already-persisted hash is preserved by the repository's upsert.
 *
 * Constructed by Hilt's `HiltWorkerFactory` (wired via
 * [com.jupiter.filemanager.JupiterApp]'s `Configuration.Provider`). It is fully
 * cooperative with cancellation (honouring both [isStopped] and coroutine cancellation),
 * bounded in memory, and exception-safe: transient IO failures surface as
 * [Result.retry] while a completed pass reports [Result.success] with the number of
 * indexed entries in output [Data].
 */
@HiltWorker
class IndexingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fileSystemDataSource: FileSystemDataSource,
    private val indexRepository: FileIndexRepository,
    private val storageAccess: StorageAccessManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Without broad storage access the walk would only ever see app-scoped dirs,
        // so there is nothing meaningful to index — treat as a successful no-op.
        if (!storageAccess.hasAllFilesAccess()) {
            return Result.success(outputOf(0))
        }

        return try {
            val indexed = indexTree(PRIMARY_STORAGE_ROOT)
            Result.success(outputOf(indexed))
        } catch (cancellation: CancellationException) {
            // Cooperative cancellation: let WorkManager handle stoppage, don't retry.
            throw cancellation
        } catch (_: Exception) {
            // Transient/unexpected IO — allow WorkManager to retry with backoff.
            Result.retry()
        }
    }

    /**
     * Walks [rootPath] top-down, mapping each readable entry to a [FileItem] and
     * upserting into the index in [BATCH_SIZE] chunks. Entries under excluded path
     * segments (see [EXCLUDED_SEGMENTS]) are skipped. Returns the number of entries
     * upserted. Honours cancellation between batches and publishes progress.
     */
    private suspend fun indexTree(rootPath: String): Int {
        val batch = ArrayList<FileItem>(BATCH_SIZE)
        var total = 0

        for (file in fileSystemDataSource.walkTopDown(rootPath)) {
            // Bail out promptly if the system asked us to stop, or the coroutine
            // was cancelled, so a huge tree never keeps a stopped worker alive.
            if (isStopped) break
            currentCoroutineContext().ensureActive()

            if (isExcluded(file)) continue

            val item = try {
                fileSystemDataSource.toFileItem(file)
            } catch (_: SecurityException) {
                continue
            } catch (_: RuntimeException) {
                continue
            }

            batch.add(item)

            if (batch.size >= BATCH_SIZE) {
                indexRepository.upsert(batch.toList())
                total += batch.size
                batch.clear()
                setProgress(outputOf(total))
            }
        }

        // Flush the trailing partial batch (unless we were stopped mid-walk).
        if (batch.isNotEmpty() && !isStopped) {
            indexRepository.upsert(batch.toList())
            total += batch.size
            setProgress(outputOf(total))
        }

        return total
    }

    /**
     * True when [file]'s path contains one of the [EXCLUDED_SEGMENTS] as a full path
     * segment. Matching on delimited segments (rather than raw substring) avoids
     * accidentally excluding files that merely contain the token in their name.
     */
    private fun isExcluded(file: File): Boolean {
        val path = file.absolutePath
        return EXCLUDED_SEGMENTS.any { segment ->
            path.contains("/$segment/") || path.endsWith("/$segment")
        }
    }

    private fun outputOf(count: Int): Data =
        Data.Builder().putInt(KEY_INDEXED_COUNT, count).build()

    companion object {
        /** Unique work name for a full index rebuild. */
        const val UNIQUE_WORK_NAME: String = "file-index-rebuild"

        /** Output/progress-[Data] key carrying the number of indexed entries. */
        const val KEY_INDEXED_COUNT: String = "indexed_count"

        /** Root of the primary emulated external storage on modern Android. */
        private const val PRIMARY_STORAGE_ROOT: String = "/storage/emulated/0"

        /** Number of entries buffered before a single batched upsert. */
        private const val BATCH_SIZE: Int = 500

        /**
         * Path segments never worth indexing: sandboxed app data/obb (usually
         * inaccessible and noisy) and thumbnail/trash caches.
         */
        private val EXCLUDED_SEGMENTS: List<String> = listOf(
            "Android/data",
            "Android/obb",
            ".thumbnails",
            ".trashed",
        )
    }
}
