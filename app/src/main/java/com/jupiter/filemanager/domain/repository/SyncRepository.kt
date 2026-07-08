package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.SyncConflict
import kotlinx.coroutines.flow.Flow

/**
 * Exposes local-vs-remote sync conflicts and their resolution.
 *
 * No real sync backend exists yet; implementations start empty and surface
 * honest empty states.
 */
interface SyncRepository {

    /**
     * Observes the current set of unresolved [SyncConflict]s. Emits an empty
     * list when no conflicts are present.
     */
    fun observeConflicts(): Flow<List<SyncConflict>>

    /**
     * Resolves the conflict identified by [conflictId], keeping the local copy
     * when [keepLocal] is true or the remote copy otherwise.
     */
    suspend fun resolve(conflictId: String, keepLocal: Boolean): AppResult<Unit>
}
