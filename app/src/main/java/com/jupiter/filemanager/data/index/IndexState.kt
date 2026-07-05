package com.jupiter.filemanager.data.index

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lifecycle status of a volume's metadata index. Completeness is a STATE, not a row
 * count: a scan that wrote some rows and was then killed is [RUNNING], never [COMPLETE].
 */
enum class IndexStatus { EMPTY, RUNNING, COMPLETE, DIRTY, FAILED }

/**
 * Persistent, per-volume index life-cycle state — the single authority for "is the index
 * trustworthy". Stored in the SAME Room database as [FileIndexEntry] so a database wipe or
 * migration can never leave a stale "complete" flag pointing at an empty index (the state
 * row is wiped with the data, resetting to [IndexStatus.EMPTY] which forces a rebuild).
 *
 * @property volumeId identifier of the storage volume this state describes (primary key).
 * @property metadataStatus current [IndexStatus] of the metadata survey.
 * @property activeScanGeneration the generation of the scan currently running (or the last
 *   one attempted); every row a scan writes is stamped with this so stale rows can be swept.
 * @property lastCompleteGeneration the generation of the last scan that reached COMPLETE, or
 *   0 when none has. Consumers should only trust the index when metadataStatus == COMPLETE.
 * @property scanStartedAt / scanCompletedAt epoch-millis bookends of the active/last scan.
 * @property filesSeen number of files the active/last scan wrote.
 * @property lastError a human-readable failure reason, or null.
 */
@Entity(tableName = "index_state")
data class IndexState(
    @PrimaryKey val volumeId: String,
    val metadataStatus: String = IndexStatus.EMPTY.name,
    val activeScanGeneration: Long = 0L,
    val lastCompleteGeneration: Long = 0L,
    val scanStartedAt: Long = 0L,
    val scanCompletedAt: Long = 0L,
    val filesSeen: Long = 0L,
    val lastError: String? = null,
) {
    val status: IndexStatus
        get() = runCatching { IndexStatus.valueOf(metadataStatus) }.getOrDefault(IndexStatus.EMPTY)

    companion object {
        /** Volume id for the primary shared storage (multi-volume support is a later round). */
        const val PRIMARY_VOLUME = "primary"
    }
}
