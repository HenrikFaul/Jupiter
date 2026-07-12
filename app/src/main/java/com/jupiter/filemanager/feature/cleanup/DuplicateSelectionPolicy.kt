package com.jupiter.filemanager.feature.cleanup

import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.MediaQuality

/**
 * Pure safety policy for duplicate deletion selection.
 *
 * The same ranking drives the BEST badge and the deletion selection, so the UI can never label
 * one copy as the keeper while the ViewModel marks that same path for deletion. A path that is a
 * keeper in any overlapping group is protected globally; consequently every group retains at
 * least one file even if malformed/overlapping groups arrive from an index implementation.
 */
internal object DuplicateSelectionPolicy {

    fun rank(
        group: DuplicateGroup,
        qualities: Map<String, MediaQuality>,
    ): List<FileItem> = group.files.sortedWith(
        compareByDescending<FileItem> { qualities[it.path]?.score ?: 0L }
            .thenByDescending { it.sizeBytes }
            .thenByDescending { it.lastModified }
            .thenBy { it.path },
    )

    fun keeperPath(
        group: DuplicateGroup,
        qualities: Map<String, MediaQuality>,
    ): String? = rank(group, qualities).firstOrNull()?.path

    fun protectedKeeperPaths(
        allGroups: List<DuplicateGroup>,
        qualities: Map<String, MediaQuality>,
    ): Set<String> = allGroups.mapNotNullTo(mutableSetOf()) {
        keeperPath(it, qualities)
    }

    /**
     * Returns paths removable from [actionableGroups] while protecting the keeper of every group
     * in [allGroups]. Protecting all keepers also makes overlapping groups deletion-safe.
     */
    fun removablePaths(
        actionableGroups: List<DuplicateGroup>,
        allGroups: List<DuplicateGroup>,
        qualities: Map<String, MediaQuality>,
    ): Set<String> {
        val protectedKeepers = protectedKeeperPaths(allGroups, qualities)
        return actionableGroups
            .asSequence()
            .flatMap { it.files.asSequence() }
            .map { it.path }
            .filterNot { it in protectedKeepers }
            .toSet()
    }

    fun sanitizeVisible(state: DuplicatesUiState): Set<String> =
        state.selectedPaths intersect removablePaths(
            actionableGroups = state.visibleGroups,
            allGroups = state.groups,
            qualities = state.qualities,
        )
}
