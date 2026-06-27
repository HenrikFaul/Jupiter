package com.jupiter.filemanager.feature.downloads

import com.jupiter.filemanager.domain.model.FileItem

/**
 * Immutable UI state for the Downloads screen.
 *
 * Produced by [DownloadsViewModel] and rendered by
 * [com.jupiter.filemanager.feature.downloads.DownloadsScreen]. All file IO is
 * performed in the ViewModel/repository; this is a pure, read-only snapshot for
 * the composable layer. The listing reflects the real device Downloads folder —
 * no fabricated data.
 *
 * @property files entries in the Downloads folder, newest first.
 * @property isLoading true while the initial listing is in progress.
 * @property error a user-facing error message, or null when there is no error.
 */
data class DownloadsUiState(
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
