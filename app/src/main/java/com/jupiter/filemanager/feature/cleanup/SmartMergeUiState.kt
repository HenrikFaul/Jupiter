package com.jupiter.filemanager.feature.cleanup

import com.jupiter.filemanager.domain.model.MediaQuality
import com.jupiter.filemanager.domain.model.MergeRecommendation

/**
 * Immutable UI state for the Smart Merge screen.
 *
 * Smart Merge builds [MergeRecommendation]s from real duplicate groups detected
 * on the primary storage volume. For each group it recommends keeping a single
 * "best" copy and removing the redundant duplicates. The user can override the
 * keep selection per group before applying the merge.
 *
 * @param isScanning whether a duplicate scan is currently in progress.
 * @param recommendations merge recommendations discovered so far.
 * @param keepSelections per-group override of which path to keep, keyed by the
 *   group's content hash. When a hash is absent the recommendation's
 *   [MergeRecommendation.recommendedKeepPath] is used.
 * @param qualities probed media-quality info keyed by file path, used to rank
 *   copies and surface a human-readable quality label under each file.
 * @param isMerging whether a merge (delete-the-rest) operation is running.
 * @param errorMessage a transient error message to surface, or null.
 * @param infoMessage a transient informational message (e.g. after a merge), or null.
 */
data class SmartMergeUiState(
    val isScanning: Boolean = false,
    val recommendations: List<MergeRecommendation> = emptyList(),
    val keepSelections: Map<String, String> = emptyMap(),
    val qualities: Map<String, MediaQuality> = emptyMap(),
    val isMerging: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    /** True when there are no recommendations and scanning has finished. */
    val isEmpty: Boolean
        get() = !isScanning && recommendations.isEmpty()

    /** Total reclaimable bytes across every recommendation. */
    val totalReclaimableBytes: Long
        get() = recommendations.sumOf { it.reclaimableBytes }

    /** Number of duplicate copies that would be removed across all groups. */
    val totalRemovableCount: Int
        get() = recommendations.sumOf { rec ->
            (rec.group.files.size - 1).coerceAtLeast(0)
        }

    /**
     * Returns the path the user has chosen (or the recommended default) to keep
     * for the recommendation identified by [groupHash].
     */
    fun keepPathFor(groupHash: String, recommendedKeepPath: String): String =
        keepSelections[groupHash] ?: recommendedKeepPath

    /** Returns the probed quality label for [path], or an empty string if none. */
    fun qualityLabelFor(path: String): String =
        qualities[path]?.label.orEmpty()
}
