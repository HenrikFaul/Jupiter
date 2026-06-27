package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.ActivityEntry
import com.jupiter.filemanager.domain.model.ActivityType
import kotlinx.coroutines.flow.Flow

/**
 * Records and exposes the history of user and system file activities.
 */
interface ActivityRepository {

    /**
     * Streams the recorded [ActivityEntry] log, most recent first.
     */
    fun observeActivity(): Flow<List<ActivityEntry>>

    /**
     * Records a new activity of the given [type] with a human-readable
     * [description] and the [affectedPaths] involved.
     */
    suspend fun record(
        type: ActivityType,
        description: String,
        affectedPaths: List<String> = emptyList(),
    )

    /**
     * Clears the entire activity history.
     */
    suspend fun clear()
}
