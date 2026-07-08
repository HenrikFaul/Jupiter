package com.jupiter.filemanager.feature.recent

import com.jupiter.filemanager.domain.model.ActivityEntry
import com.jupiter.filemanager.domain.model.FileItem

/**
 * Immutable UI state for the Recent tab.
 *
 * Produced by [RecentViewModel] and rendered by
 * [com.jupiter.filemanager.feature.recent.RecentScreen]. All file IO is performed
 * in the ViewModel/repository; this is a pure, read-only snapshot for the
 * composable layer.
 *
 * @property recentFiles recently modified files across primary storage, newest first (capped).
 * @property activity the recorded activity feed, most recent first.
 * @property isLoading true while the initial recent-file scan is in progress.
 * @property error a user-facing error message, or null when there is no error.
 */
data class RecentUiState(
    val recentFiles: List<FileItem> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
