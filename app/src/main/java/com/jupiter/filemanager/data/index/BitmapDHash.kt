package com.jupiter.filemanager.data.index

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Reduces an already-decoded [Bitmap] to a 64-bit [PerceptualHash] dHash — the shared last step of
 * every perceptual fingerprint, whatever produced the bitmap:
 *  - a decoded image file ([PerceptualHashSource]);
 *  - a sampled video frame ([AndroidMediaFingerprintSource.videoKeyframeHash]);
 *  - a rendered PDF page ([AndroidMediaFingerprintSource.pdfRenderHash]).
 *
 * Keeping this one place means a re-encode of any of those lands at the same small Hamming distance
 * as its original, and the math is validated once. The bitmap is scaled to the 9×8 luminance grid,
 * converted with Rec. 601 luma weights, and reduced by [PerceptualHash.fromLuminanceGrid].
 */
object BitmapDHash {

    /** dHash of [bitmap]; [PerceptualHash.UNHASHABLE] if it is empty/degenerate. */
    fun of(bitmap: Bitmap): Long {
        if (bitmap.width <= 0 || bitmap.height <= 0) return PerceptualHash.UNHASHABLE
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            PerceptualHash.GRID_WIDTH,
            PerceptualHash.GRID_HEIGHT,
            /* filter = */ true,
        )
        val gray = IntArray(PerceptualHash.GRID_SIZE)
        var i = 0
        for (y in 0 until PerceptualHash.GRID_HEIGHT) {
            for (x in 0 until PerceptualHash.GRID_WIDTH) {
                val pixel = scaled.getPixel(x, y)
                gray[i++] = (
                    Color.red(pixel) * 299 +
                        Color.green(pixel) * 587 +
                        Color.blue(pixel) * 114
                    ) / 1000
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return PerceptualHash.fromLuminanceGrid(gray)
    }
}
