package com.jupiter.filemanager.data.trash

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, crash-safe façade over [WorkManager] for the Recycle-Bin auto-delete ([TrashPurgeWorker]).
 *
 * Centralizes the unique-work names and enqueue policies so callers (app start, the Settings toggle)
 * don't need to know the worker wiring. Every enqueue is best-effort — a scheduling hiccup can never
 * crash the caller.
 */
@Singleton
class TrashScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * Ensures the daily auto-purge is scheduled (KEEP: an existing schedule is left untouched).
     * The worker itself no-ops when auto-delete is off, so it is safe to keep scheduled always.
     * Battery-not-low so retention housekeeping never drains a dying phone.
     */
    fun schedulePeriodicPurge() {
        try {
            workManager.enqueueUniquePeriodicWork(
                TrashPurgeWorker.UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        } catch (_: Exception) {
            // Best-effort.
        }
    }

    /**
     * Runs a purge promptly (e.g. right after the user picks/shortens the retention window), so the
     * change takes effect without waiting for the next daily window. REPLACE coalesces rapid changes.
     */
    fun purgeNow() {
        try {
            workManager.enqueueUniqueWork(
                TrashPurgeWorker.UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TrashPurgeWorker>().build(),
            )
        } catch (_: Exception) {
            // Best-effort.
        }
    }
}
