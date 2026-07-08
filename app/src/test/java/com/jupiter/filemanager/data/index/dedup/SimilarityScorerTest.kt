package com.jupiter.filemanager.data.index.dedup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the fusion engine: tiers, type-aware weights, veto caps, exact dominance. */
class SimilarityScorerTest {

    @Test
    fun `exact identity always dominates to tier EXACT regardless of other signals`() {
        val r = SimilarityScorer.score(
            type = TypeClass.IMAGE,
            exactIdentity = true,
            signals = listOf(LayerSignal(SimilarityLayer.PERCEPTUAL, 0.0)),
        )
        assertEquals(100, r.finalScore)
        assertEquals(DedupTier.EXACT, r.tier)
        assertEquals(1.0, r.confidence, 0.0)
    }

    @Test
    fun `strong image perceptual match reaches a duplicate tier`() {
        val r = SimilarityScorer.score(
            type = TypeClass.IMAGE,
            exactIdentity = false,
            signals = listOf(
                LayerSignal(SimilarityLayer.PERCEPTUAL, 0.95),
                LayerSignal(SimilarityLayer.METADATA, 0.9),
            ),
        )
        // perceptual 55*0.95=52.25 + metadata 10*0.9=9 => ~61 => PROBABLE+
        assertTrue("score=${r.finalScore}", r.finalScore >= 60)
        assertTrue(r.tier >= DedupTier.PROBABLE)
        assertTrue("explanation mentions perceptual", r.explanation.contains("perceptual"))
    }

    @Test
    fun `the same perceptual score weighs LESS for a binary than for an image`() {
        val perceptual = listOf(LayerSignal(SimilarityLayer.PERCEPTUAL, 1.0))
        val image = SimilarityScorer.score(TypeClass.IMAGE, false, perceptual)
        val binary = SimilarityScorer.score(TypeClass.BINARY, false, perceptual)
        assertTrue("image ${image.finalScore} > binary ${binary.finalScore}", image.finalScore > binary.finalScore)
        assertEquals("binary ignores perceptual entirely", 0, binary.finalScore)
    }

    @Test
    fun `a veto caps the score to REVIEW no matter how strong the raw evidence`() {
        val r = SimilarityScorer.score(
            type = TypeClass.APK,
            exactIdentity = false,
            signals = listOf(LayerSignal(SimilarityLayer.STRUCTURAL, 1.0), LayerSignal(SimilarityLayer.METADATA, 1.0)),
            vetoes = setOf(Veto.SIGNER_MISMATCH),
        )
        assertTrue("score=${r.finalScore}", r.finalScore <= SimilarityScorer.REVIEW_CAP)
        assertTrue(r.tier < DedupTier.PROBABLE)
        assertTrue(r.explanation.contains("signer mismatch"))
    }

    @Test
    fun `no comparable signals yields UNRELATED`() {
        val r = SimilarityScorer.score(TypeClass.BINARY, false, emptyList())
        assertEquals(0, r.finalScore)
        assertEquals(DedupTier.UNRELATED, r.tier)
    }

    @Test
    fun `tier boundaries map score ranges correctly`() {
        assertEquals(DedupTier.UNRELATED, DedupTier.fromScore(0))
        assertEquals(DedupTier.UNRELATED, DedupTier.fromScore(19))
        assertEquals(DedupTier.WEAK, DedupTier.fromScore(20))
        assertEquals(DedupTier.POSSIBLE, DedupTier.fromScore(40))
        assertEquals(DedupTier.PROBABLE, DedupTier.fromScore(60))
        assertEquals(DedupTier.VERY_LIKELY, DedupTier.fromScore(75))
        assertEquals(DedupTier.EXACT, DedupTier.fromScore(90))
        assertEquals(DedupTier.EXACT, DedupTier.fromScore(100))
    }

    @Test
    fun `every type has weights summing to 100`() {
        for (t in TypeClass.entries) {
            assertEquals("weights for $t", 100, SimilarityScorer.weightsFor(t).values.sum())
        }
    }
}
