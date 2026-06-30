package com.jupiter.filemanager.feature.cleanup

import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.MediaQuality

/**
 * Immutable UI state for the Duplicates screen.
 *
 * @param isScanning whether a duplicate scan is currently in progress.
 * @param groups duplicate groups discovered so far, newest results last.
 * @param selectedPaths absolute paths the user has marked for deletion.
 * @param qualities probed media-quality info keyed by absolute file path; used
 *   to render a per-file quality label. Missing entries simply render no label.
 * @param isDeleting whether a delete operation is currently running.
 * @param errorMessage a transient error message to surface, or null.
 * @param infoMessage a transient informational message (e.g. after a delete), or null.
 */
data class DuplicatesUiState(
    val isScanning: Boolean = false,
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val qualities: Map<String, MediaQuality> = emptyMap(),
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    /** True when there are no groups and scanning has finished. */
    val isEmpty: Boolean
        get() = !isScanning && groups.isEmpty()

    /** Total bytes wasted across every discovered duplicate group. */
    val totalWastedBytes: Long
        get() = groups.sumOf { it.wastedBytes }

    /** Total bytes that would be reclaimed by deleting the currently selected files. */
    val selectedReclaimableBytes: Long
        get() = groups
            .asSequence()
            .flatMap { it.files.asSequence() }
            .filter { it.path in selectedPaths }
            .sumOf { it.sizeBytes }

    /** Number of files currently selected for deletion. */
    val selectedCount: Int
        get() = selectedPaths.size
}
