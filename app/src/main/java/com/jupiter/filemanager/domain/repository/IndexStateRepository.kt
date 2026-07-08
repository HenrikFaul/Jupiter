package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.data.index.IndexState
import kotlinx.coroutines.flow.Flow

/**
 * Single authority for the persistent index's life-cycle state.
 *
 * Every screen that treats the index as authoritative MUST gate on
 * [isMetadataComplete] (status == COMPLETE), never on a row count — a partial/interrupted
 * scan leaves rows behind but is NOT complete.
 */
interface IndexStateRepository {

    /** Observes the primary volume's state row (null until the first scan begins). */
    fun observe(): Flow<IndexState?>

    /** Snapshot of the primary volume's state, or null when none exists yet. */
    suspend fun current(): IndexState?

    /** True only when the primary volume's metadata survey has reached COMPLETE. */
    suspend fun isMetadataComplete(): Boolean

    /**
     * True when the index is USABLE for reads: either the survey is COMPLETE, or a previous
     * survey completed (`lastCompleteGeneration > 0`) and its rows are still present while a
     * rescan is RUNNING (or has FAILED). The stale-sweep only runs on success, so a prior
     * complete generation's data survives an in-flight rebuild — read paths (search,
     * analytics, cleanup) should keep serving it instead of degrading to a partial live walk.
     * Scheduling decisions must keep using [isMetadataComplete] (a rescan in progress is NOT
     * complete).
     */
    suspend fun isUsable(): Boolean

    /**
     * Marks a new scan as RUNNING and returns its generation (a monotonically increasing
     * number). Every row the scan writes should be stamped with this generation so stale
     * rows can be swept on success.
     */
    suspend fun beginScan(): Long

    /** Records a successfully completed scan: status COMPLETE, lastCompleteGeneration set. */
    suspend fun completeScan(generation: Long, filesSeen: Long)

    /** Records a failed scan (status FAILED); the previous complete generation stays usable. */
    suspend fun failScan(error: String?)

    /** Resets the state to EMPTY (e.g. when indexing is disabled / the index is cleared). */
    suspend fun reset()
}
