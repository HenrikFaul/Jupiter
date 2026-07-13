package com.jupiter.filemanager.data.index

import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks the false-negative guard: a transient failure must stay eligible for a later retry. */
class PerceptualBackfillPolicyTest {

    @Test
    fun transientFingerprintFailureIsRetriedNotStoredAsUnhashable() {
        assertEquals(PerceptualBackfillDecision.Retry, perceptualBackfillDecision(null))
    }

    @Test
    fun explicitUndecodableResultIsPersistedExactlyOnce() {
        assertEquals(
            PerceptualBackfillDecision.Persist(PerceptualFingerprint.UNHASHABLE),
            perceptualBackfillDecision(PerceptualFingerprint.UNHASHABLE),
        )
    }

    @Test
    fun duplicateCleanupCanRequestComparisonWhenContinuousIndexingIsOff() {
        assertEquals(true, mayRunPerceptualBackfill(indexingEnabled = false, explicitUserRequest = true))
        assertEquals(false, mayRunPerceptualBackfill(indexingEnabled = false, explicitUserRequest = false))
    }
}
