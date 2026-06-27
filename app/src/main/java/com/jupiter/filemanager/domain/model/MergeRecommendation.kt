package com.jupiter.filemanager.domain.model

/**
 * A smart-merge recommendation for a group of duplicate files.
 *
 * Built from a real [DuplicateGroup] returned by storage analytics: keep one
 * representative file and mark the rest as removable to reclaim space.
 */
data class MergeRecommendation(
    val group: DuplicateGroup,
    val recommendedKeepPath: String,
    val removablePaths: List<String>,
) {
    /** Bytes reclaimable if the removable copies are deleted. */
    val reclaimableBytes: Long
        get() = group.wastedBytes
}
