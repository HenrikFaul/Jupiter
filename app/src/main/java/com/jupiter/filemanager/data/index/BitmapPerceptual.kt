package com.jupiter.filemanager.data.index

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Reduces one already-decoded [Bitmap] to the full stacked [PerceptualFingerprint]
 * (dHash + pHash + aHash) in a single pass — the image is normalized once (fixed-size
 * grayscale grids from the same decode) and each layer reads its own grid:
 *  - dHash: 9×8 (via the shared [BitmapDHash] math),
 *  - pHash: 32×32 → DCT,
 *  - aHash: 8×8 → mean threshold.
 *
 * Keeping all three next to each other guarantees every layer sees the SAME normalized
 * input, so their Hamming distances are comparable and the combined score is meaningful.
 */
object BitmapPerceptual {

    /** Full fingerprint of [bitmap]; [PerceptualFingerprint.UNHASHABLE] if degenerate. */
    fun of(bitmap: Bitmap): PerceptualFingerprint {
        if (bitmap.width <= 0 || bitmap.height <= 0) return PerceptualFingerprint.UNHASHABLE
        val dhash = BitmapDHash.of(bitmap)
        val phash = PerceptualHash.pHashFromLuminanceGrid(
            luminanceGrid(bitmap, PerceptualHash.PHASH_GRID, PerceptualHash.PHASH_GRID),
        )
        val ahash = PerceptualHash.aHashFromLuminanceGrid(
            luminanceGrid(bitmap, PerceptualHash.AHASH_GRID, PerceptualHash.AHASH_GRID),
        )
        return PerceptualFingerprint(dhash = dhash, phash = phash, ahash = ahash)
    }

    /** Scales [bitmap] to [w]×[h] and converts to a row-major Rec. 601 luminance grid. */
    private fun luminanceGrid(bitmap: Bitmap, w: Int, h: Int): IntArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, /* filter = */ true)
        val gray = IntArray(w * h)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = scaled.getPixel(x, y)
                gray[i++] = (
                    Color.red(pixel) * 299 +
                        Color.green(pixel) * 587 +
                        Color.blue(pixel) * 114
                    ) / 1000
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return gray
    }
}
