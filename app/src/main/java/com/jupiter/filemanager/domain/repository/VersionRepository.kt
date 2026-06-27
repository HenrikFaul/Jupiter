package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileVersion
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to the version history of a file.
 *
 * No real versioning backend exists yet; implementations start empty and
 * surface honest empty states. [restore] reports failure until a backend is
 * configured.
 */
interface VersionRepository {

    /**
     * Observes the list of [FileVersion]s recorded for the file at [path].
     * Emits an empty list when no versions are tracked.
     */
    fun versionsFor(path: String): Flow<List<FileVersion>>

    /**
     * Restores the file to the version identified by [versionId].
     *
     * Returns [AppResult.Failure] until a versioning backend is configured.
     */
    suspend fun restore(versionId: String): AppResult<Unit>
}
