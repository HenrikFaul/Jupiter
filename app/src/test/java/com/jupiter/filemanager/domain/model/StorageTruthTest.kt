package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTruthTest {

    @Test
    fun separatesPlatformUsageFromInspectableSharedFilesWithoutInventingCategories() {
        val truth = StorageTruth(
            platformTotalBytes = 256_000L,
            platformUsedBytes = 229_000L,
            platformFreeBytes = 27_000L,
            analyzedSharedFileBytes = 114_800L,
        )

        assertEquals(114_200L, truth.usedOutsideSharedAnalysisBytes)
        assertEquals(114_800L, truth.chartedSharedFileBytes)
        assertTrue(truth.analyzedFractionOfUsed in 0f..1f)
    }

    @Test
    fun clampsUnexpectedAnalysisOverrunRatherThanProducingMoreThanOneHundredPercent() {
        val truth = StorageTruth(
            platformTotalBytes = 100L,
            platformUsedBytes = 60L,
            platformFreeBytes = 40L,
            analyzedSharedFileBytes = 90L,
        )

        assertEquals(0L, truth.usedOutsideSharedAnalysisBytes)
        assertEquals(60L, truth.chartedSharedFileBytes)
        assertEquals(1f, truth.analyzedFractionOfUsed)
    }
}
