package com.jupiter.filemanager.domain.model

/**
 * High-level categories used when summarizing how storage is consumed on a volume.
 */
enum class StorageCategory {
    IMAGES,
    VIDEOS,
    AUDIO,
    DOCUMENTS,
    ARCHIVES,
    APPS,
    DOWNLOADS,
    OTHER,
}

/**
 * Aggregated usage for a single [StorageCategory].
 *
 * @param category the category this usage describes.
 * @param sizeBytes total bytes consumed by files in this category.
 * @param fileCount number of files counted toward this category.
 */
data class CategoryUsage(
    val category: StorageCategory,
    val sizeBytes: Long,
    val fileCount: Int,
)

/**
 * A snapshot describing how a [StorageVolumeInfo] is being used, broken down by category.
 *
 * @param volume the volume that was analyzed.
 * @param categories per-category usage breakdown.
 * @param totalAnalyzedBytes total number of bytes that were analyzed to produce this overview.
 */
data class StorageOverview(
    val volume: StorageVolumeInfo,
    val categories: List<CategoryUsage>,
    val totalAnalyzedBytes: Long,
)

/**
 * A group of files that share the same content hash and are therefore considered duplicates.
 *
 * @param hash the content hash shared by every file in [files].
 * @param files the files belonging to this duplicate group.
 * @param similar true when this is a VISUAL near-duplicate group (same photo at different
 *   resolution/format, matched by perceptual dHash) rather than a byte-identical group. The copies
 *   differ in bytes, so the UI labels it "similar" and keeping the largest/highest-quality is the
 *   sensible default.
 */
data class DuplicateGroup(
    val hash: String,
    val files: List<FileItem>,
    val similar: Boolean = false,
) {
    /**
     * The number of bytes that could be reclaimed by keeping a single copy and removing the rest.
     * Returns 0 when the group does not actually contain duplicates.
     */
    val wastedBytes: Long
        get() = if (files.size <= 1) 0L else files.drop(1).sumOf { it.sizeBytes }
}
