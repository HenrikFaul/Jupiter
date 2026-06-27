package com.jupiter.filemanager.feature.workspace

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.Workspace

/**
 * Immutable UI state for the workspace detail screen.
 *
 * Renders a single [Workspace] together with the resolved [FileItem]s for its
 * member paths. Paths that can no longer be resolved (e.g. the underlying file
 * was moved or deleted) are surfaced via [missingPaths] so the UI can present
 * them honestly rather than silently dropping them.
 *
 * @param isLoading true while the workspace and its items are being resolved.
 * @param workspace the resolved workspace, or null when not yet loaded or not found.
 * @param items resolved file items for the workspace's member paths.
 * @param missingPaths member paths that could not be resolved on the file system.
 * @param notFound true when no workspace exists for the supplied identifier.
 * @param errorMessage a user-facing message when loading failed, or null.
 */
data class WorkspaceDetailUiState(
    val isLoading: Boolean = true,
    val workspace: Workspace? = null,
    val items: List<FileItem> = emptyList(),
    val missingPaths: List<String> = emptyList(),
    val notFound: Boolean = false,
    val errorMessage: String? = null,
)
