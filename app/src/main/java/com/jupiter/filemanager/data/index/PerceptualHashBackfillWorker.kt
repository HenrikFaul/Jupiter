package com.jupiter.filemanager.data.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
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
 * ([PerceptualHash]), so near-duplicate detection covers the existing library — not just
 * files arriving after the feature shipped.
 *
 * Cheap per item (bounds decode + subsampled decode + 9×8 scale, a few ms each) but large
 * in volume (tens of thousands of photos), so it runs in bounded batches with cooperative
 * cancellation. Progress is GUARANTEED: every attempted row is marked — with its hash, or
 * with [PerceptualHash.UNHASHABLE] for undecodable files — so the "missing fingerprint"
 * work list always shrinks and a corrupt image can never loop forever. When more work
 * remains after this run's cap, it returns retry so WorkManager reschedules it; the
 * periodic refresh kicker and app-start ensure keep it converging regardless.
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

            var processed = 0
            while (processed < MAX_PER_RUN) {
                currentCoroutineContext().ensureActive()
                if (isStopped) return Result.retry()

                val batch = indexRepository.imagesNeedingPerceptualHash(BATCH_SIZE)
                if (batch.isEmpty()) return Result.success()

                for (item in batch) {
                    currentCoroutineContext().ensureActive()
                    if (isStopped) return Result.retry()
                    // null = transient failure → try again on a later run; otherwise mark
                    // (real hash or UNHASHABLE) so this row leaves the work list.
                    val hash = perceptualHashSource.compute(item.path)
                        ?: PerceptualHash.UNHASHABLE
                    runCatching { indexRepository.putPerceptualHash(item.path, hash) }
                    processed++
                }
            }
            // Cap reached with work remaining — reschedule to continue.
            Result.retry()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        /** Unique work name (KEEP semantics; see IndexingScheduler). */
        const val UNIQUE_WORK_NAME = "perceptual-hash-backfill"

        private const val BATCH_SIZE = 100

        /** Per-run cap so one execution stays a reasonable background slice (~2k decodes). */
        private const val MAX_PER_RUN = 2_000
    }
}
