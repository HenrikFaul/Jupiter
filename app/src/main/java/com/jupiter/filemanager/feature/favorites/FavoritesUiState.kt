package com.jupiter.filemanager.feature.favorites

import com.jupiter.filemanager.domain.model.Bookmark
import com.jupiter.filemanager.domain.model.FileItem

/**
 * A single favorite entry pairing the persisted [Bookmark] with the resolved
 * [FileItem] it points to (when the path could be resolved on disk).
 *
 * @property bookmark the saved bookmark (path + label).
 * @property item the resolved file-system entry, or null when the path no longer
 *   exists / could not be read.
 * @property isDirectory whether the favorite points at a folder. When [item] is
 *   resolvable this mirrors `item.isDirectory`; otherwise it falls back to a
 *   best-effort guess from the bookmark path.
 */
data class FavoriteEntry(
    val bookmark: Bookmark,
    val item: FileItem?,
    val isDirectory: Boolean,
)

/**
 * Immutable UI state for the Favorites tab.
 *
 * @property isLoading true while the initial set of favorites is being resolved.
 * @property entries the resolved favorite entries, in persisted order.
 */
data class FavoritesUiState(
    val isLoading: Boolean = true,
    val entries: List<FavoriteEntry> = emptyList(),
) {
    /** True when there are no favorites to display and loading has completed. */
    val isEmpty: Boolean
        get() = !isLoading && entries.isEmpty()
}
