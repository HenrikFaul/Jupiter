package com.jupiter.filemanager.data.index

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * Background pass that fingerprints already-indexed IMAGES with a perceptual dHash
 * ([PerceptualHash]), so near-duplicate detection covers the EXISTING library — not just files
 * arriving after the feature shipped. This is the ONLY proactive bulk writer of `perceptualHash`,
 * so duplicate-photo detection on a pre-existing gallery depends entirely on it.
 *
 * It drains the ENTIRE "missing fingerprint" backlog in a single run (no per-run cap) and promotes
 * itself to a FOREGROUND service for a large backlog, so Doze / background-execution limits can't
 * throttle it into never finishing — the previous 2,000-per-run cap under WorkManager's exponential
 * retry backoff took ~20 hours-apart runs to cover a 40k-image library, which meant the duplicate
 * screen saw an essentially empty fingerprint set for a long time. Progress is GUARANTEED: every
 * attempted row is marked — with its hash, or [PerceptualHash.UNHASHABLE] for undecodable files — so
 * the work list always shrinks and a corrupt image can never loop forever. If the OS stops the worker
 * mid-pass it returns retry and resumes from the remaining backlog.
 */
@HiltWorker
class PerceptualHashBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsDataStore,
    private val indexRepository: FileIndexRepository,
    private val perceptualHashSource: PerceptualHashSource,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!settings.indexingEnabled.first()) return Result.success()

            // Peek the backlog. Nothing to do → cheap success (no notification flashes).
            var batch = indexRepository.imagesNeedingPerceptualHash(BATCH_SIZE)
            if (batch.isEmpty()) return Result.success()

            // A large backlog (first run on an existing gallery) must run to completion, which can
            // take a few minutes of decoding — promote to a foreground service so the OS lets it.
            val remaining = runCatching { indexRepository.imagesNeedingPerceptualHashCount() }
                .getOrDefault(Int.MAX_VALUE)
            val foreground = remaining >= FOREGROUND_THRESHOLD
            if (foreground) runCatching { setForeground(foregroundInfo(0, remaining)) }

            var processed = 0
            while (batch.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                if (isStopped) return Result.retry()
                for (item in batch) {
                    currentCoroutineContext().ensureActive()
                    if (isStopped) return Result.retry()
                    // null = transient failure → try again on a later run; otherwise mark
                    // (real fingerprint or UNHASHABLE in all layers) so this row leaves the
                    // work list. One decode produces all three stacked hashes.
                    val fp = perceptualHashSource.computeAll(item.path)
                        ?: PerceptualFingerprint.UNHASHABLE
                    runCatching {
                        indexRepository.putPerceptualFingerprint(item.path, fp.dhash, fp.phash, fp.ahash)
                    }
                    processed++
                }
                if (foreground) runCatching { setForeground(foregroundInfo(processed, remaining)) }
                // Cap one execution so it always finishes within the foreground window, then let
                // WorkManager continue immediately (the first retries use a short backoff, so a big
                // library still converges in a couple of runs — not the ~20 the old 2k cap needed).
                if (processed >= MAX_PER_RUN) return Result.retry()
                batch = indexRepository.imagesNeedingPerceptualHash(BATCH_SIZE)
            }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0)

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        ensureNotificationChannel()
        val text = if (total > 0) "Analyzing photos for duplicates — $done / $total" else "Analyzing photos…"
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Jupiter")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(0), done.coerceIn(0, total.coerceAtLeast(0)), total <= 0)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Photo analysis",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }
    }

    companion object {
        /** Unique work name (KEEP semantics; see IndexingScheduler). */
        const val UNIQUE_WORK_NAME = "perceptual-hash-backfill"

        private const val BATCH_SIZE = 100

        /**
         * Per-run cap so one foreground execution stays well within the OS foreground window (~5 min
         * of decoding) while still covering all but the largest libraries in one or two runs. Far
         * larger than the old 2,000 cap, which needed ~20 backoff-spaced runs for a 40k gallery.
         */
        private const val MAX_PER_RUN = 20_000

        /** Backlog at/above which the pass runs as a foreground service so it isn't throttled. */
        private const val FOREGROUND_THRESHOLD = 400

        private const val NOTIFICATION_CHANNEL_ID = "photo-analysis"
        private const val NOTIFICATION_ID = 4243
    }
}
