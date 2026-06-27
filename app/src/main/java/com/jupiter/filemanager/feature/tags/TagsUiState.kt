package com.jupiter.filemanager.feature.tags

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.Tag

/**
 * Immutable UI state for the Tags screen.
 *
 * @param tags the user-defined tags, each carrying its associated file count.
 * @param selectedTagId the id of the tag whose files are currently being viewed,
 *   or `null` when no tag is selected (showing the tag list).
 * @param selectedTagName the display name of the selected tag, for the detail header.
 * @param filesForSelectedTag resolved [FileItem]s for the selected tag.
 * @param isLoading true while the initial tag list is loading.
 * @param isFilesLoading true while files for the selected tag are being resolved.
 */
data class TagsUiState(
    val tags: List<Tag> = emptyList(),
    val selectedTagId: String? = null,
    val selectedTagName: String? = null,
    val filesForSelectedTag: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val isFilesLoading: Boolean = false,
)
