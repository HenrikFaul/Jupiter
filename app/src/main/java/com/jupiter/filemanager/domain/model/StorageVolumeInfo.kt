package com.jupiter.filemanager.domain.model

/**
 * Describes a single mounted storage volume (e.g. internal storage or an SD card),
 * including capacity figures used by the home/cleanup screens.
 */
data class StorageVolumeInfo(
    val id: String,
    val label: String,
    val rootPath: String,
    val totalBytes: Long,
    val availableBytes: Long,
    val isRemovable: Boolean,
    val isPrimary: Boolean,
) {
    /** Bytes currently in use; never negative even if reported figures are inconsistent. */
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)

    /** Fraction of the volume that is used, in the range [0f, 1f]. Returns 0f when capacity is unknown. */
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else usedBytes.toFloat() / totalBytes.toFloat()
}
