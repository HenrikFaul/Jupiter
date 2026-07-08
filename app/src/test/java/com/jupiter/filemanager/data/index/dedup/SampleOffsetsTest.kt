package com.jupiter.filemanager.data.index.dedup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the sample-fingerprint offset math. */
class SampleOffsetsTest {

    @Test
    fun `small file samples the whole thing`() {
        val ranges = SampleOffsets.compute(size = 1000L, chunk = 4096L)
        assertEquals(listOf(0L until 1000L), ranges)
    }

    @Test
    fun `empty or zero-chunk yields no ranges`() {
        assertTrue(SampleOffsets.compute(0L, 4096L).isEmpty())
        assertTrue(SampleOffsets.compute(1000L, 0L).isEmpty())
    }

    @Test
    fun `large file samples are deterministic for the same size`() {
        val a = SampleOffsets.compute(10_000_000L, 4096L)
        val b = SampleOffsets.compute(10_000_000L, 4096L)
        assertEquals("same file → same offsets", a, b)
    }

    @Test
    fun `ranges are ascending, in-bounds, and non-overlapping`() {
        val size = 50_000_000L
        val chunk = 4096L
        val ranges = SampleOffsets.compute(size, chunk, interior = 5)
        assertTrue(ranges.isNotEmpty())
        var prevEnd = -1L
        for (r in ranges) {
            assertTrue("start >= 0", r.first >= 0L)
            assertTrue("end <= size", r.last < size)
            assertTrue("ascending & non-overlapping", r.first > prevEnd)
            prevEnd = r.last
        }
    }

    @Test
    fun `covers first and last regions of a large file`() {
        val ranges = SampleOffsets.compute(20_000_000L, 4096L)
        assertTrue("includes head", ranges.any { it.first == 0L })
        assertTrue("includes tail", ranges.any { it.last >= 20_000_000L - 4096L })
    }
}
