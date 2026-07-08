package com.jupiter.filemanager.data.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Runs the [DedupReconciler] as a short background job. Enqueued (expedited, KEEP) whenever the
 * app foregrounds, MediaStore signals a change, or the periodic refresh fires — so duplicate
 * detection catches files that arrived while the app was dead. Cheap when nothing is new.
 */
@HiltWorker
class DedupReconcileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reconciler: DedupReconciler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            reconciler.reconcile()
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "dedup-reconcile"
    }
}
