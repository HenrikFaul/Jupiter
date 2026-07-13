package com.jupiter.filemanager.feature.cleanup

import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.MediaQuality

/**
 * Minimum-size filter for the duplicate list. A group is shown when at least one of its copies is
 * at least [minBytes], so the user can focus on the space-worth duplicates (multi-MB photos /
 * multi-GB videos) and hide the many tiny few-KB images.
 */
enum class SizeFilter(val label: String, val minBytes: Long) {
    ALL("All sizes", 0L),
    KB_100("≥ 100 KB", 100L * 1024),
    MB_1("≥ 1 MB", 1L * 1024 * 1024),
    MB_10("≥ 10 MB", 10L * 1024 * 1024),
    MB_100("≥ 100 MB", 100L * 1024 * 1024),
}

/** Explicit size ordering for duplicate groups; kept in UI state so recomposition is stable. */
enum class DuplicateSizeOrder(val label: String) {
    LARGEST_FIRST("Largest first"),
    SMALLEST_FIRST("Smallest first"),
}

/** Keeps exact byte copies and perceptually similar photos as separate review scopes. */
enum class DuplicatePresentation {
    EXACT,
    SIMILAR,
}

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
 * @param permissionRequired true when the scan was skipped because the app lacks
 *   All-Files-Access; the screen renders an actionable CTA instead of spinning.
 * @param analyzingPhotos true while the perceptual fingerprint of the photo library is still being
 *   built in the background — visual (near-duplicate) PHOTO groups keep appearing as it completes,
 *   so the screen shows a "still analyzing photos" hint instead of implying the result is final.
 */
data class DuplicatesUiState(
    val isScanning: Boolean = false,
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val qualities: Map<String, MediaQuality> = emptyMap(),
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val permissionRequired: Boolean = false,
    val sizeFilter: SizeFilter = SizeFilter.ALL,
    val sizeOrder: DuplicateSizeOrder = DuplicateSizeOrder.LARGEST_FIRST,
    val analyzingPhotos: Boolean = false,
    // Additive: kept after the complete pre-v0.51 constructor contract for positional callers.
    val presentation: DuplicatePresentation = DuplicatePresentation.EXACT,
) {
    /** True when there are no groups and scanning has finished (and access is granted). */
    val isEmpty: Boolean
        get() = !isScanning && !permissionRequired && !analyzingPhotos && groups.isEmpty()

    /** Groups that pass the active [sizeFilter], before the exact/similar tab is applied. */
    val sizeFilteredGroups: List<DuplicateGroup>
        get() = if (sizeFilter == SizeFilter.ALL) {
            groups
        } else {
            groups.filter { group -> group.files.any { it.sizeBytes >= sizeFilter.minBytes } }
        }

    /** The only groups that the current tab and size filter make actionable. */
    val visibleGroups: List<DuplicateGroup>
        get() {
            val scoped = sizeFilteredGroups.filter { group ->
                when (presentation) {
                    DuplicatePresentation.EXACT -> !group.similar
                    DuplicatePresentation.SIMILAR -> group.similar
                }
            }
            val byLargestFile = compareBy<DuplicateGroup> { group ->
                group.files.maxOfOrNull { it.sizeBytes } ?: 0L
            }.thenBy { it.hash }
            return when (sizeOrder) {
                DuplicateSizeOrder.LARGEST_FIRST -> scoped.sortedWith(byLargestFile.reversed())
                DuplicateSizeOrder.SMALLEST_FIRST -> scoped.sortedWith(byLargestFile)
            }
        }

    /** All exact + similar items in the active size scope, independent of the selected tab. */
    val totalDuplicateItemCount: Int
        get() = sizeFilteredGroups
            .asSequence()
            .flatMap { it.files.asSequence() }
            .map { it.path }
            .distinct()
            .count()

    fun duplicateItemCount(presentation: DuplicatePresentation): Int = sizeFilteredGroups
        .asSequence()
        .filter { group ->
            when (presentation) {
                DuplicatePresentation.EXACT -> !group.similar
                DuplicatePresentation.SIMILAR -> group.similar
            }
        }
        .flatMap { it.files.asSequence() }
        .map { it.path }
        .distinct()
        .count()

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
