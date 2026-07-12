package com.jupiter.filemanager.data.index

import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * Proactively fingerprints non-image similarity layers for the existing library. Arrival-time
 * detection already fingerprints a new file, but the Duplicates Similar tab also needs older
 * originals to carry comparable descriptors: text/code SimHash, archive/APK member-tree hash,
 * video/PDF visual dHash, and audio envelope hash.
 */
@HiltWorker
class StructuralFingerprintBackfillWorker @AssistedInject constructor(
    @Assisted appContext: android.content.Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsDataStore,
    private val indexRepository: FileIndexRepository,
    private val structuralFingerprintSource: StructuralFingerprintSource,
    private val mediaFingerprintSource: MediaFingerprintSource,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!settings.indexingEnabled.first()) return Result.success()
            var processed = 0
            var batch = indexRepository.filesNeedingStructuralHash(BATCH_SIZE)
            while (batch.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                if (isStopped) return Result.retry()
                for (item in batch) {
                    currentCoroutineContext().ensureActive()
                    if (isStopped) return Result.retry()
                    val fingerprint = when (item.type) {
                        FileType.CODE -> structuralFingerprintSource.textSimHash(item.path)
                        FileType.ARCHIVE, FileType.APK -> structuralFingerprintSource.archiveTreeHash(item.path)
                        FileType.VIDEO -> mediaFingerprintSource.videoKeyframeHash(item.path)
                        FileType.PDF -> mediaFingerprintSource.pdfRenderHash(item.path)
                        FileType.AUDIO -> mediaFingerprintSource.audioAcousticHash(item.path)
                        else -> StructuralHash.UNHASHABLE
                    } ?: StructuralHash.UNHASHABLE
                    runCatching { indexRepository.putStructuralHash(item.path, fingerprint) }
                    processed++
                }
                if (processed >= MAX_PER_RUN) return Result.retry()
                batch = indexRepository.filesNeedingStructuralHash(BATCH_SIZE)
            }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "structural-fingerprint-backfill"
        private const val BATCH_SIZE = 100
        private const val MAX_PER_RUN = 10_000
    }
}
