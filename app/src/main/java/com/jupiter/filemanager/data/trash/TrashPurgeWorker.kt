package com.jupiter.filemanager.data.trash

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.repository.TrashRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Enforces the Recycle Bin retention policy: permanently deletes items that have sat in the trash
 * longer than the user-chosen "auto-delete after N days" window.
 *
 * OFF by default (`trashAutoDeleteDays == 0`): with no explicit opt-in nothing is ever auto-deleted,
 * so a trashed file is only removed when the user empties the bin or the retention window they set
 * elapses. Runs on a daily periodic cadence (and once, promptly, when the setting is changed).
 */
@HiltWorker
class TrashPurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsDataStore,
    private val trashRepository: TrashRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching {
            val days = settings.trashAutoDeleteDays.first()
            if (days > 0) {
                val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
                trashRepository.purgeOlderThan(cutoff)
            }
        }
        // Retention is best-effort housekeeping; a hiccup should never surface as a failed job.
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "trash-auto-purge-periodic"
        const val UNIQUE_ONE_TIME_WORK_NAME = "trash-auto-purge-now"
    }
}
