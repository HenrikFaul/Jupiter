package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.Coverage
import com.jupiter.filemanager.domain.model.PipelineStatus
import com.jupiter.filemanager.domain.model.StorageReadiness
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.repository.IndexReadinessRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

@Singleton
class IndexReadinessRepositoryImpl @Inject constructor(
    private val indexStateDao: IndexStateDao,
    private val fileIndexDao: FileIndexDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : IndexReadinessRepository {

    override fun observeReadiness(): Flow<StorageReadiness> =
        indexStateDao.observe(IndexState.PRIMARY_VOLUME)
            .map { buildReadiness(it) }
            .flowOn(dispatcher)

    override suspend fun currentReadiness(): StorageReadiness = withContext(dispatcher) {
        buildReadiness(indexStateDao.get(IndexState.PRIMARY_VOLUME))
    }

    private suspend fun buildReadiness(state: IndexState?): StorageReadiness {
        val fileTotal = fileIndexDao.countFiles().toLong()
        val exactReady = fileIndexDao.countFilesWithContentHash().toLong()
        val imageTotal = fileIndexDao.countImages(FileType.IMAGE.name).toLong()
        val imageReady = fileIndexDao.countImagesWithDescriptors(FileType.IMAGE.name).toLong()
        val structuralTypes = listOf(
            FileType.CODE.name,
            FileType.ARCHIVE.name,
            FileType.APK.name,
            FileType.VIDEO.name,
            FileType.PDF.name,
            FileType.AUDIO.name,
        )
        val structuralTotal = fileIndexDao.countStructuralCandidates(structuralTypes).toLong()
        val structuralReady = fileIndexDao.countStructuralReady(structuralTypes).toLong()
        val status = state?.status ?: IndexStatus.EMPTY
        val hasCompleteGeneration = (state?.lastCompleteGeneration ?: 0L) > 0L

        return StorageReadiness(
            metadata = Coverage(
                ready = state?.filesSeen ?: 0L,
                total = null,
                state = StorageReadiness.metadataState(status, hasCompleteGeneration),
            ),
            exactHashes = Coverage(exactReady, fileTotal, coverageState(exactReady, fileTotal)),
            imageDescriptors = Coverage(imageReady, imageTotal, coverageState(imageReady, imageTotal)),
            structuralDescriptors = Coverage(
                structuralReady,
                structuralTotal,
                coverageState(structuralReady, structuralTotal),
            ),
            isStale = status == IndexStatus.DIRTY || status == IndexStatus.FAILED,
            lastCompleteAtMillis = state?.scanCompletedAt?.takeIf { it > 0L },
        )
    }

    private fun coverageState(ready: Long, total: Long): PipelineStatus =
        when {
            total <= 0L -> PipelineStatus.EMPTY
            ready <= 0L -> PipelineStatus.RUNNING
            ready >= total -> PipelineStatus.COMPLETE
            else -> PipelineStatus.PARTIAL
        }
}
