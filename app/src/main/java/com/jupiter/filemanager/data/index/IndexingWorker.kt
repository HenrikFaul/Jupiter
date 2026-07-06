package com.jupiter.filemanager.data.index

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jupiter.filemanager.data.file.FileSystemDataSource
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.IndexStateRepository
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
    private val mediaStoreIndexSource: MediaStoreIndexSource,
    private val indexRepository: FileIndexRepository,
    private val storageAccess: StorageAccessManager,
    private val indexStateRepository: IndexStateRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Without broad storage access neither MediaStore nor a walk can see the shared
        // volume, so there is nothing meaningful to index — treat as a successful no-op.
        if (!storageAccess.hasAllFilesAccess()) {
            return Result.success(outputOf(0, 0))
        }

        // Promote to a FOREGROUND service with an ongoing notification. A full-volume survey
        // takes real time; as a plain background job Doze / background-execution limits throttle
        // and kill it so it never completes. As a foreground service it runs to completion (and
        // the notification shows the user it is working). Best-effort: if the OS refuses (e.g.
        // notifications blocked) the survey still runs, just background-throttled.
        runCatching { setForeground(foregroundInfo(0)) }

        // Open a new scan GENERATION and mark the index RUNNING. Completeness is a STATE
        // (in Room), not a row count: if this worker is killed mid-build the state stays
        // RUNNING and the previous COMPLETE generation remains usable, so a partial index is
        // never treated as finished. Every row this survey writes is stamped with `gen`.
        val gen = indexStateRepository.beginScan()

        return try {
            // PHASE 1 — fast SEED from MediaStore (Android's own pre-scanned index): a single
            // cursor with zero per-file syscalls, covering the media/downloads/documents the
            // system already catalogued. This makes partial results appear in seconds.
            val seeded = indexFromMediaStore(gen)

            // PHASE 2 — RECONCILIATION walk: MediaStore is a media CATALOG, not a full-storage
            // mirror — it omits directories, .nomedia content, and other non-scanned files. So
            // walk the accessible tree and add everything the seed missed (files AND folders),
            // skipping paths already indexed (one cheap `stat` per new entry). Only after this
            // does the index reflect the WHOLE volume, so COMPLETE means "fully surveyed", not
            // just "media catalogued".
            val added = reconcileFilesystem(PRIMARY_STORAGE_ROOT, gen)

            // Sweep rows a PREVIOUS survey saw but this one did not (deleted files), then mark
            // the index COMPLETE for this generation.
            indexRepository.sweepStaleGenerations(gen)
            val total = seeded + added
            indexStateRepository.completeScan(gen, total.toLong())
            Result.success(outputOf(total, total))
        } catch (cancellation: CancellationException) {
            // Cooperative cancellation: leave the index NOT complete (state stays RUNNING) so
            // it rebuilds; the previous complete generation is untouched (no sweep ran).
            throw cancellation
        } catch (e: Exception) {
            // Transient/unexpected IO — record FAILED and let WorkManager retry with backoff.
            runCatching { indexStateRepository.failScan(e.message) }
            Result.retry()
        }
    }

    /**
     * Supplies the notification WorkManager shows while this worker runs as a foreground /
     * expedited service (also called by WorkManager for expedited requests on API < 31).
     */
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0)

    /** Builds the ongoing foreground notification, showing the running file count. */
    private fun foregroundInfo(scannedSoFar: Int): ForegroundInfo {
        ensureNotificationChannel()
        val text = if (scannedSoFar > 0) {
            "Indexing storage — $scannedSoFar files"
        } else {
            "Indexing storage…"
        }
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Jupiter")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** Best-effort refresh of the foreground notification with the current file count. */
    private suspend fun updateNotification(scannedSoFar: Int) {
        runCatching { setForeground(foregroundInfo(scannedSoFar)) }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Storage indexing",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }
    }

    /**
     * Bulk-indexes the shared volume from [MediaStoreIndexSource] in [BATCH_SIZE] chunks,
     * publishing a real indexed/total progress after each batch (the total comes from an
     * instant MediaStore count so the UI can show a percentage). Returns the number of
     * files indexed. Cancellation propagates out of the source's row loop.
     */
    private suspend fun indexFromMediaStore(generation: Long): Int {
        val total = mediaStoreIndexSource.count()
        setProgress(outputOf(0, total))
        var indexed = 0
        mediaStoreIndexSource.forEachBatch(BATCH_SIZE) { batch ->
            indexRepository.upsertScanned(batch, generation)
            indexed += batch.size
            // The count() denominator is only APPROXIMATE (directories/excluded/null-path
            // rows are skipped while streaming), so clamp to avoid >100% during the run.
            setProgress(outputOf(indexed, maxOf(total, indexed)))
        }
        return indexed
    }

    /**
     * Reconciliation walk over [rootPath]: adds every accessible entry the MediaStore seed
     * did NOT already index — non-media files AND directories — stamping each with
     * [generation]. Entries already in the index (the media majority) are skipped WITHOUT a
     * stat, and each new entry costs a single `stat` via [FileSystemDataSource.toIndexItem],
     * so this is far cheaper than the old full [toFileItem] crawl. Excluded path segments
     * (see [EXCLUDED_SEGMENTS]) are skipped. Returns the number of entries added. Honours
     * cancellation and publishes indeterminate progress (the tree total is unknown up-front).
     */
    private suspend fun reconcileFilesystem(rootPath: String, generation: Long): Int {
        // Skip only what THIS generation already wrote (the MediaStore seed). Rows from an
        // interrupted earlier generation are intentionally NOT skipped — they get re-stamped
        // to `generation` so a resumed survey re-sees (and keeps) them instead of the final
        // sweep deleting its own partial progress.
        val known = runCatching { indexRepository.pathsAtGeneration(generation) }
            .getOrDefault(emptySet())
        val batch = ArrayList<FileItem>(BATCH_SIZE)
        var total = 0

        for (file in fileSystemDataSource.walkTopDown(rootPath)) {
            // Bail out promptly if the system asked us to stop, or the coroutine
            // was cancelled, so a huge tree never keeps a stopped worker alive.
            if (isStopped) break
            currentCoroutineContext().ensureActive()

            if (isExcluded(file)) continue
            // Already written this generation (e.g. the MediaStore seed) — skip without a stat.
            if (file.absolutePath in known) continue

            val item = fileSystemDataSource.toIndexItem(file) ?: continue
            batch.add(item)

            if (batch.size >= BATCH_SIZE) {
                indexRepository.upsertScanned(batch.toList(), generation)
                total += batch.size
                batch.clear()
                setProgress(outputOf(total, 0))
                // Keep the foreground notification alive + informative so the OS doesn't drop
                // the service and the user sees the survey progressing.
                updateNotification(total)
            }
        }

        // Flush the trailing partial batch (unless we were stopped mid-walk).
        if (batch.isNotEmpty() && !isStopped) {
            indexRepository.upsertScanned(batch.toList(), generation)
            total += batch.size
            setProgress(outputOf(total, 0))
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

    private fun outputOf(count: Int, total: Int): Data =
        Data.Builder()
            .putInt(KEY_INDEXED_COUNT, count)
            .putInt(KEY_TOTAL_COUNT, total)
            .build()

    companion object {
        /** Unique work name for a full index rebuild. */
        const val UNIQUE_WORK_NAME: String = "file-index-rebuild"

        /** Output/progress-[Data] key carrying the number of indexed entries. */
        const val KEY_INDEXED_COUNT: String = "indexed_count"

        /** Output/progress-[Data] key carrying the estimated total to index (for a %). */
        const val KEY_TOTAL_COUNT: String = "total_count"

        /** Notification channel + id for the foreground survey notification. */
        private const val NOTIFICATION_CHANNEL_ID: String = "storage-indexing"
        private const val NOTIFICATION_ID: Int = 4242

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
