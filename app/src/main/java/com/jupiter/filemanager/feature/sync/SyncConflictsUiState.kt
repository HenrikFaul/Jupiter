package com.jupiter.filemanager.feature.sync

import com.jupiter.filemanager.domain.model.SyncConflict

/**
 * Immutable UI state for the Sync Conflicts screen.
 *
 * Produced by [SyncConflictsViewModel] from the real
 * [com.jupiter.filemanager.domain.repository.SyncRepository]. No real sync
 * backend (cloud / NAS protocol I/O, conflict detection) is wired up yet, so
 * [conflicts] starts empty and the screen surfaces an honest empty state rather
 * than fabricating conflicts.
 *
 * @property conflicts the current set of unresolved local-vs-remote conflicts.
 * @property isLoading true while the initial collection has not produced a value yet.
 * @property resolvingId id of the conflict currently being resolved, or `null`.
 * @property errorMessage a one-shot, user-facing error from the last failed resolve.
 */
data class SyncConflictsUiState(
    val conflicts: List<SyncConflict> = emptyList(),
    val isLoading: Boolean = true,
    val resolvingId: String? = null,
    val errorMessage: String? = null,
) {
    /** True when there are no conflicts to display once loading has finished. */
    val isEmpty: Boolean
        get() = !isLoading && conflicts.isEmpty()
}
