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

    /**
     * A photo-like grid with real LOW-frequency structure: a bright blob at ([cx],[cy]) over a
     * smooth falloff. (A checkerboard is the pathological opposite — its low-frequency DCT block
     * is all-zero, so its median-thresholded bits are pure noise; real photographs are not that.)
     */
    private fun blob(side: Int, cx: Int, cy: Int): IntArray =
        IntArray(side * side) { i ->
            val x = i % side
            val y = i / side
            (235 - 9 * (kotlin.math.abs(x - cx) + kotlin.math.abs(y - cy))).coerceIn(0, 255)
        }

    /** Pre-optimization reference: direct 2-D DCT with cosine evaluated inside the loops. */
    private fun directPHash(gray: IntArray): Long {
        val n = PerceptualHash.PHASH_GRID
        val block = PerceptualHash.AHASH_GRID
        val coefficients = DoubleArray(block * block)
        for (v in 0 until block) {
            for (u in 0 until block) {
                var sum = 0.0
                for (y in 0 until n) {
                    val cosY = kotlin.math.cos((2 * y + 1) * v * Math.PI / (2.0 * n))
                    for (x in 0 until n) {
                        val cosX = kotlin.math.cos((2 * x + 1) * u * Math.PI / (2.0 * n))
                        sum += gray[y * n + x] * cosX * cosY
                    }
                }
                coefficients[v * block + u] = sum
            }
        }
        val sorted = coefficients.copyOfRange(1, coefficients.size).sortedArray()
        val median = (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        var hash = 0L
        for (index in 1 until coefficients.size) {
            if (coefficients[index] > median) hash = hash or (1L shl index)
        }
        return hash
    }

    @Test
    fun pHashToleratesSmallPerturbationButSeparatesDifferentContent() {
        val n = PerceptualHash.PHASH_GRID
        val base = blob(n, cx = 10, cy = 12)
        val p1 = PerceptualHash.pHashFromLuminanceGrid(base)
        assertEquals(
            "same grid must hash identically",
            p1,
            PerceptualHash.pHashFromLuminanceGrid(blob(n, cx = 10, cy = 12)),
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

        // A structurally different picture: the blob in the opposite corner.
        val pOther = PerceptualHash.pHashFromLuminanceGrid(blob(n, cx = 25, cy = 4))
        assertTrue(
            "different structure must be far apart " +
                "(got ${PerceptualHash.hammingDistance(p1, pOther)})",
            PerceptualHash.hammingDistance(p1, pOther) > 12,
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

    @Test
    fun precomputedCosineBasisPreservesThePersistedPHashBits() {
        val n = PerceptualHash.PHASH_GRID
        val richGrid = IntArray(n * n) { index ->
            val x = index % n
            val y = index / n
            (index * 73 + y * 29 + x * y * 7).and(255)
        }

        assertEquals(
            "the DCT speed-up must not change stored descriptor meaning",
            directPHash(richGrid),
            PerceptualHash.pHashFromLuminanceGrid(richGrid),
        )
    }

    @Test
    fun samePictureEvidenceRejectsAThirdFamilyCatastrophicDisagreement() {
        // This passed the old `d <= 8 && (p <= 10 || a <= 10)` gate because dHash+pHash happened
        // to agree. The fused score now exposes that aHash is the exact opposite instead of letting
        // a single OR branch place unrelated images in one cleanup card.
        val evidence = PerceptualHash.samePictureEvidence(
            dA = 0L,
            dB = 0L,
            pA = 0L,
            pB = 0L,
            aA = 0L,
            aB = -1L,
        )

        assertTrue(evidence.descriptorComplete)
        assertEquals(64, evidence.aHashDistance)
        assertFalse(evidence.matches)
    }

    @Test
    fun samePictureEvidenceAcceptsBalancedCloseStacksAndFailsClosedWhenIncomplete() {
        val close = PerceptualHash.samePictureEvidence(
            dA = 0L,
            dB = 0xFFL,
            pA = 0L,
            pB = 0x3FFL,
            aA = 0L,
            aB = 0x3FFL,
        )
        assertTrue(close.matches)
        assertTrue(close.combinedScore!! <= PerceptualHash.SAME_PICTURE_SCORE_THRESHOLD)

        val incomplete = PerceptualHash.samePictureEvidence(
            dA = 0L,
            dB = 0L,
            pA = null,
            pB = 0L,
            aA = 0L,
            aB = 0L,
        )
        assertFalse(incomplete.descriptorComplete)
        assertFalse(incomplete.matches)
    }

    @Test
    fun samePictureEvidenceRecoversOnlyThreeFamilyConsensusBeyondTheStandardDhashGate() {
        val smoothReencode = PerceptualHash.samePictureEvidence(
            dA = 0L,
            dB = 0x3FFL, // ten dHash bits: two beyond the standard gate
            pA = 0L,
            pB = 0b11L,
            aA = 0L,
            aB = 0b111L,
        )
        assertTrue(smoothReencode.matches)

        val oneMoreDhashBit = PerceptualHash.samePictureEvidence(
            dA = 0L,
            dB = 0x7FFL,
            pA = 0L,
            pB = 0b11L,
            aA = 0L,
            aB = 0b111L,
        )
        assertFalse(oneMoreDhashBit.matches)

        val thirdFamilyDisagrees = PerceptualHash.samePictureEvidence(
            dA = 0L,
            dB = 0x3FFL,
            pA = 0L,
            pB = 0b11L,
            aA = 0L,
            aB = 0x1FL,
        )
        assertFalse(thirdFamilyDisagrees.matches)

        val smoothPHashAtBound = PerceptualHash.samePictureEvidence(
            dA = 0L,
            dB = 0b11L,
            pA = 0L,
            pB = 0xFFFF_FFFFL,
            aA = 0L,
            aB = 0b1111L,
        )
        assertTrue(smoothPHashAtBound.matches)

        val smoothPHashBeyondBound = PerceptualHash.samePictureEvidence(
            dA = 0L,
            dB = 0b11L,
            pA = 0L,
            pB = 0x1_FFFF_FFFFL,
            aA = 0L,
            aB = 0b1111L,
        )
        assertFalse(smoothPHashBeyondBound.matches)
    }
}
