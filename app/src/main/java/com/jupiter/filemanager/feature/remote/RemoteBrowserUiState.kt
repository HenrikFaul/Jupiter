package com.jupiter.filemanager.feature.remote

import com.jupiter.filemanager.domain.model.RemoteEntry

/**
 * Immutable UI state for [RemoteBrowserScreen].
 *
 * Represents the currently displayed directory of a remote connection: the
 * resolved [entries] (directories first), the [path] being viewed, and the
 * transient loading/error/download flags. A one-shot [message] surfaces download
 * results (or honest failures) in a snackbar.
 */
data class RemoteBrowserUiState(
    val title: String = "",
    val path: String = "",
    val entries: List<RemoteEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadingPath: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    /** True when there is nothing to show and no work in flight. */
    val isEmpty: Boolean
        get() = entries.isEmpty()

    /** True when the current path is the connection root (no "up" affordance). */
    val isAtRoot: Boolean
        get() = path.isEmpty() || path == "/"
}
