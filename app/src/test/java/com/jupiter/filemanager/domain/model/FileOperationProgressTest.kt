package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationProgressTest {

    private fun progress(
        processedBytes: Long,
        totalBytes: Long,
    ) = FileOperationProgress(
        type = FileOperationType.COPY,
        state = OperationState.RUNNING,
        processedBytes = processedBytes,
        totalBytes = totalBytes,
    )

    @Test
    fun fraction_isZero_whenTotalBytesIsZero() {
        assertEquals(0f, progress(processedBytes = 0L, totalBytes = 0L).fraction, 0f)
    }

    @Test
    fun fraction_isZero_whenTotalBytesIsNegative() {
        assertEquals(0f, progress(processedBytes = 100L, totalBytes = -50L).fraction, 0f)
    }

    @Test
    fun fraction_isZero_whenTotalBytesIsZero_evenWithProcessedBytes() {
        assertEquals(0f, progress(processedBytes = 500L, totalBytes = 0L).fraction, 0f)
    }

    @Test
    fun fraction_isHalf_atMidProgress() {
        assertEquals(0.5f, progress(processedBytes = 50L, totalBytes = 100L).fraction, 1e-6f)
    }

    @Test
    fun fraction_isQuarter_atQuarterProgress() {
        assertEquals(0.25f, progress(processedBytes = 25L, totalBytes = 100L).fraction, 1e-6f)
    }

    @Test
    fun fraction_isOne_whenComplete() {
        assertEquals(1f, progress(processedBytes = 100L, totalBytes = 100L).fraction, 0f)
    }

    @Test
    fun fraction_isClampedToOne_whenProcessedExceedsTotal() {
        val f = progress(processedBytes = 250L, totalBytes = 100L).fraction
        assertEquals(1f, f, 0f)
        assertTrue("fraction must be <= 1f", f <= 1f)
    }

    @Test
    fun fraction_isZero_whenProcessedBytesIsZero() {
        assertEquals(0f, progress(processedBytes = 0L, totalBytes = 100L).fraction, 0f)
    }

    @Test
    fun fraction_alwaysInUnitRange() {
        val cases = listOf(
            0L to 0L,
            0L to 100L,
            50L to 100L,
            100L to 100L,
            200L to 100L,
            7L to 3L,
            -10L to 100L,
        )
        for ((processed, total) in cases) {
            val f = progress(processedBytes = processed, totalBytes = total).fraction
            assertTrue("fraction $f out of range for ($processed, $total)", f in 0f..1f)
        }
    }
}
