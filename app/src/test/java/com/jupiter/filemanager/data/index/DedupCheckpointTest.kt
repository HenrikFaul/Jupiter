package com.jupiter.filemanager.data.index

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for [DedupCheckpoint.advance] — the guarantee that the reconciler's checkpoint
 * always moves forward, so a `DATE_ADDED > checkpoint` delta query can never loop on the same
 * rows (the failure mode that would either hang the reconcile or re-alert forever).
 */
class DedupCheckpointTest {

    @Test
    fun `advances to the batch max when it moved forward`() {
        assertEquals(1_500L, DedupCheckpoint.advance(since = 1_000L, batchMax = 1_500L))
    }

    @Test
    fun `bumps by one second when the whole batch shares the current timestamp`() {
        // Every row had DATE == since; advancing to `since` would re-query them forever.
        assertEquals(1_001L, DedupCheckpoint.advance(since = 1_000L, batchMax = 1_000L))
    }

    @Test
    fun `never moves backwards even if a stale batch max is smaller`() {
        assertEquals(1_001L, DedupCheckpoint.advance(since = 1_000L, batchMax = 900L))
    }
}
