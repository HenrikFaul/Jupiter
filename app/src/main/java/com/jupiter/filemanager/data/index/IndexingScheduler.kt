package com.jupiter.filemanager.data.index

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, crash-safe façade over [WorkManager] for the file-index rebuild job.
 *
 * Centralizes the unique-work name and enqueue policy so callers (e.g. the Settings
 * screen's "Rebuild index" action) don't need to know about [IndexingWorker] wiring.
 * A rebuild is enqueued as a single [ExistingWorkPolicy.REPLACE] one-time request under
 * [IndexingWorker.UNIQUE_WORK_NAME], so tapping repeatedly restarts the current run
 * rather than stacking duplicates.
 */
@Singleton
class IndexingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * Enqueues (or restarts) a full index rebuild. Any failure to enqueue is swallowed
     * so a scheduling hiccup can never crash the caller's screen.
     */
    fun rebuildNow() {
        try {
            workManager.enqueueUniqueWork(
                IndexingWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                buildRequest(),
            )
        } catch (_: Exception) {
            // Best-effort: indexing is an optimization, never critical-path.
        }
    }

    /**
     * Builds the survey request as EXPEDITED so it starts promptly instead of waiting for a
     * background window; the worker itself then promotes to a foreground service
     * ([IndexingWorker.getForegroundInfo]) so a long full-volume walk runs to completion.
     * [OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST] means that if the expedited quota
     * is exhausted it still runs (as a normal request) rather than being dropped.
     */
    private fun buildRequest() =
        OneTimeWorkRequestBuilder<IndexingWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

    /**
     * Ensures a background index survey runs, WITHOUT restarting one that is already
     * pending/running ([ExistingWorkPolicy.KEEP]). Call this on app start so the initial
     * "thorough survey" is built once in the background — after which the real-time delta
     * hooks keep it live, so opening the app never waits on a deep scan. Unlike
     * [rebuildNow] (the explicit user "Rebuild" action) this never interrupts progress.
     */
    fun ensureIndexed() {
        try {
            workManager.enqueueUniqueWork(
                IndexingWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                buildRequest(),
            )
        } catch (_: Exception) {
            // Best-effort.
        }
    }

    /**
     * Cancels any pending/running index survey (e.g. when the user disables indexing), so a
     * long build does not keep running after the feature is turned off. Best-effort.
     */
    fun cancel() {
        try {
            workManager.cancelUniqueWork(IndexingWorker.UNIQUE_WORK_NAME)
        } catch (_: Exception) {
            // Best-effort.
        }
    }

    /**
     * Observes the current state of the rebuild job, emitting the single most recent
     * [WorkInfo] for the unique work (or null when it has never run). Backed by
     * WorkManager's Flow API so it stays in sync as the worker progresses.
     */
    fun observeStatus(): Flow<WorkInfo?> =
        workManager
            .getWorkInfosForUniqueWorkFlow(IndexingWorker.UNIQUE_WORK_NAME)
            .map { infos -> infos.maxByOrNull { it.state.ordinal } }
}
