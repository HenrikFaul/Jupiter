package com.jupiter.filemanager.feature.version

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileVersion

/**
 * Immutable UI state for [VersionHistoryScreen].
 *
 * No real versioning backend exists yet, so [versions] is populated from
 * [com.jupiter.filemanager.domain.repository.VersionRepository.versionsFor],
 * which starts empty. The screen therefore renders an honest empty state and a
 * "restore" action that surfaces the backend's not-configured failure rather
 * than fabricating fake history.
 *
 * @property file the resolved [FileItem] whose history is being shown, or null
 *   before it loads (or when the navigation path could not be resolved).
 * @property title a display title for the file (typically the file name).
 * @property versions the recorded [FileVersion]s for the file, newest first.
 *   Empty when no versions are tracked.
 * @property isLoading whether the file metadata / version list is still loading.
 * @property isRestoring whether a restore operation is currently in flight.
 * @property error a human-readable error message, or null when there is none.
 * @property message a transient one-shot message (e.g. the honest restore
 *   failure) to surface in a snackbar; cleared via
 *   [VersionHistoryViewModel.consumeMessage].
 */
data class VersionHistoryUiState(
    val file: FileItem? = null,
    val title: String = "",
    val versions: List<FileVersion> = emptyList(),
    val isLoading: Boolean = true,
    val isRestoring: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    /** True when there are no tracked versions to show. */
    val isEmpty: Boolean
        get() = versions.isEmpty()
}
