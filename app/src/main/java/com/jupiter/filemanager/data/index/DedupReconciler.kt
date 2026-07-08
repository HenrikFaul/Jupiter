package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.data.permission.StorageAccessGate
import com.jupiter.filemanager.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catches up duplicate detection on files that arrived while the app was NOT running.
 *
 * The real-time [DownloadIndexObserver] only fires while the process is alive, so a file
 * downloaded/received/captured with Jupiter closed would never be checked. This reconciler
 * closes that gap: it asks [NewFileSource] for every file whose MediaStore `_id` is newer than a
 * persisted checkpoint, runs each through [DuplicateDetector], and advances the checkpoint. It is
 * cheap when nothing is new (one indexed cursor, zero rows), so it is safe to run on every app
 * foreground, on each observer signal, and periodically.
 *
 * Checkpoint keyed on `_id` (unique + strictly increasing) so pagination is gap-free even for a
 * bulk import of hundreds of files in the same second (a 1-second-granularity date would skip
 * rows past the page boundary).
 *
 * Baseline rule: on the very first run it does NOT alert on the pre-existing library — it records
 * the current max `_id` as the baseline. Crucially it establishes the baseline ONLY when storage
 * is actually readable and MediaStore returns a real id; a fresh install that triggers a reconcile
 * before storage permission is granted must NOT pin a low baseline, or the whole library would
 * later re-alert. Only files that arrive AFTER a real baseline produce alerts; existing duplicates
 * are surfaced by the Duplicates screen and the perceptual backfill instead of an alert storm.
 */
@Singleton
class DedupReconciler @Inject constructor(
    private val newFileSource: NewFileSource,
    private val arrivalInspector: ArrivalInspector,
    private val checkpointStore: DedupCheckpointStore,
    private val storageAccess: StorageAccessGate,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    // Serializes concurrent reconciles (observer + foreground + worker can overlap) so the
    // checkpoint advances cleanly and a file is never double-processed in one window.
    private val mutex = Mutex()

    /**
     * Processes all files newer than the checkpoint through the detector and advances the
     * checkpoint. Returns the number of files inspected. Never throws.
     */
    suspend fun reconcile(): Int = withContext(dispatcher) {
        if (mutex.isLocked) return@withContext 0 // a reconcile is already in flight; skip.
        mutex.withLock {
            runCatching { reconcileLocked() }.getOrDefault(0)
        }
    }

    private suspend fun reconcileLocked(): Int {
        if (!checkpointStore.isIndexingEnabled()) return 0
        // Without broad storage access MediaStore returns nothing/throws; skip entirely so we
        // never establish a bogus baseline before the user has granted access.
        if (!storageAccess.hasFullAccess()) return 0

        val checkpoint = checkpointStore.getCheckpointId()
        if (checkpoint <= 0L) {
            // Establish the baseline WITHOUT alerting on the existing library — but ONLY when
            // MediaStore actually returned a real id. A 0 here means empty OR not-yet-readable;
            // either way, leave the checkpoint at 0 and retry on a later trigger (once a real
            // library is visible, the baseline is set correctly and nothing is retro-alerted).
            val baseline = newFileSource.maxObservedId()
            if (baseline > 0L) checkpointStore.setCheckpointId(baseline)
            return 0
        }

        var since = checkpoint
        var inspected = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = newFileSource.queryNewSince(since, BATCH_SIZE)
            if (batch.isEmpty()) break

            var batchMax = since
            for (newFile in batch) {
                currentCoroutineContext().ensureActive()
                arrivalInspector.onFileArrived(newFile.item)
                inspected++
                batchMax = maxOf(batchMax, newFile.id) // ids are unique + strictly increasing
            }

            since = DedupCheckpoint.advance(since, batchMax)
            checkpointStore.setCheckpointId(since)

            if (batch.size < BATCH_SIZE) break // drained
            if (inspected >= MAX_PER_RUN) break // fairness cap; next trigger continues
        }
        return inspected
    }

    private companion object {
        const val BATCH_SIZE = 200

        /** Cap per run so a huge backlog (first foreground after a long absence) stays bounded. */
        const val MAX_PER_RUN = 2_000
    }
}

/** Pure checkpoint-advance math, extracted so the loop-termination guarantee is unit-tested. */
object DedupCheckpoint {
    /**
     * The next checkpoint after processing a batch whose greatest `_id` was [batchMax], given the
     * current [since]. Advances to [batchMax] when it moved forward; otherwise bumps by one (a
     * defensive guard — with unique ids `batchMax` is always > [since] for a non-empty batch, so
     * this branch is unreachable in practice but prevents any theoretical re-query loop).
     */
    fun advance(since: Long, batchMax: Long): Long = if (batchMax > since) batchMax else since + 1L
}
