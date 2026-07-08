package com.jupiter.filemanager.feature.details

import com.jupiter.filemanager.domain.model.FileItem

/**
 * Immutable UI state for the [FileDetailsScreen].
 *
 * The screen resolves a single [FileItem] from a file path supplied via
 * `SavedStateHandle`. While the lookup is in flight [isLoading] is true; on
 * failure [error] holds a user-facing message and [file] is null.
 */
data class FileDetailsUiState(
    val isLoading: Boolean = true,
    val file: FileItem? = null,
    val isFavorite: Boolean = false,
    val error: String? = null,
)
