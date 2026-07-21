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
 * It visits up to 50k missing-fingerprint rows per run and promotes itself to a FOREGROUND service
 * for a large backlog, so the reported 40k-photo corpus fits in one contiguous attempt instead of
 * the old 2k/20k backoff-spaced runs. Crucially, a transient decode or
 * database failure is NOT written as [PerceptualHash.UNHASHABLE]: doing so turns a temporary OEM
 * decoder/provider hiccup into a permanent `Similar photos (0)` false negative. Truly undecodable
 * files are still marked with that sentinel by [PerceptualHashSource], while retryable rows remain
 * visible in the backlog and cause a bounded WorkManager retry.
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
            if (!mayRunPerceptualBackfill(
                    indexingEnabled = settings.indexingEnabled.first(),
                    explicitUserRequest = inputData.getBoolean(KEY_EXPLICIT_USER_REQUEST, false),
                )
            ) return Result.success()

            // Peek the backlog. An empty page is verified against the authoritative full count
            // below: a concurrent insert/rename can otherwise land behind the keyset cursor and
            // incorrectly turn this run into a terminal success.
            val firstBatch = indexRepository.imagesNeedingPerceptualHash(BATCH_SIZE)
            if (firstBatch.isEmpty()) {
                val remaining = try {
                    indexRepository.imagesNeedingPerceptualHashCount()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    return Result.retry()
                }
                return if (remaining > 0) Result.retry() else Result.success()
            }

            // A large backlog (first run on an existing gallery) must run to completion, which can
            // take a few minutes of decoding — promote to a foreground service so the OS lets it.
            val remaining = runCatching { indexRepository.imagesNeedingPerceptualHashCount() }
                .getOrDefault(Int.MAX_VALUE)
            val foreground = remaining >= FOREGROUND_THRESHOLD
            if (foreground) runCatching { setForeground(foregroundInfo(0, remaining)) }

            val run = runPerceptualBackfillPages(
                initialBatch = firstBatch.map { it.path },
                batchSize = BATCH_SIZE,
                maxVisitedRows = MAX_PER_RUN,
                loadAfter = { afterPath, limit ->
                    currentCoroutineContext().ensureActive()
                    indexRepository.imagesNeedingPerceptualHash(limit, afterPath).map { it.path }
                },
                compute = { path ->
                    currentCoroutineContext().ensureActive()
                    // `null` means retryable: preserve the missing state so it is never silently
                    // excluded from Similar photos. UNHASHABLE is an explicit non-null result for
                    // a genuinely unsupported/corrupt image and is safe to persist once.
                    when (val decision = perceptualBackfillDecision(
                        perceptualHashSource.computeAll(path),
                    )) {
                        PerceptualBackfillDecision.Retry -> null
                        is PerceptualBackfillDecision.Persist -> {
                            PathPerceptualFingerprint(path, decision.fingerprint)
                        }
                    }
                },
                persistBatch = { updates ->
                    try {
                        indexRepository.putPerceptualFingerprints(updates)
                        true
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        false
                    }
                },
                countRemaining = indexRepository::imagesNeedingPerceptualHashCount,
                shouldStop = { isStopped },
                onBatchCompleted = { persisted ->
                    if (foreground) {
                        runCatching { setForeground(foregroundInfo(persisted, remaining)) }
                    }
                },
            )
            when (run.outcome) {
                PerceptualBackfillLoopOutcome.Success -> Result.success()
                PerceptualBackfillLoopOutcome.Retry -> Result.retry()
            }
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
            .setContentTitle("Jupiscan")
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
        // minSdk 26: notification channels always exist on every supported device.
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

    companion object {
        /** Unique work name (KEEP semantics; see IndexingScheduler). */
        const val UNIQUE_WORK_NAME = "perceptual-hash-backfill"

        /** Input flag used only for the user-visible Duplicate cleanup tool. */
        const val KEY_EXPLICIT_USER_REQUEST = "explicit_user_request"

        private const val BATCH_SIZE = 100

        /**
         * Per-run cap so one foreground execution remains bounded while covering the reported
         * 40k-photo corpus in a single contiguous attempt. Rows are processed in small DB batches,
         * so this does not grow memory with library size.
         */
        private const val MAX_PER_RUN = 50_000

        /** Backlog at/above which the pass runs as a foreground service so it isn't throttled. */
        private const val FOREGROUND_THRESHOLD = 400

        private const val NOTIFICATION_CHANNEL_ID = "photo-analysis"
        private const val NOTIFICATION_ID = 4243
    }
}

