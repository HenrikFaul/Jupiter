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
}
