package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.TransferTask
import kotlinx.coroutines.flow.Flow

/**
 * Streams the active and historical [TransferTask]s for the transfer center.
 *
 * No live transfer backend (nearby / Wi-Fi / cloud) is wired yet, so
 * implementations start empty and expose honest empty states rather than
 * fabricating fake live progress.
 */
interface TransferRepository {

    /**
     * Streams the current list of [TransferTask]s, emitting on every change.
     */
    fun observeTransfers(): Flow<List<TransferTask>>

    /**
     * Removes all transfers whose status is terminal (completed/failed).
     *
     * Safe to call when there are no completed transfers.
     */
    suspend fun clearCompleted()
}
