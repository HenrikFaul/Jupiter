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
 * How the directory listing is rendered.
 *
 * [LIST] shows a single-column row list; [GRID] shows a multi-column grid of items.
 */
enum class ViewMode { LIST, GRID }

/**
 * A single open browser tab pointing at a directory.
 *
 * @property path absolute path of the directory the tab is showing.
 * @property title short display label for the tab (usually the folder name).
 */
data class BrowserTab(
    val path: String,
    val title: String,
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
 * @property viewMode whether the listing renders as a list or a grid.
 * @property tabs the set of open browser tabs.
 * @property activeTabIndex index of the currently active tab within [tabs].
 * @property treeExpanded whether the folder tree side panel is expanded.
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
    val viewMode: ViewMode = ViewMode.LIST,
    val tabs: List<BrowserTab> = listOf(BrowserTab(path = "", title = "Files")),
    val activeTabIndex: Int = 0,
    val treeExpanded: Boolean = false,
    /** User preference; the screen applies it only on tablet-width layouts. */
    val dualPaneEnabled: Boolean = false,
    /** When true, non-folder rows are split into honest file-type sections. */
    val groupFilesByType: Boolean = false,
    /** Confirmation policy for reversible moves to Recycle Bin (permanent delete is unaffected). */
    val confirmBeforeTrash: Boolean = true,
)
