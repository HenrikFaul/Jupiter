package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageVolumeInfoTest {

    private fun volume(
        totalBytes: Long,
        availableBytes: Long,
    ): StorageVolumeInfo = StorageVolumeInfo(
        id = "id",
        label = "label",
        rootPath = "/storage/emulated/0",
        totalBytes = totalBytes,
        availableBytes = availableBytes,
        isRemovable = false,
        isPrimary = true,
    )

    @Test
    fun usedBytes_isTotalMinusAvailable() {
        val info = volume(totalBytes = 1000L, availableBytes = 250L)
        assertEquals(750L, info.usedBytes)
    }

    @Test
    fun usedBytes_isZeroWhenFullyAvailable() {
        val info = volume(totalBytes = 1000L, availableBytes = 1000L)
        assertEquals(0L, info.usedBytes)
    }

    @Test
    fun usedBytes_coercedToZeroWhenAvailableExceedsTotal() {
        val info = volume(totalBytes = 1000L, availableBytes = 1500L)
        assertEquals(0L, info.usedBytes)
    }

    @Test
    fun usedBytes_neverNegativeWithZeroTotal() {
        val info = volume(totalBytes = 0L, availableBytes = 100L)
        assertEquals(0L, info.usedBytes)
        assertTrue(info.usedBytes >= 0L)
    }

    @Test
    fun usedFraction_isHalfForHalfUsedVolume() {
        val info = volume(totalBytes = 1000L, availableBytes = 500L)
        assertEquals(0.5f, info.usedFraction, 0.0001f)
    }

    @Test
    fun usedFraction_isOneWhenFull() {
        val info = volume(totalBytes = 1000L, availableBytes = 0L)
        assertEquals(1f, info.usedFraction, 0.0001f)
    }

    @Test
    fun usedFraction_isZeroWhenEmpty() {
        val info = volume(totalBytes = 1000L, availableBytes = 1000L)
        assertEquals(0f, info.usedFraction, 0.0001f)
    }

    @Test
    fun usedFraction_alwaysWithinUnitRange() {
        val cases = listOf(
            volume(totalBytes = 1000L, availableBytes = 0L),
            volume(totalBytes = 1000L, availableBytes = 500L),
            volume(totalBytes = 1000L, availableBytes = 1000L),
            volume(totalBytes = 1000L, availableBytes = 1500L),
            volume(totalBytes = 1L, availableBytes = 0L),
        )
        for (info in cases) {
            assertTrue(
                "fraction ${info.usedFraction} out of range",
                info.usedFraction in 0f..1f,
            )
        }
    }

    @Test
    fun usedFraction_isZeroWhenTotalIsZero() {
        val info = volume(totalBytes = 0L, availableBytes = 0L)
        assertEquals(0f, info.usedFraction, 0.0001f)
    }

    @Test
    fun usedFraction_isZeroWhenTotalIsNegative() {
        val info = volume(totalBytes = -100L, availableBytes = 0L)
        assertEquals(0f, info.usedFraction, 0.0001f)
    }
}
