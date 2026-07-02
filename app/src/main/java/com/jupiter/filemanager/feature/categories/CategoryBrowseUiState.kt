package com.jupiter.filemanager.feature.categories

import com.jupiter.filemanager.data.media.CategorySort
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageCategory

/**
 * Immutable UI state for the device-wide category browser
 * ([CategoryBrowseScreen]).
 *
 * The listing is produced by [com.jupiter.filemanager.data.media.MediaStoreCategorySource]
 * which queries `MediaStore` directly, so it is fast even for tens of thousands
 * of files. This state is purely presentational — it holds the already-resolved
 * items plus the honest loading/error flags the screen renders.
 *
 * @param category the category being browsed (drives the title, layout and query).
 * @param items the resolved files for [category], already sorted by [sort].
 * @param isLoading true while a query is in flight and there is nothing to show yet.
 * @param error a user-facing error message, or null when there is no error.
 * @param sort the sort order currently applied to [items].
 */
data class CategoryBrowseUiState(
    val category: StorageCategory = StorageCategory.IMAGES,
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sort: CategorySort = CategorySort.DATE_DESC,
) {
    /** Number of files currently listed. */
    val itemCount: Int get() = items.size

    /** Total size, in bytes, of every listed file. */
    val totalSizeBytes: Long get() = items.sumOf { it.sizeBytes }

    /**
     * Whether this category should be presented as a thumbnail grid (images and
     * videos) rather than a details list.
     */
    val isGrid: Boolean
        get() = category == StorageCategory.IMAGES || category == StorageCategory.VIDEOS
}
