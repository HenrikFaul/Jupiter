package com.jupiter.filemanager.data.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the dHash math in [PerceptualHash] (no Android types). */
class PerceptualHashTest {

    /** Row-major 9×8 grid where luminance strictly increases left→right. */
    private fun risingGrid() = IntArray(PerceptualHash.GRID_SIZE) { it % PerceptualHash.GRID_WIDTH }

    /** Strictly decreasing left→right. */
    private fun fallingGrid() =
        IntArray(PerceptualHash.GRID_SIZE) { PerceptualHash.GRID_WIDTH - (it % PerceptualHash.GRID_WIDTH) }

    @Test
    fun `rising gradient sets all 64 bits, falling none`() {
        assertEquals(-1L, PerceptualHash.fromLuminanceGrid(risingGrid())) // 64 ones
        assertEquals(0L, PerceptualHash.fromLuminanceGrid(fallingGrid()))
    }

    @Test
    fun `hash is deterministic for the same grid`() {
        assertEquals(
            PerceptualHash.fromLuminanceGrid(risingGrid()),
            PerceptualHash.fromLuminanceGrid(risingGrid()),
        )
    }

    /**
     * A small local luminance change (what heavy re-compression causes) flips at most the
     * two comparisons that touch the changed sample — the hashes stay NEAR.
     */
    @Test
    fun `small perturbation stays within the near threshold`() {
        val original = PerceptualHash.fromLuminanceGrid(risingGrid())
        val perturbed = risingGrid().also { it[40] += 3 } // bump one interior sample
        val perturbedHash = PerceptualHash.fromLuminanceGrid(perturbed)
        assertTrue(PerceptualHash.hammingDistance(original, perturbedHash) <= 2)
        assertTrue(PerceptualHash.isNear(original, perturbedHash))
    }

    /** Structurally opposite images are far apart — never flagged as the same picture. */
    @Test
    fun `opposite structures are not near`() {
        val a = PerceptualHash.fromLuminanceGrid(risingGrid())
        val b = PerceptualHash.fromLuminanceGrid(fallingGrid())
        assertEquals(64, PerceptualHash.hammingDistance(a, b))
        assertFalse(PerceptualHash.isNear(a, b))
    }

    /** The unhashable sentinel never matches anything — not even itself. */
    @Test
    fun `unhashable sentinel is never near`() {
        assertFalse(PerceptualHash.isNear(PerceptualHash.UNHASHABLE, PerceptualHash.UNHASHABLE))
        assertFalse(PerceptualHash.isNear(PerceptualHash.UNHASHABLE, 0L))
        assertFalse(PerceptualHash.isNear(0L, PerceptualHash.UNHASHABLE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong grid size is rejected`() {
        PerceptualHash.fromLuminanceGrid(IntArray(10))
    }
}
