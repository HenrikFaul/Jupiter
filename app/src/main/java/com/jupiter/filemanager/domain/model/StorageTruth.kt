package com.jupiter.filemanager.domain.model

/**
 * Explicit accounting bridge between Android's whole-volume numbers and the files Jupiscan can
 * inspect. It intentionally does not guess that every byte outside the shared-file scan belongs to
 * apps: it can include app-private data, system-reserved space and other Android-protected areas.
 */
data class StorageTruth(
    val platformTotalBytes: Long,
    val platformUsedBytes: Long,
    val platformFreeBytes: Long,
    val analyzedSharedFileBytes: Long,
) {
    /** Used bytes that the shared-file category scan cannot attribute file-by-file. */
    val usedOutsideSharedAnalysisBytes: Long
        get() = (platformUsedBytes - analyzedSharedFileBytes).coerceAtLeast(0L)

    /** Byte count that can safely be represented by the category chart. */
    val chartedSharedFileBytes: Long
        get() = analyzedSharedFileBytes.coerceAtMost(platformUsedBytes)

    val analyzedFractionOfUsed: Float
        get() = if (platformUsedBytes <= 0L) 0f else {
            (chartedSharedFileBytes.toDouble() / platformUsedBytes.toDouble()).toFloat()
        }

    companion object {
        fun from(overview: StorageOverview): StorageTruth = StorageTruth(
            platformTotalBytes = overview.volume.totalBytes.coerceAtLeast(0L),
            platformUsedBytes = overview.volume.usedBytes.coerceAtLeast(0L),
            platformFreeBytes = overview.volume.availableBytes.coerceAtLeast(0L),
            analyzedSharedFileBytes = overview.totalAnalyzedBytes.coerceAtLeast(0L),
        )
    }
}
