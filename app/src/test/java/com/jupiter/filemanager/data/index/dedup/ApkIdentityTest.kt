package com.jupiter.filemanager.data.index.dedup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for APK relation logic (the APK structural layer). */
class ApkIdentityTest {

    private fun apk(pkg: String, ver: Long, signer: String?) = ApkIdentity(pkg, ver, signer)

    @Test
    fun `same package signer and version is an exact duplicate`() {
        val r = ApkComparator.relationOf(apk("com.x", 5, "SIG"), apk("com.x", 5, "SIG"))
        assertEquals(ApkRelation.SAME_EXACT, r)
        assertTrue(ApkComparator.toSignal(r).second.isEmpty())
    }

    @Test
    fun `same package and signer different version is an update family not a duplicate`() {
        val r = ApkComparator.relationOf(apk("com.x", 5, "SIG"), apk("com.x", 7, "SIG"))
        assertEquals(ApkRelation.SAME_APP_UPDATE, r)
        val (sim, vetoes) = ApkComparator.toSignal(r)
        assertTrue(sim > 0.5)
        assertTrue("an update is not vetoed", vetoes.isEmpty())
    }

    @Test
    fun `same package different signer is flagged and vetoed - never the same app`() {
        val r = ApkComparator.relationOf(apk("com.x", 5, "SIG_A"), apk("com.x", 5, "SIG_B"))
        assertEquals(ApkRelation.DIFFERENT_SIGNER, r)
        assertTrue(ApkComparator.toSignal(r).second.contains(Veto.SIGNER_MISMATCH))
    }

    @Test
    fun `missing signer on either side is treated as a signer mismatch`() {
        assertEquals(ApkRelation.DIFFERENT_SIGNER, ApkComparator.relationOf(apk("com.x", 5, null), apk("com.x", 5, "SIG")))
        assertEquals(ApkRelation.DIFFERENT_SIGNER, ApkComparator.relationOf(apk("com.x", 5, "SIG"), apk("com.x", 5, null)))
    }

    @Test
    fun `different package is unrelated`() {
        assertEquals(ApkRelation.UNRELATED, ApkComparator.relationOf(apk("com.x", 5, "SIG"), apk("com.y", 5, "SIG")))
    }

    /** Fed through the fusion engine, a signer mismatch caps an otherwise-strong APK match. */
    @Test
    fun `signer mismatch caps the fused APK score to review`() {
        val (sim, vetoes) = ApkComparator.toSignal(ApkRelation.DIFFERENT_SIGNER)
        val r = SimilarityScorer.score(
            TypeClass.APK, exactIdentity = false,
            signals = listOf(LayerSignal(SimilarityLayer.STRUCTURAL, sim), LayerSignal(SimilarityLayer.METADATA, 1.0)),
            vetoes = vetoes,
        )
        assertTrue(r.finalScore <= SimilarityScorer.REVIEW_CAP)
    }
}
