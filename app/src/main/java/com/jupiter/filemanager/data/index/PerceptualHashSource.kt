package com.jupiter.filemanager.data.index

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes an image file into the luminance grid [PerceptualHash] needs and returns its
 * 64-bit dHash.
 *
 * Cost is kept minimal for a per-file background pass: a bounds-only decode first, then a
 * subsampled decode (`inSampleSize`) close to the target size, then one bilinear scale to
 * the 9×8 grid. Even multi-MB photos decode in a few milliseconds this way.
 *
 * Returns [PerceptualHash.UNHASHABLE] for files that exist but cannot be decoded as an
 * image (corrupt/unsupported), so callers can persist the attempt and never retry forever;
 * returns null only for transient/unexpected failures worth retrying later.
 */
@Singleton
class PerceptualHashSource @Inject constructor() {

    fun compute(path: String): Long? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return PerceptualHash.UNHASHABLE
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(path, options)
                ?: return PerceptualHash.UNHASHABLE

            val scaled = Bitmap.createScaledBitmap(
                decoded,
                PerceptualHash.GRID_WIDTH,
                PerceptualHash.GRID_HEIGHT,
                /* filter = */ true,
            )
            if (scaled !== decoded) decoded.recycle()

            val gray = IntArray(PerceptualHash.GRID_SIZE)
            var i = 0
            for (y in 0 until PerceptualHash.GRID_HEIGHT) {
                for (x in 0 until PerceptualHash.GRID_WIDTH) {
                    val pixel = scaled.getPixel(x, y)
                    // Rec. 601 luma weights.
                    gray[i++] = (
                        Color.red(pixel) * 299 +
                            Color.green(pixel) * 587 +
                            Color.blue(pixel) * 114
                        ) / 1000
                }
            }
            scaled.recycle()
            PerceptualHash.fromLuminanceGrid(gray)
        } catch (oom: OutOfMemoryError) {
            // Pathological image; mark unhashable rather than retrying it forever.
            PerceptualHash.UNHASHABLE
        } catch (_: Exception) {
            null
        }
    }

    /** Largest power-of-two subsample that keeps both dimensions comfortably above the grid. */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= TARGET_DECODE_SIZE && h / 2 >= TARGET_DECODE_SIZE) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    private companion object {
        /** Decode down to roughly this many pixels per side before the final 9×8 scale. */
        const val TARGET_DECODE_SIZE = 64
    }
}
