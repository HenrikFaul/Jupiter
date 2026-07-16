package com.jupiter.filemanager.data.index

/**
 * Pure 64-bit difference-hash (dHash) core for NEAR-duplicate image detection.
 *
 * A dHash captures the image's luminance STRUCTURE, not its bytes: the image is reduced to
 * a [GRID_WIDTH]×[GRID_HEIGHT] grayscale grid and each of the 64 bits records whether a
 * pixel is darker than its right-hand neighbour. Re-encoding (PNG↔JPEG), resizing, or
 * re-compressing the same picture leaves the structure — and therefore the hash — almost
 * unchanged, so two images are "the same picture" when the Hamming distance between their
 * hashes is small. Byte-identical duplicates are handled separately by the content hash;
 * this catches the pairs the content hash cannot.
 *
 * This object is deliberately free of any Android type so the math is fully unit-testable
 * on the JVM; decoding a real image file into the luminance grid lives in
 * [PerceptualHashSource].
 */
object PerceptualHash {

    /** Grid is 9 wide × 8 tall: 8 horizontal comparisons per row × 8 rows = 64 bits. */
    const val GRID_WIDTH = 9
    const val GRID_HEIGHT = 8

    /** Number of luminance samples expected by [fromLuminanceGrid]. */
    const val GRID_SIZE = GRID_WIDTH * GRID_HEIGHT

    /**
     * Max Hamming distance (of 64) at which two images count as the same picture.
     * Re-encodes/resizes of the same image typically land at 0–6; unrelated images
     * average ~32. 8 keeps false positives rare while tolerating strong recompression.
     */
    const val DEFAULT_NEAR_THRESHOLD = 8

    /**
     * Sentinel stored for files that were TRIED but cannot be decoded (corrupt/unsupported),
     * so the backfill never retries them forever. Excluded from all comparisons.
     */
    const val UNHASHABLE = Long.MIN_VALUE

    /**
     * Computes the dHash from a row-major [GRID_WIDTH]×[GRID_HEIGHT] luminance grid
     * (values 0–255). Bit (row*8+col) is set when grid[row][col] < grid[row][col+1].
     */
    fun fromLuminanceGrid(gray: IntArray): Long {
        require(gray.size == GRID_SIZE) {
            "expected $GRID_SIZE luminance samples, got ${gray.size}"
        }
        var hash = 0L
        for (row in 0 until GRID_HEIGHT) {
            for (col in 0 until GRID_WIDTH - 1) {
                val left = gray[row * GRID_WIDTH + col]
                val right = gray[row * GRID_WIDTH + col + 1]
                if (left < right) {
                    hash = hash or (1L shl (row * (GRID_WIDTH - 1) + col))
                }
            }
        }
        return hash
    }

    /** Number of differing bits between two hashes (0 = structurally identical). */
    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** True when [a] and [b] are close enough to be the same picture. */
    fun isNear(a: Long, b: Long, threshold: Int = DEFAULT_NEAR_THRESHOLD): Boolean {
        if (a == UNHASHABLE || b == UNHASHABLE) return false
        return hammingDistance(a, b) <= threshold
    }

    // ------------------------------------------------------------------
    // Stacked perceptual layer: aHash + pHash on top of the dHash above.
    // One hash family alone has characteristic blind spots (dHash: smooth
    // gradients; aHash: brightness-preserving edits; pHash: none of the cheap
    // failure modes but costlier). Combining all three makes the match
    // decision robust — no single hash, no single pass.
    // ------------------------------------------------------------------

    /** aHash operates on an 8×8 grid (64 samples). */
    const val AHASH_GRID = 8
    const val AHASH_SIZE = AHASH_GRID * AHASH_GRID

    /** pHash reduces a 32×32 grid via DCT to its top-left 8×8 low-frequency block. */
    const val PHASH_GRID = 32
    const val PHASH_SIZE = PHASH_GRID * PHASH_GRID

    /**
     * Average hash: bit i is set when sample i is above the grid mean. Cheap and robust to
     * re-compression; blind to brightness-preserving edits (which dHash/pHash catch).
     * Input: row-major 8×8 luminance grid (0–255).
     */
    fun aHashFromLuminanceGrid(gray: IntArray): Long {
        require(gray.size == AHASH_SIZE) { "expected $AHASH_SIZE samples, got ${gray.size}" }
        var sum = 0L
        for (v in gray) sum += v
        val mean = sum / AHASH_SIZE
        var hash = 0L
        for (i in 0 until AHASH_SIZE) {
            if (gray[i] > mean) hash = hash or (1L shl i)
        }
        return hash
    }

