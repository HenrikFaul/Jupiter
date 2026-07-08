package com.jupiter.filemanager.data.sync

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.SyncConflict
import com.jupiter.filemanager.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [SyncRepository] implementation.
 *
 * No real sync backend (cloud/NAS protocol I/O, conflict detection) is wired up
 * yet, so this implementation starts empty and surfaces honest empty states.
 * Conflicts are exposed via a [MutableStateFlow] that never emits fabricated
 * data, and [resolve] succeeds as a no-op since there is nothing to resolve.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor() : SyncRepository {

    private val conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())

    override fun observeConflicts(): Flow<List<SyncConflict>> = conflicts.asStateFlow()

    override suspend fun resolve(conflictId: String, keepLocal: Boolean): AppResult<Unit> {
        // No backend conflicts exist; drop any matching entry defensively and succeed.
        conflicts.value = conflicts.value.filterNot { it.id == conflictId }
        return AppResult.Success(Unit)
    }
}
