package com.jupiter.filemanager.feature.workspace

import com.jupiter.filemanager.domain.model.Workspace

/**
 * Immutable UI state for [WorkspacesScreen].
 *
 * @param isLoading true while the initial workspace listing is being collected.
 * @param workspaces the user's saved workspaces, newest/most-recent first.
 * @param showCreateDialog whether the create-workspace dialog is visible.
 * @param isCreating true while a new workspace is being persisted.
 */
data class WorkspacesUiState(
    val isLoading: Boolean = true,
    val workspaces: List<Workspace> = emptyList(),
    val showCreateDialog: Boolean = false,
    val isCreating: Boolean = false,
) {
    /** Convenience flag for rendering the empty state. */
    val isEmpty: Boolean get() = !isLoading && workspaces.isEmpty()
}
