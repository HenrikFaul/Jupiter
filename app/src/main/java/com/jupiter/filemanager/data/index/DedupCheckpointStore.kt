package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence seam for the dedup reconciler's checkpoint + the indexing-enabled gate, so the
 * reconciler is testable without constructing the full [SettingsDataStore] (and its encrypted
 * credential store) under a unit test.
 */
interface DedupCheckpointStore {
    suspend fun isIndexingEnabled(): Boolean

    /** The last MediaStore `_id` the reconciler has examined. 0 = no baseline yet. */
    suspend fun getCheckpointId(): Long

    /** Monotonic: implementations must never move the checkpoint backwards. */
    suspend fun setCheckpointId(value: Long)
}

/** [SettingsDataStore]-backed [DedupCheckpointStore]. */
@Singleton
class SettingsDedupCheckpointStore @Inject constructor(
    private val settings: SettingsDataStore,
) : DedupCheckpointStore {
    override suspend fun isIndexingEnabled(): Boolean = settings.indexingEnabled.first()
    override suspend fun getCheckpointId(): Long = settings.dedupCheckpointId.first()
    override suspend fun setCheckpointId(value: Long) = settings.setDedupCheckpointId(value)
}
