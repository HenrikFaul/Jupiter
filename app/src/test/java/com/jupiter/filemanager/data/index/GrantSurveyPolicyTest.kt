package com.jupiter.filemanager.data.index

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM truth-table tests for [GrantSurveyPolicy] — the decision that makes
 * "give access → the full-storage scan starts" actually hold.
 *
 * These directly encode the user-facing contract: granting access while the index is not yet
 * built must start a survey; but it must not restart one that is already RUNNING, must not
 * rescan a COMPLETE index, must respect the indexing opt-out, and must do nothing without
 * access.
 */
class GrantSurveyPolicyTest {

    /** The case that fixes the bug: access granted, indexing on, index not yet built. */
    @Test
    fun `grant starts survey when access on, indexing enabled, index empty`() {
        assertTrue(GrantSurveyPolicy.shouldStartSurvey(true, indexingEnabled = true, status = IndexStatus.EMPTY))
    }

    /** A prior failed or dirtied index is retried on the next grant/confirm. */
    @Test
    fun `grant retries a failed or dirty index`() {
        assertTrue(GrantSurveyPolicy.shouldStartSurvey(true, indexingEnabled = true, status = IndexStatus.FAILED))
        assertTrue(GrantSurveyPolicy.shouldStartSurvey(true, indexingEnabled = true, status = IndexStatus.DIRTY))
    }

    /** Never restart an in-flight survey (would throw away progress and thrash). */
    @Test
    fun `grant never restarts a running survey`() {
        assertFalse(GrantSurveyPolicy.shouldStartSurvey(true, indexingEnabled = true, status = IndexStatus.RUNNING))
    }

    /** Never redundantly rescan an already-complete index. */
    @Test
    fun `grant never rescans a complete index`() {
        assertFalse(GrantSurveyPolicy.shouldStartSurvey(true, indexingEnabled = true, status = IndexStatus.COMPLETE))
    }

    /** Without full access there is nothing to scan — never start, whatever the state. */
    @Test
    fun `no survey without full access`() {
        for (status in IndexStatus.values()) {
            assertFalse(
                "status=$status",
                GrantSurveyPolicy.shouldStartSurvey(false, indexingEnabled = true, status = status),
            )
        }
    }

    /** The user's opt-out is respected — a grant never forces an index they disabled. */
    @Test
    fun `no survey when indexing disabled`() {
        for (status in IndexStatus.values()) {
            assertFalse(
                "status=$status",
                GrantSurveyPolicy.shouldStartSurvey(true, indexingEnabled = false, status = status),
            )
        }
    }
}
