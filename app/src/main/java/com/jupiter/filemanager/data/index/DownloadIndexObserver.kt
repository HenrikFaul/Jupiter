package com.jupiter.filemanager.data.index

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.jupiter.filemanager.core.util.StorageExclusions
import com.jupiter.filemanager.data.file.FileSystemDataSource
import com.jupiter.filemanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hybrid real-time trigger for files created outside Jupiter.
 *
 * MediaStore's [ContentObserver] remains the broad, lawful signal and [DedupReconciler] remains
 * the crash-safe source of truth. OEM download providers are not uniform, however: some expose a
 * non-pending MediaStore row before the bytes are complete, send no final provider update, or
 * heavily delay a short WorkManager job. For the common user-write locations we therefore add a
 * bounded recursive [FileObserver] safety net. It waits until a candidate is stable, then invokes
 * the same [ArrivalInspector] directly. This is deliberately hybrid: neither observer is trusted
 * alone, and the persisted reconciliation worker remains the process-death backup.
 */
@Singleton
class DownloadIndexObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val indexingScheduler: IndexingScheduler,
    private val dedupReconciler: DedupReconciler,
    private val arrivalInspector: ArrivalInspector,
    private val fileSystemDataSource: FileSystemDataSource,
    @IoDispatcher dispatcher: CoroutineDispatcher,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val trailingKick = Runnable { reconcileNowWithBackup() }
    private val directoryObservers = ConcurrentHashMap<String, FileObserver>()
    private val candidateJobs = ConcurrentHashMap<String, Job>()

    @Volatile
    private var observer: ContentObserver? = null

    /** Registers both observation layers. Idempotent and safe to retry after access is granted. */
    @Synchronized
    fun start() {
        if (observer == null) {
            val obs = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) = scheduleTrailingKick()
                override fun onChange(selfChange: Boolean) = scheduleTrailingKick()
            }
            val registered = runCatching {
                context.contentResolver.registerContentObserver(
                    MediaStore.Files.getContentUri("external"),
                    /* notifyForDescendants = */ true,
                    obs,
                )
            }.isSuccess
            if (registered) observer = obs
        }

        // Registration can happen before MANAGE_EXTERNAL_STORAGE is granted. Retrying start()
        // must therefore retry the filesystem layer even if ContentObserver is already alive.
        scope.launch { installCommonDirectoryObservers() }
        reconcileNowWithBackup()
    }

    /** Unregisters all live signals and pending candidate checks. Idempotent. */
    @Synchronized
    fun stop() {
        handler.removeCallbacks(trailingKick)
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        observer = null
        directoryObservers.values.forEach { runCatching { it.stopWatching() } }
        directoryObservers.clear()
        candidateJobs.values.forEach { it.cancel() }
        candidateJobs.clear()
    }

    /**
     * Every provider signal moves the check after the final update. The in-process pass is what
     * makes an alive app immediate on restrictive OEMs; WorkManager is retained as a delayed,
     * persistent trailing pass if the process dies or the provider settles later.
     */
    private fun scheduleTrailingKick() {
        handler.removeCallbacks(trailingKick)
        handler.postDelayed(trailingKick, MEDIASTORE_DEBOUNCE_MS)
    }

    private fun reconcileNowWithBackup() {
        indexingScheduler.ensureIndexed()
        scope.launch { runCatching { dedupReconciler.reconcile() } }
        indexingScheduler.reconcileDedupNow(initialDelayMillis = WORK_BACKUP_DELAY_MS)
    }

    /** Watches a bounded set of user-write trees rather than the whole 256 GB volume. */
    private fun installCommonDirectoryObservers() {
        commonRoots().forEach(::watchTree)
    }

    private fun commonRoots(): List<File> = listOf(
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_MOVIES,
        Environment.DIRECTORY_DOCUMENTS,
        Environment.DIRECTORY_MUSIC,
    ).mapNotNull { type ->
        runCatching { Environment.getExternalStoragePublicDirectory(type) }.getOrNull()
    }.distinctBy { it.absolutePath }

    /**
     * Adds one observer per directory so nested camera/messenger folders are covered. The hard
     * cap prevents a malformed/deep tree from consuming unbounded inotify watches.
     */
    private fun watchTree(root: File) {
        if (!root.isDirectory || directoryObservers.size >= MAX_DIRECTORY_OBSERVERS) return
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty() && directoryObservers.size < MAX_DIRECTORY_OBSERVERS) {
            val directory = queue.removeFirst()
            val path = directory.absolutePath
            if (!directory.isDirectory || StorageExclusions.isExcluded(path) ||
                directoryObservers.containsKey(path)
            ) {
                continue
            }

            val watcher = object : FileObserver(path, FILE_EVENT_MASK) {
                override fun onEvent(event: Int, relativePath: String?) {
                    if (relativePath.isNullOrBlank()) return
                    val candidate = File(directory, relativePath)
                    if ((event and (CREATE or MOVED_TO)) != 0 && candidate.isDirectory) {
                        // Some producers create an app-specific subfolder on first use.
                        scope.launch {
                            delay(NEW_DIRECTORY_SETTLE_MS)
                            watchTree(candidate)
                        }
                    } else if ((event and FILE_CANDIDATE_EVENTS) != 0) {
                        scheduleStableCandidate(candidate)
                    }
                }
            }
            val started = runCatching { watcher.startWatching() }.isSuccess
            if (started) {
                val existing = directoryObservers.putIfAbsent(path, watcher)
                if (existing != null) runCatching { watcher.stopWatching() }
            }

            runCatching { directory.listFiles() }
                .getOrNull()
                ?.asSequence()
                ?.filter { it.isDirectory }
                ?.forEach(queue::addLast)
        }
    }

    /**
     * CREATE can be the only OEM event and can precede the completed download by minutes. Recheck
     * until size + mtime remain unchanged across two full windows. CLOSE_WRITE/MOVED_TO simply
     * replace this job, so the final event produces a prompt check without duplicate work.
     */
    private fun scheduleStableCandidate(file: File) {
        val path = file.absolutePath
        if (StorageExclusions.isExcluded(path) || isTemporaryDownloadName(file.name)) return

        // Persist a process-death backup at signal time, before any debounce/stability wait. The
        // direct file path may still need minutes to settle, but killing the app must not erase the
        // only evidence that external storage changed.
        indexingScheduler.reconcileDedupNow(initialDelayMillis = WORK_BACKUP_DELAY_MS)

        val replacement = scope.launch {
            delay(CANDIDATE_DEBOUNCE_MS)
            var previous = snapshot(file)
            var stableWindows = 0
            repeat(MAX_STABILITY_WINDOWS) {
                delay(FILE_STABILITY_WINDOW_MS)
                val current = snapshot(file)
                if (current != null && current == previous) {
                    stableWindows++
                    if (stableWindows >= REQUIRED_STABLE_WINDOWS) {
                        val item = runCatching { fileSystemDataSource.toFileItem(file) }.getOrNull()
                        if (item != null && !item.isDirectory && item.canRead) {
                            val inspection = arrivalInspector.inspectArrival(item)
                            if (inspection is ArrivalInspectionResult.Retry) {
                                // The writer may have closed the descriptor before the decoder or
                                // FUSE layer can read final bytes. Keep polling this same candidate;
                                // do not let a transient null consume the durable delta checkpoint.
                                stableWindows = 0
                                previous = snapshot(file)
                                return@repeat
                            }
                            // Persist the corresponding MediaStore checkpoint when/if the OEM row
                            // becomes visible; a delayed worker is still queued for process death.
                            runCatching { dedupReconciler.reconcile() }
                            indexingScheduler.reconcileDedupNow(
                                initialDelayMillis = WORK_BACKUP_DELAY_MS,
                            )
                        }
                        return@launch
                    }
                } else {
                    stableWindows = 0
                }
                previous = current
            }
        }
        candidateJobs.put(path, replacement)?.cancel()
        replacement.invokeOnCompletion { candidateJobs.remove(path, replacement) }
    }

    private fun snapshot(file: File): CandidateSnapshot? = runCatching {
        if (!file.isFile || !file.canRead()) return@runCatching null
        CandidateSnapshot(size = file.length(), lastModified = file.lastModified())
    }.getOrNull()

    internal data class CandidateSnapshot(val size: Long, val lastModified: Long)

    internal companion object {
        private const val MEDIASTORE_DEBOUNCE_MS = 1_500L
        private const val WORK_BACKUP_DELAY_MS = 8_000L
        private const val CANDIDATE_DEBOUNCE_MS = 500L
        private const val FILE_STABILITY_WINDOW_MS = 1_500L
        private const val REQUIRED_STABLE_WINDOWS = 2
        private const val MAX_STABILITY_WINDOWS = 80 // about two minutes
        private const val NEW_DIRECTORY_SETTLE_MS = 250L
        private const val MAX_DIRECTORY_OBSERVERS = 1_024

        private const val FILE_CANDIDATE_EVENTS =
            FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        private const val FILE_EVENT_MASK = FILE_CANDIDATE_EVENTS or FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF

        internal fun isTemporaryDownloadName(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".crdownload") || lower.endsWith(".part") ||
                lower.endsWith(".partial") || lower.endsWith(".download")
        }
    }
}
