package com.jupiter.filemanager.feature.browser

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SortOption

/**
 * A single segment of the path breadcrumb trail shown above the file listing.
 *
 * @property name the display label for this segment (e.g. the folder name).
 * @property path the absolute path this segment navigates to when tapped.
 */
data class Breadcrumb(
    val name: String,
    val path: String,
)

/**
 * Immutable UI state for the file browser screen.
 *
 * Produced by [FileBrowserViewModel] and rendered by
 * [com.jupiter.filemanager.feature.browser.FileBrowserScreen]. All file IO is
 * performed in the ViewModel/repository; this state is a pure, read-only
 * snapshot for the composable layer.
 *
 * @property currentPath absolute path of the directory currently being shown.
 * @property breadcrumbs ordered trail of [Breadcrumb]s from the root to [currentPath].
 * @property items the (already sorted and filtered) directory contents.
 * @property isLoading true while a listing is being produced.
 * @property error a user-facing error message, or null when there is no error.
 * @property sortOption the active sort configuration.
 * @property filter the active filter configuration.
 * @property selectionMode true when multi-select mode is active.
 * @property selectedPaths the set of currently selected item paths.
 * @property operation progress of an in-flight file operation, or null when idle.
 * @property canNavigateUp true when [currentPath] has a navigable parent.
 */
data class FileBrowserUiState(
    val currentPath: String = "",
    val breadcrumbs: List<Breadcrumb> = emptyList(),
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortOption: SortOption = SortOption(),
    val filter: FilterOption = FilterOption(),
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val operation: FileOperationProgress? = null,
    val canNavigateUp: Boolean = false,
)