/** Terminal decision and counters from one bounded keyset-pagination pass. */
internal data class PerceptualBackfillLoopResult(
    val outcome: PerceptualBackfillLoopOutcome,
    /** Every row offered to the compute step, including retryable decode failures. */
    val visitedRows: Int,
    val persistedRows: Int,
)

internal enum class PerceptualBackfillLoopOutcome { Success, Retry }

/**
 * Testable keyset-pagination core for [PerceptualHashBackfillWorker].
 *
 * [maxVisitedRows] bounds actual decode/database attempts, not only successful writes. After the
 * cursor reaches the end (or the visit budget is exhausted), [countRemaining] rechecks the whole
 * backlog. That authoritative check catches retryable rows as well as concurrent inserts/renames
 * that sort behind the current cursor. A failed count is fail-closed and requests another run.
 */
internal suspend fun <T> runPerceptualBackfillPages(
    initialBatch: List<String>,
    batchSize: Int,
    maxVisitedRows: Int,
    loadAfter: suspend (afterPath: String, limit: Int) -> List<String>,
    compute: suspend (path: String) -> T?,
    /** Atomically persists every computed update in one page; false keeps the whole page retryable. */
    persistBatch: suspend (updates: List<T>) -> Boolean,
    countRemaining: suspend () -> Int,
    shouldStop: () -> Boolean = { false },
    onBatchCompleted: suspend (persistedRows: Int) -> Unit = {},
): PerceptualBackfillLoopResult {
    require(batchSize > 0) { "batchSize must be positive" }
    require(maxVisitedRows > 0) { "maxVisitedRows must be positive" }

    var batch = initialBatch
    var visitedRows = 0
    var persistedRows = 0

    suspend fun finish(): PerceptualBackfillLoopResult {
        val remaining = try {
            countRemaining()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        return PerceptualBackfillLoopResult(
            outcome = if (remaining == 0) {
                PerceptualBackfillLoopOutcome.Success
            } else {
                PerceptualBackfillLoopOutcome.Retry
            },
            visitedRows = visitedRows,
            persistedRows = persistedRows,
        )
    }

    while (batch.isNotEmpty()) {
        if (shouldStop()) {
            return PerceptualBackfillLoopResult(
                PerceptualBackfillLoopOutcome.Retry,
                visitedRows,
                persistedRows,
            )
        }
        val pageUpdates = mutableListOf<T>()
        var budgetExhaustedMidPage = false
        for (path in batch) {
            if (shouldStop()) {
                return PerceptualBackfillLoopResult(
                    PerceptualBackfillLoopOutcome.Retry,
                    visitedRows,
                    persistedRows,
                )
            }
            if (visitedRows >= maxVisitedRows) {
                budgetExhaustedMidPage = true
                break
            }
            visitedRows++
            compute(path)?.let(pageUpdates::add)
        }
        if (pageUpdates.isNotEmpty() && persistBatch(pageUpdates)) {
            persistedRows += pageUpdates.size
        }
        onBatchCompleted(persistedRows)

        if (budgetExhaustedMidPage || visitedRows >= maxVisitedRows) return finish()
        val afterPath = batch.last()
        batch = loadAfter(afterPath, batchSize)
    }

    return finish()
}
