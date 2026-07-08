package com.jupiter.filemanager.data.index.dedup

import com.jupiter.filemanager.domain.model.FileType

/**
 * The independent evidence layers the fusion engine combines. Each contributes a normalized
 * [0,1] similarity for a candidate pair; no single layer decides (except IDENTITY, which
 * dominates when present). See [SimilarityScorer].
 */
enum class SimilarityLayer { IDENTITY, METADATA, STRUCTURAL, SAMPLE, PERCEPTUAL, SEMANTIC }

/**
 * Coarse type class that selects the per-type weight vector. Mapped from the app's [FileType].
 */
enum class TypeClass {
    IMAGE, VIDEO, PDF, DOCUMENT, TEXT, CODE, APK, AUDIO, ARCHIVE, BINARY;

    companion object {
        fun fromFileType(type: FileType): TypeClass = when (type) {
            FileType.IMAGE -> IMAGE
            FileType.VIDEO -> VIDEO
            FileType.AUDIO -> AUDIO
            FileType.PDF -> PDF
            FileType.DOCUMENT -> DOCUMENT
            FileType.ARCHIVE -> ARCHIVE
            FileType.APK -> APK
            FileType.CODE -> CODE
            else -> BINARY // FOLDER, OTHER
        }
    }
}

/**
 * Confidence-graded verdict for a candidate pair. Score is [0,100]; each tier has a fixed
 * range and a user-facing label. Auto-actioning (delete) is only ever offered from PROBABLE
 * upward and always requires confirmation.
 */
enum class DedupTier(val minScore: Int, val label: String) {
    UNRELATED(0, "Unrelated"),
    WEAK(20, "Weakly related"),
    POSSIBLE(40, "Possibly similar"),
    PROBABLE(60, "Probably a duplicate"),
    VERY_LIKELY(75, "Very likely a duplicate"),
    EXACT(90, "Exact duplicate");

    companion object {
        /** The tier whose range contains [score] (clamped to [0,100]). */
        fun fromScore(score: Int): DedupTier {
            val s = score.coerceIn(0, 100)
            return entries.last { s >= it.minScore }
        }
    }
}

/** One layer's normalized similarity for a pair. [similarity] is ignored unless [present]. */
data class LayerSignal(
    val layer: SimilarityLayer,
    val similarity: Double,
    val present: Boolean = true,
)

/**
 * A hard constraint that CAPS the score to at most [SimilarityScorer.REVIEW_CAP] no matter how
 * high the raw evidence — e.g. an APK signer mismatch can never be "the same app", and a size
 * ratio far from 1 can never be an exact duplicate. Vetoes pull down, never up.
 */
enum class Veto { SIZE_MISMATCH, SIGNER_MISMATCH, DURATION_MISMATCH, PAGE_COUNT_MISMATCH, TYPE_MISMATCH }

/** The fused verdict + why. [contributions] is per-layer weighted points, for the UI breakdown. */
data class DedupScore(
    val finalScore: Int,
    val tier: DedupTier,
    val confidence: Double,
    val explanation: String,
    val contributions: Map<SimilarityLayer, Double> = emptyMap(),
)
