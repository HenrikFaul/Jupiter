package com.jupiter.filemanager.data.version

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileVersion
import com.jupiter.filemanager.domain.repository.VersionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Empty [VersionRepository] implementation.
 *
 * No real versioning backend exists yet, so version history is always empty
 * and [restore] reports an honest failure until one is configured.
 */
@Singleton
class VersionRepositoryImpl @Inject constructor() : VersionRepository {

    override fun versionsFor(path: String): Flow<List<FileVersion>> =
        flowOf(emptyList())

    override suspend fun restore(versionId: String): AppResult<Unit> =
        AppResult.Failure(AppError.Unknown("Versioning not configured"))
}
