package com.jupiter.filemanager.data.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM proof of the STACKED perceptual layer (aHash + DCT pHash + combined scoring) that
 * now sits on top of the existing dHash: deterministic hashes, small distance under a small
 * perturbation, large distance for structurally different content, threshold tiers, and the
 * dHash-only fallback for legacy rows — the "never rely on a single hash" rule in code.
 */
class PerceptualStackTest {

    /** Deterministic checkerboard grid of [side]×[side] luminance samples. */
    private fun checkerboard(side: Int, dark: Int = 30, bright: Int = 220): IntArray =
        IntArray(side * side) { i ->
            val x = i % side
            val y = i / side
            if ((x + y) % 2 == 0) dark else bright
        }

    /** Smooth horizontal gradient grid. */
    private fun gradient(side: Int): IntArray =
        IntArray(side * side) { i -> (i % side) * 255 / (side - 1) }

    @Test
    fun aHashIsDeterministicAndSeparatesStructure() {
        val a1 = PerceptualHash.aHashFromLuminanceGrid(checkerboard(PerceptualHash.AHASH_GRID))
        val a2 = PerceptualHash.aHashFromLuminanceGrid(checkerboard(PerceptualHash.AHASH_GRID))
        assertEquals("same grid must hash identically", a1, a2)

        val g = PerceptualHash.aHashFromLuminanceGrid(gradient(PerceptualHash.AHASH_GRID))
        assertTrue(
            "checkerboard vs gradient must be far apart",
            PerceptualHash.hammingDistance(a1, g) > 8,
        )
    }

    @Test
    fun pHashToleratesSmallPerturbationButSeparatesDifferentContent() {
        val base = checkerboard(PerceptualHash.PHASH_GRID)
        val p1 = PerceptualHash.pHashFromLuminanceGrid(base)
        assertEquals(
            "same grid must hash identically",
            p1,
            PerceptualHash.pHashFromLuminanceGrid(checkerboard(PerceptualHash.PHASH_GRID)),
        )

        // Mild re-compression noise: nudge a scattering of samples by a small amount.
        val noisy = base.copyOf()
        for (i in noisy.indices step 37) {
            noisy[i] = (noisy[i] + 12).coerceAtMost(255)
        }
        val pNoisy = PerceptualHash.pHashFromLuminanceGrid(noisy)
        assertTrue(
            "small perturbation must stay near (got ${PerceptualHash.hammingDistance(p1, pNoisy)})",
            PerceptualHash.hammingDistance(p1, pNoisy) <= 8,
        )

        val pGradient = PerceptualHash.pHashFromLuminanceGrid(gradient(PerceptualHash.PHASH_GRID))
        assertTrue(
            "different structure must be far apart",
            PerceptualHash.hammingDistance(p1, pGradient) > 12,
        )
    }

    @Test
    fun combinedScoreWeighsAllThreeLayers() {
        assertEquals(0.0, PerceptualHash.combinedScore(0, 0, 0), 1e-9)
        val score = PerceptualHash.combinedScore(2, 4, 5)
        assertEquals(
            PerceptualHash.WEIGHT_DHASH * 2 + PerceptualHash.WEIGHT_PHASH * 4 +
                PerceptualHash.WEIGHT_AHASH * 5,
            score,
            1e-9,
        )
        assertTrue("a close stacked match sits under the strict tier", score <= PerceptualHash.STRICT_SCORE_THRESHOLD)
        assertTrue(
            "far distances land beyond the relaxed tier",
            PerceptualHash.combinedScore(20, 25, 22) > PerceptualHash.RELAXED_SCORE_THRESHOLD,
        )
    }

    @Test
    fun isVisuallySimilarUsesTheStackAndFallsBackForLegacyRows() {
        // Full stack, all layers close → similar.
        assertTrue(PerceptualHash.isVisuallySimilar(0L, 0b11L, 5L, 5L, 9L, 9L))
        // Full stack, pHash+aHash far → NOT similar even though the dHashes are identical:
        // no single hash family decides a match.
        assertFalse(
            PerceptualHash.isVisuallySimilar(
                0L, 0L,
                0L, -1L xor Long.MIN_VALUE, // 63 differing bits
                0L, -1L xor Long.MIN_VALUE,
            ),
        )
        // Legacy row (no pHash/aHash) → dHash-only fallback keeps the previous behaviour.
        assertTrue(PerceptualHash.isVisuallySimilar(0L, 0b11L, null, null, null, null))
        assertFalse(PerceptualHash.isVisuallySimilar(0L, -1L xor Long.MIN_VALUE, null, null, null, null))
        // UNHASHABLE never matches.
        assertFalse(
            PerceptualHash.isVisuallySimilar(PerceptualHash.UNHASHABLE, 0L, null, null, null, null),
        )
    }
}
