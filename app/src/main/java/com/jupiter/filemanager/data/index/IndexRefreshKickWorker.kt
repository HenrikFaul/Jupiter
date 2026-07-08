package com.jupiter.filemanager.data.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic "kicker" that keeps the file index FRESH in the background: every run simply
 * re-enqueues the one-time survey via [IndexingScheduler.ensureIndexed] (KEEP), so
 * - a survey already in flight is never restarted (no racing generations), and
 * - after a completed survey a fresh pass runs, picking up everything that changed while
 *   the app was closed (files deleted/added by other apps that the live MediaStore
 *   observer could not see).
 *
 * Deliberately NOT the [IndexingWorker] itself under a second unique name — two surveys
 * running concurrently would interleave generations and corrupt the stale-sweep. All work
 * is funneled through the single one-time unique name.
 */
@HiltWorker
class IndexRefreshKickWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsDataStore,
    private val indexingScheduler: IndexingScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching {
            if (settings.indexingEnabled.first()) {
                indexingScheduler.ensureIndexed()
                // Also catch up duplicate detection on the periodic cadence, so a file added
                // while the app stayed closed for days is still flagged.
                indexingScheduler.reconcileDedupNow()
            }
        }
        return Result.success()
    }
}
