package com.jupiter.filemanager.data.index.dedup

/**
 * Pure, type-aware fusion engine: combines the independent [SimilarityLayer] signals for a
 * candidate pair into one [DedupScore] (score + tier + confidence + human explanation).
 *
 * Design rules (from the dedup blueprint, §4.7 / §9 / §10):
 *  - EXACT identity dominates: a content-hash match is always tier EXACT, whatever else says.
 *  - Weights differ per [TypeClass] (perceptual dominates for images; signer/structure for APKs;
 *    tokens for text/code; identity+sample for opaque binaries).
 *  - Hard [Veto]s CAP the score (never raise it): a signer mismatch can't be "the same app", a
 *    size mismatch can't be an exact duplicate. High evidence in a weak layer cannot override a
 *    veto.
 *  - The result is explainable: [DedupScore.explanation] names the layers that drove it and
 *    [DedupScore.contributions] gives the per-layer weighted points for a UI breakdown.
 */
object SimilarityScorer {

    /** A veto caps the fused score to at most this — never auto-actionable, always REVIEW-able. */
    const val REVIEW_CAP = 59

    /**
     * Per-type weight of each NON-identity layer, summing to 100. Identity is handled as an
     * override, so it is not part of the weighted sum.
     */
    fun weightsFor(type: TypeClass): Map<SimilarityLayer, Int> = when (type) {
        TypeClass.IMAGE -> mapOf(
            SimilarityLayer.METADATA to 10, SimilarityLayer.STRUCTURAL to 10,
            SimilarityLayer.SAMPLE to 5, SimilarityLayer.PERCEPTUAL to 55, SimilarityLayer.SEMANTIC to 20,
        )
        TypeClass.VIDEO -> mapOf(
            SimilarityLayer.METADATA to 15, SimilarityLayer.STRUCTURAL to 20,
            SimilarityLayer.SAMPLE to 5, SimilarityLayer.PERCEPTUAL to 45, SimilarityLayer.SEMANTIC to 15,
        )
        TypeClass.PDF -> mapOf(
            SimilarityLayer.METADATA to 10, SimilarityLayer.STRUCTURAL to 25,
            SimilarityLayer.SAMPLE to 5, SimilarityLayer.PERCEPTUAL to 35, SimilarityLayer.SEMANTIC to 25,
        )
        TypeClass.DOCUMENT -> mapOf(
            SimilarityLayer.METADATA to 10, SimilarityLayer.STRUCTURAL to 25,
            SimilarityLayer.SAMPLE to 5, SimilarityLayer.PERCEPTUAL to 0, SimilarityLayer.SEMANTIC to 60,
        )
        TypeClass.TEXT, TypeClass.CODE -> mapOf(
            SimilarityLayer.METADATA to 10, SimilarityLayer.STRUCTURAL to 20,
            SimilarityLayer.SAMPLE to 10, SimilarityLayer.PERCEPTUAL to 0, SimilarityLayer.SEMANTIC to 60,
        )
        TypeClass.APK -> mapOf(
            SimilarityLayer.METADATA to 20, SimilarityLayer.STRUCTURAL to 60,
            SimilarityLayer.SAMPLE to 5, SimilarityLayer.PERCEPTUAL to 15, SimilarityLayer.SEMANTIC to 0,
        )
        TypeClass.AUDIO -> mapOf(
            SimilarityLayer.METADATA to 20, SimilarityLayer.STRUCTURAL to 25,
            SimilarityLayer.SAMPLE to 5, SimilarityLayer.PERCEPTUAL to 45, SimilarityLayer.SEMANTIC to 5,
        )
        TypeClass.ARCHIVE -> mapOf(
            SimilarityLayer.METADATA to 15, SimilarityLayer.STRUCTURAL to 55,
            SimilarityLayer.SAMPLE to 15, SimilarityLayer.PERCEPTUAL to 0, SimilarityLayer.SEMANTIC to 15,
        )
        TypeClass.BINARY -> mapOf(
            SimilarityLayer.METADATA to 20, SimilarityLayer.STRUCTURAL to 20,
            SimilarityLayer.SAMPLE to 55, SimilarityLayer.PERCEPTUAL to 0, SimilarityLayer.SEMANTIC to 5,
        )
    }

    /**
     * Fuses [signals] for a pair of the given [type].
     *
     * @param exactIdentity true when a content hash proves byte-for-byte equality → tier EXACT.
     * @param signals per-layer normalized similarities (absent layers are ignored and lower
     *   confidence). IDENTITY signals here are ignored — use [exactIdentity].
     * @param vetoes hard caps (signer mismatch, size mismatch, …).
     */
    fun score(
        type: TypeClass,
        exactIdentity: Boolean,
        signals: List<LayerSignal>,
        vetoes: Set<Veto> = emptySet(),
    ): DedupScore {
        if (exactIdentity) {
            return DedupScore(
                finalScore = 100,
                tier = DedupTier.EXACT,
                confidence = 1.0,
                explanation = "Identical content (same hash)",
                contributions = mapOf(SimilarityLayer.IDENTITY to 100.0),
            )
        }

        val weights = weightsFor(type)
        val present = signals.filter { it.present && it.layer != SimilarityLayer.IDENTITY }
        val contributions = LinkedHashMap<SimilarityLayer, Double>()
        var raw = 0.0
        var presentWeight = 0
        for (s in present) {
            val w = weights[s.layer] ?: 0
            if (w == 0) continue
            val points = w * s.similarity.coerceIn(0.0, 1.0)
            contributions[s.layer] = points
            raw += points
            presentWeight += w
        }

        var finalScore = raw.toInt().coerceIn(0, 100)
        val vetoApplied = vetoes.isNotEmpty()
        if (vetoApplied) finalScore = minOf(finalScore, REVIEW_CAP)

        val tier = DedupTier.fromScore(finalScore)
        // Confidence rises with how much of the type's weight mass was actually observed and with
        // the strength of the winning evidence; a veto conflict lowers it.
        val coverage = if (presentWeight == 0) 0.0 else presentWeight / 100.0
        val strength = (raw / 100.0).coerceIn(0.0, 1.0)
        var confidence = (0.4 * coverage + 0.6 * strength).coerceIn(0.0, 1.0)
        if (vetoApplied) confidence *= 0.5

        return DedupScore(
            finalScore = finalScore,
            tier = tier,
            confidence = confidence,
            explanation = explain(type, contributions, vetoes, tier),
            contributions = contributions,
        )
    }

    private fun explain(
        type: TypeClass,
        contributions: Map<SimilarityLayer, Double>,
        vetoes: Set<Veto>,
        tier: DedupTier,
    ): String {
        if (contributions.isEmpty() && vetoes.isEmpty()) return "No comparable evidence"
        val top = contributions.entries.sortedByDescending { it.value }.take(2)
            .joinToString(", ") { "${it.key.name.lowercase()} ${it.value.toInt()}pts" }
        val vetoNote = if (vetoes.isEmpty()) "" else
            " (capped: ${vetoes.joinToString(", ") { it.name.lowercase().replace('_', ' ') }})"
        val lead = when (tier) {
            DedupTier.EXACT -> "Exact"
            DedupTier.VERY_LIKELY, DedupTier.PROBABLE -> "Likely duplicate"
            DedupTier.POSSIBLE -> "Possibly similar"
            else -> "Weakly related"
        }
        return "$lead (${type.name.lowercase()}): $top$vetoNote"
    }
}
