package com.jupiter.filemanager.feature.categories

import com.jupiter.filemanager.data.media.CategorySort
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.domain.model.StorageCategory
import java.util.Locale

/**
 * Real, path-backed refinements for the Photos category.
 *
 * These deliberately model only folders that can be identified from a
 * [FileItem.path]. There is no "Similar" option here: near-duplicate detection
 * belongs to the cleanup scan and must not be implied by a gallery filter.
 */
enum class PhotoLocationFilter(
    val label: String,
) {
    ALL("All"),
    CAMERA("Camera"),
    SCREENSHOTS("Screenshots"),
    DOWNLOADS("Downloads");

    /** Returns whether this filter can truthfully include [item]. */
    fun matches(item: FileItem): Boolean = when (this) {
        ALL -> true
        CAMERA -> item.pathSegments().any { it == "camera" }
        SCREENSHOTS -> item.pathSegments().any { it == "screenshots" } ||
            item.name.startsWith("screenshot", ignoreCase = true)
        DOWNLOADS -> item.pathSegments().any { it == "download" || it == "downloads" }
    }
}

/** Normalizes both Android-style and test/device paths before folder matching. */
private fun FileItem.pathSegments(): List<String> = path
    .replace('\\', '/')
    .split('/')
    .asSequence()
    .filter { it.isNotBlank() }
    .map { it.lowercase(Locale.ROOT) }
    .toList()

/** Immutable state of the built-in, repository-backed destination picker. */
data class CategoryFolderPickerState(
    val rootPath: String = "",
    val currentPath: String = "",
    val folders: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val canNavigateUp: Boolean
        get() = currentPath.isNotBlank() &&
            rootPath.isNotBlank() &&
            !PhotoMovePolicy.samePath(currentPath, rootPath)
}

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
 * @param photoFilter the path-backed refinement currently active for Photos.
 * @param sourceItemCount count before [photoFilter] is applied. This keeps a
 * filtered-empty gallery navigable, so the user can always switch back to All.
 * @param sourceTotalSizeBytes total bytes before the Photos location refinement;
 * used as the truthful denominator of the overview progress bar.
 * @param selectionMode whether photo grid selection controls are visible.
 * @param selectedPaths stable paths selected in the currently visible result.
 * @param collapsedDateGroups local-day keys collapsed by the user.
 * @param moveFolderPicker non-null while the built-in destination chooser is visible.
 * @param moveOperation latest real repository move progress, including terminal state.
 * @param moveError last user-visible move/picker error, or null.
 */
data class CategoryBrowseUiState(
    val category: StorageCategory = StorageCategory.IMAGES,
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sort: CategorySort = CategorySort.DATE_DESC,
    val photoFilter: PhotoLocationFilter = PhotoLocationFilter.ALL,
    val sourceItemCount: Int = 0,
    val sourceTotalSizeBytes: Long = 0L,
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val collapsedDateGroups: Set<Long> = emptySet(),
    val moveFolderPicker: CategoryFolderPickerState? = null,
    val moveOperation: FileOperationProgress? = null,
    val moveError: String? = null,
) {
    /** Number of files currently listed. */
    val itemCount: Int get() = items.size

    /** Total size, in bytes, of every listed file. */
    val totalSizeBytes: Long get() = items.sumOf { it.sizeBytes }

    /** Selected, still-visible items in the exact current MediaStore order. */
    val selectedItems: List<FileItem>
        get() = items.filter { it.path in selectedPaths }

    val selectedCount: Int get() = selectedItems.size

    val isMoveBusy: Boolean
        get() = moveOperation?.state == OperationState.RUNNING

    /** Fraction of the Photos source represented by the current real filter. */
    val filteredSizeFraction: Float
        get() = when {
            sourceTotalSizeBytes <= 0L -> 0f
            else -> (totalSizeBytes.toDouble() / sourceTotalSizeBytes.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        }

    /**
     * Whether this category should be presented as a thumbnail grid (images and
     * videos) rather than a details list.
     */
    val isGrid: Boolean
        get() = category == StorageCategory.IMAGES || category == StorageCategory.VIDEOS

    /** True when a Photos folder refinement has hidden every matching item. */
    val isPhotoFilterEmpty: Boolean
        get() = category == StorageCategory.IMAGES &&
            photoFilter != PhotoLocationFilter.ALL &&
            sourceItemCount > 0 &&
            items.isEmpty()
}
