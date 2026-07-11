package com.jupiter.filemanager.data.index

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun compute(path: String): Long? = computeAll(path)?.dhash

    /**
     * Decodes [path] ONCE and returns the full stacked fingerprint (dHash + pHash + aHash).
     * [PerceptualFingerprint.UNHASHABLE] for undecodable files (persist, never retry);
     * null only for transient failures worth retrying later.
     */
    fun computeAll(path: String): PerceptualFingerprint? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return PerceptualFingerprint.UNHASHABLE
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(path, options)
                ?: return PerceptualFingerprint.UNHASHABLE

            val fingerprint = BitmapPerceptual.of(decoded) // one decode → all three layers
            decoded.recycle()
            fingerprint
        } catch (oom: OutOfMemoryError) {
            // Pathological image; mark unhashable rather than retrying it forever.
            PerceptualFingerprint.UNHASHABLE
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
