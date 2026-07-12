package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.repository.IndexStateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [IndexStateRepository] for the primary volume.
 *
 * All decisions about whether the index is trustworthy funnel through here, so no screen
 * ever infers completeness from a row count.
 */
@Singleton
class IndexStateRepositoryImpl @Inject constructor(
    private val dao: IndexStateDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : IndexStateRepository {

    private val volume = IndexState.PRIMARY_VOLUME

    override fun observe(): Flow<IndexState?> =
        dao.observe(volume).flowOn(ioDispatcher)

    override suspend fun current(): IndexState? = withContext(ioDispatcher) {
        dao.get(volume)
    }

    override suspend fun isMetadataComplete(): Boolean = withContext(ioDispatcher) {
        dao.get(volume)?.status == IndexStatus.COMPLETE
    }

    override suspend fun isUsable(): Boolean = withContext(ioDispatcher) {
        val state = dao.get(volume) ?: return@withContext false
        // COMPLETE is trivially usable. A RUNNING/FAILED rescan with a prior complete
        // generation is too: the sweep only runs on success, so those rows are intact.
        // reset() rewrites the row with lastCompleteGeneration = 0, so a cleared index
        // is never considered usable.
        state.status == IndexStatus.COMPLETE || state.lastCompleteGeneration > 0L
    }

    override suspend fun beginScan(): Long = withContext(ioDispatcher) {
        val existing = dao.get(volume)
        val generation = (existing?.activeScanGeneration ?: 0L) + 1L
        val now = System.currentTimeMillis()
        dao.upsert(
            (existing ?: IndexState(volumeId = volume)).copy(
                metadataStatus = IndexStatus.RUNNING.name,
                activeScanGeneration = generation,
                scanStartedAt = now,
                lastError = null,
            ),
        )
        generation
    }

    override suspend fun completeScan(generation: Long, filesSeen: Long) =
        withContext(ioDispatcher) {
            val existing = dao.get(volume) ?: IndexState(volumeId = volume)
            dao.upsert(
                existing.copy(
                    metadataStatus = IndexStatus.COMPLETE.name,
                    activeScanGeneration = generation,
                    lastCompleteGeneration = generation,
                    scanCompletedAt = System.currentTimeMillis(),
                    filesSeen = filesSeen,
                    lastError = null,
                ),
            )
        }

    override suspend fun failScan(error: String?) = withContext(ioDispatcher) {
        val existing = dao.get(volume) ?: IndexState(volumeId = volume)
        dao.upsert(
            existing.copy(metadataStatus = IndexStatus.FAILED.name, lastError = error),
        )
    }

    override suspend fun recordDeltaSync(version: String?, generation: Long) =
        withContext(ioDispatcher) {
            val existing = dao.get(volume)
            if (existing == null) dao.upsert(IndexState(volumeId = volume))
            dao.updateDeltaState(
                volumeId = volume,
                version = version,
                generation = generation,
                at = System.currentTimeMillis(),
            )
        }

    override suspend fun reset() = withContext(ioDispatcher) {
        dao.upsert(IndexState(volumeId = volume, metadataStatus = IndexStatus.EMPTY.name))
    }
}
