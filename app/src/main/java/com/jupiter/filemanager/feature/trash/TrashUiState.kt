package com.jupiter.filemanager.feature.trash

import com.jupiter.filemanager.domain.model.TrashItem

/** Presentation order for the real Recycle-Bin entries. */
enum class TrashSort(val label: String) {
    NEWEST("Deleted (newest)"),
    OLDEST("Deleted (oldest)"),
    SIZE("Size (largest)"),
}

/** Local type filter; no item is removed from the repository by changing it. */
enum class TrashFilter(val label: String) {
    ALL("All items"),
    FILES("Files"),
    FOLDERS("Folders"),
}

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
    val sort: TrashSort = TrashSort.NEWEST,
    val filter: TrashFilter = TrashFilter.ALL,
) {
    /** True when there is nothing in the Recycle Bin once loading has finished. */
    val isEmpty: Boolean
        get() = !isLoading && items.isEmpty()

    /** Filtered and sorted view of [items], leaving the source list untouched. */
    val visibleItems: List<TrashItem>
        get() {
            val filtered = when (filter) {
                TrashFilter.ALL -> items
                TrashFilter.FILES -> items.filterNot(TrashItem::isDirectory)
                TrashFilter.FOLDERS -> items.filter(TrashItem::isDirectory)
            }
            return when (sort) {
                TrashSort.NEWEST -> filtered.sortedByDescending(TrashItem::deletedAt)
                TrashSort.OLDEST -> filtered.sortedBy(TrashItem::deletedAt)
                TrashSort.SIZE -> filtered.sortedByDescending(TrashItem::sizeBytes)
            }
        }
}