    /**
     * DCT perceptual hash: 32×32 luminance grid → 2-D DCT-II → keep the top-left 8×8
     * low-frequency block, bit set when the coefficient is above the MEDIAN of that block
     * (DC term excluded — it only encodes overall brightness). The classic pHash — the most
     * edit-robust of the three layers. Input: row-major 32×32 luminance grid (0–255).
     */
    fun pHashFromLuminanceGrid(gray: IntArray): Long {
        require(gray.size == PHASH_SIZE) { "expected $PHASH_SIZE samples, got ${gray.size}" }
        val n = PHASH_GRID
        // Separable 2-D DCT-II: rows then columns; only the first 8 output rows/cols are
        // computed, keeping this ~8/32 of a full transform.
        val rowPass = Array(n) { DoubleArray(AHASH_GRID) }
        for (y in 0 until n) {
            for (u in 0 until AHASH_GRID) {
                var acc = 0.0
                for (x in 0 until n) {
                    acc += gray[y * n + x] * kotlin.math.cos((2 * x + 1) * u * Math.PI / (2.0 * n))
                }
                rowPass[y][u] = acc
            }
        }
        val coeffs = DoubleArray(AHASH_SIZE)
        for (v in 0 until AHASH_GRID) {
            for (u in 0 until AHASH_GRID) {
                var acc = 0.0
                for (y in 0 until n) {
                    acc += rowPass[y][u] * kotlin.math.cos((2 * y + 1) * v * Math.PI / (2.0 * n))
                }
                coeffs[v * AHASH_GRID + u] = acc
            }
        }
        val sorted = coeffs.copyOfRange(1, AHASH_SIZE).sortedArray()
        val median = (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        var hash = 0L
        for (i in 1 until AHASH_SIZE) {
            if (coeffs[i] > median) hash = hash or (1L shl i)
        }
        return hash
    }

    /** Relative weights of the three layers in [combinedScore] (pHash trusted most). */
    const val WEIGHT_DHASH = 0.35
    const val WEIGHT_PHASH = 0.45
    const val WEIGHT_AHASH = 0.20

    /**
     * STRONG-duplicate ceiling for [combinedScore]: matches this close are the same picture
     * with high confidence (typical re-encodes score 0–3).
     */
    const val STRICT_SCORE_THRESHOLD = 5.0

    /**
     * NEAR-duplicate ceiling for [combinedScore]: matches between strict and relaxed are
     * surfaced for review, never auto-acted on. Unrelated images average ~30.
     */
    const val RELAXED_SCORE_THRESHOLD = 10.0

    /**
     * Weighted Hamming score across the three layers — lower is more similar:
     * `w1*dHashDist + w2*pHashDist + w3*aHashDist`.
     */
    fun combinedScore(dDist: Int, pDist: Int, aDist: Int): Double =
        WEIGHT_DHASH * dDist + WEIGHT_PHASH * pDist + WEIGHT_AHASH * aDist

    /**
     * Stacked match decision for two images: true when the weighted score is within
     * [RELAXED_SCORE_THRESHOLD]. Falls back to the dHash-only [isNear] when either side
     * lacks the extra layers (rows fingerprinted before they existed) — never a regression
     * against the previous dHash-only behaviour.
     */
    fun isVisuallySimilar(
        dA: Long, dB: Long,
        pA: Long?, pB: Long?,
        aA: Long?, aB: Long?,
        dhashThreshold: Int = DEFAULT_NEAR_THRESHOLD,
    ): Boolean {
        if (dA == UNHASHABLE || dB == UNHASHABLE) return false
        if (pA == null || pB == null || aA == null || aB == null ||
            pA == UNHASHABLE || pB == UNHASHABLE || aA == UNHASHABLE || aB == UNHASHABLE
        ) {
            return hammingDistance(dA, dB) <= dhashThreshold
        }
        val score = combinedScore(
            hammingDistance(dA, dB),
            hammingDistance(pA, pB),
            hammingDistance(aA, aB),
        )
        return score <= RELAXED_SCORE_THRESHOLD
    }

    /**
     * High-precision "same picture" decision used by duplicate cleanup/arrival alerts. Unlike the
     * broader visual-similarity helper this never lets a lone dHash decide, and every family has an
     * independent ceiling. Rows missing the stacked descriptor wait for backfill instead of being
     * surfaced as a potentially destructive false positive.
     */
    fun isSamePicture(
        dA: Long, dB: Long,
        pA: Long?, pB: Long?,
        aA: Long?, aB: Long?,
        geometryA: Long? = null,
        geometryB: Long? = null,
        dhashThreshold: Int = DEFAULT_NEAR_THRESHOLD,
    ): Boolean {
        if (dA == UNHASHABLE || dB == UNHASHABLE ||
            pA == null || pB == null || aA == null || aB == null ||
            pA == UNHASHABLE || pB == UNHASHABLE || aA == UNHASHABLE || aB == UNHASHABLE
        ) return false
        if (!CompactMetadataCodec.dimensionsCompatible(geometryA, geometryB)) return false
        val d = hammingDistance(dA, dB)
        val p = hammingDistance(pA, pB)
        val a = hammingDistance(aA, aB)
        // dHash remains the candidate geometry gate, but at least one independent family must
        // confirm it. pHash can be unstable on very smooth gradients (same JPEG may flip many
        // near-zero DCT coefficients), while aHash remains stable there; requiring BOTH would miss
        // genuine re-encodes, accepting dHash ALONE recreates false positives.
        return d <= dhashThreshold && (p <= 10 || a <= 10)
    }
}

/**
 * The full stacked perceptual fingerprint of one image — all three layers from a single decode.
 * [PerceptualHash.UNHASHABLE] in every layer means "tried, undecodable" (never retried).
 */
data class PerceptualFingerprint(
    val dhash: Long,
    val phash: Long,
    val ahash: Long,
    val width: Int = 0,
    val height: Int = 0,
) {
    val visualGeometry: Long?
        get() = CompactMetadataCodec.packDimensions(width, height)

    companion object {
        /** Fingerprint stored for undecodable files so they are never retried or matched. */
        val UNHASHABLE = PerceptualFingerprint(
            PerceptualHash.UNHASHABLE,
            PerceptualHash.UNHASHABLE,
            PerceptualHash.UNHASHABLE,
        )
    }
}
