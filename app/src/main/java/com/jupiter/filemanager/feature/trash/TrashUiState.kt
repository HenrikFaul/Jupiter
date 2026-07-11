package com.jupiter.filemanager.feature.trash

import com.jupiter.filemanager.domain.model.TrashItem

/**
 * Immutable UI state for the Recycle Bin (Trash) screen.
 *
 * Produced by [TrashViewModel] from the real
 * [com.jupiter.filemanager.domain.repository.TrashRepository]. [items] streams
 * every entry currently held in the app-managed Recycle Bin, most-recently
 * deleted first. When the bin is empty the screen surfaces an honest empty
 * state rather than an empty list.
 *
 * @property items the trashed entries currently held in the Recycle Bin.
 * @property isLoading true until the first emission from the trash stream arrives.
 * @property busy true while a restore / delete / empty operation is in flight.
 * @property errorMessage a one-shot, user-facing error from the last failed action.
 * @property autoDeleteDays the Recycle-Bin retention window in days (0 = OFF); when > 0 the screen
 *   shows a per-item "deletes in N days" countdown.
 */
data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
    val isLoading: Boolean = true,
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val autoDeleteDays: Int = 0,
) {
    /** True when there is nothing in the Recycle Bin once loading has finished. */
    val isEmpty: Boolean
        get() = !isLoading && items.isEmpty()
}
