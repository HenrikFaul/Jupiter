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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

@Singleton
class IndexReadinessRepositoryImpl @Inject constructor(
    private val indexStateDao: IndexStateDao,
    private val fileIndexDao: FileIndexDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : IndexReadinessRepository {

    private val structuralTypes = listOf(
        FileType.CODE.name,
        FileType.ARCHIVE.name,
        FileType.APK.name,
        FileType.VIDEO.name,
        FileType.PDF.name,
        FileType.AUDIO.name,
    )

    override fun observeReadiness(): Flow<StorageReadiness> =
        combine(
            indexStateDao.observe(IndexState.PRIMARY_VOLUME),
            fileIndexDao.observeDescriptorCoverage(
                FileType.IMAGE.name,
                PerceptualHash.CURRENT_DESCRIPTOR_VERSION,
                PerceptualHash.UNHASHABLE,
                structuralTypes,
            ),
        ) { state, coverage -> buildReadiness(state, coverage) }
            .flowOn(dispatcher)

    override suspend fun currentReadiness(): StorageReadiness = withContext(dispatcher) {
        buildReadiness(
            indexStateDao.get(IndexState.PRIMARY_VOLUME),
            fileIndexDao.descriptorCoverage(
                FileType.IMAGE.name,
                PerceptualHash.CURRENT_DESCRIPTOR_VERSION,
                PerceptualHash.UNHASHABLE,
                structuralTypes,
            ),
        )
    }

    private fun buildReadiness(
        state: IndexState?,
        coverage: DescriptorCoverageSnapshot,
    ): StorageReadiness {
        val status = state?.status ?: IndexStatus.EMPTY
        val hasCompleteGeneration = (state?.lastCompleteGeneration ?: 0L) > 0L

        return StorageReadiness(
            metadata = Coverage(
                ready = state?.filesSeen ?: 0L,
                total = null,
                state = StorageReadiness.metadataState(status, hasCompleteGeneration),
            ),
            exactHashes = Coverage(
                coverage.exactReady,
                coverage.fileTotal,
                coverageState(coverage.exactReady, coverage.fileTotal),
            ),
            imageDescriptors = Coverage(
                coverage.imageReady,
                coverage.imageTotal,
                coverageState(coverage.imageReady, coverage.imageTotal),
            ),
            structuralDescriptors = Coverage(
                coverage.structuralReady,
                coverage.structuralTotal,
                coverageState(coverage.structuralReady, coverage.structuralTotal),
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
