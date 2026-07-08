package com.jupiter.filemanager.data.media

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Source media dimensions in pixels (already normalised for orientation, i.e.
 * [width] / [height] are the decoded pixel dimensions).
 */
data class MediaDimensions(val width: Int, val height: Int) {
    /** Long edge of the media in pixels. */
    val longEdge: Int get() = max(width, height)

    /** Short edge of the media in pixels. */
    val shortEdge: Int get() = min(width, height)
}

/**
 * A recommendable compression target.
 *
 * @param label human-facing name shown on a chip ("1080p", "Match screen", …).
 * @param targetLongEdgePx the long edge (in px) the media's long edge is scaled
 *   down to. The short edge is derived from the source aspect ratio by the
 *   compressor, so only the long edge is captured here.
 * @param bitrateBps a suggested video bitrate for this resolution, derived from a
 *   pixels * framerate heuristic. Best-effort — the compressor may treat it as a
 *   hint or ignore it for resolution-only compression.
 * @param recommended whether this is the single best preset for the current
 *   device + source pairing (exactly one preset in a list is marked recommended).
 */
data class CompressPreset(
    val label: String,
    val targetLongEdgePx: Int,
    val bitrateBps: Int,
    val recommended: Boolean,
)

/**
 * Reads the phone's *actual* display hardware and turns it into sensible,
 * never-upscale compression recommendations.
 *
 * The core idea: there is no point keeping a 4K video on a 1080p screen, so the
 * best target is the smaller of "what the screen can show" and "what the source
 * already is". [recommendedPresets] therefore only ever offers presets whose long
 * edge is strictly smaller than the source (never upscales) and marks the one
 * that best matches the display as [CompressPreset.recommended].
 */
@Singleton
class DeviceDisplayProfile @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** The true display size + identity of the current device. */
    data class Display(
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val model: String,
    ) {
        /** Long edge of the display in px (orientation-independent). */
        val longEdge: Int get() = max(widthPx, heightPx)

        /** Short edge of the display in px (orientation-independent). */
        val shortEdge: Int get() = min(widthPx, heightPx)
    }

    /**
     * The device's true, physical display size.
     *
     * On API 30+ this uses [WindowManager.getCurrentWindowMetrics], whose bounds
     * include system decorations and therefore report the full hardware panel
     * size rather than the app's (possibly letterboxed / multi-window) area. On
     * older releases it falls back to [DisplayMetrics]. Never throws: any failure
     * degrades to the [DisplayMetrics] path and, failing that, a 1080p default.
     */
    fun display(): Display {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        var widthPx = metrics.widthPixels
        var heightPx = metrics.heightPixels

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = context.getSystemService(WindowManager::class.java)
                val bounds = wm?.currentWindowMetrics?.bounds
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    widthPx = bounds.width()
                    heightPx = bounds.height()
                }
            } catch (_: Throwable) {
                // Fall back to the DisplayMetrics values captured above.
            }
        }

        if (widthPx <= 0 || heightPx <= 0) {
            // Last-resort sane default so callers never divide by / scale to zero.
            widthPx = 1080
            heightPx = 1920
        }

        return Display(
            widthPx = widthPx,
            heightPx = heightPx,
            densityDpi = if (metrics.densityDpi > 0) metrics.densityDpi else DisplayMetrics.DENSITY_DEFAULT,
            model = Build.MODEL ?: "Android device",
        )
    }

    /**
     * Builds the list of compression presets for a given [source] media size.
     *
     * Rules (per contract):
     *  - Candidates: MatchScreen, 1080p, 720p, 480p.
     *  - Never upscale: a preset is only included if its long edge is strictly
     *    smaller than the source's long edge. (If [source] is null — dimensions
     *    unknown — all screen-appropriate candidates are offered.)
     *  - "Screen appropriate": presets whose long edge is <= the display's long
     *    edge are the recommended set; the single best (largest that still fits
     *    the screen, capped by the source) is marked [CompressPreset.recommended].
     *  - Each preset carries a suggested bitrate via
     *    width * height * framerate * 0.07 (framerate = 30).
     *
     * The returned list is ordered largest -> smallest and always marks exactly
     * one preset recommended when non-empty.
     */
    fun recommendedPresets(source: MediaDimensions?): List<CompressPreset> {
        val display = display()
        val displayLong = display.longEdge

        // Candidate long edges. "Match screen" tracks the real panel; the rest are
        // the familiar broadcast tiers keyed by their long edge (16:9 reference).
        data class Candidate(val label: String, val longEdge: Int)

        val candidates = listOf(
            Candidate("Match screen", displayLong),
            Candidate("1080p", 1920),
            Candidate("720p", 1280),
            Candidate("480p", 854),
        )

        val sourceLong = source?.longEdge

        // Filter: never upscale (strictly smaller than source when source known),
        // and drop non-positive / degenerate edges.
        val filtered = candidates.filter { c ->
            c.longEdge in 1 until (sourceLong ?: Int.MAX_VALUE)
        }

        // De-duplicate by long edge (e.g. a 1080p panel makes "Match screen" and
        // "1080p" collide) keeping the first (more descriptive) label, then order
        // largest -> smallest.
        val deduped = filtered
            .distinctBy { it.longEdge }
            .sortedByDescending { it.longEdge }

        if (deduped.isEmpty()) return emptyList()

        // The recommended preset is the largest candidate that still fits the
        // screen (long edge <= display long edge). If none fits the screen (all
        // candidates exceed it — unusual), fall back to the largest available.
        val recommendedLongEdge = deduped
            .filter { it.longEdge <= displayLong }
            .maxByOrNull { it.longEdge }
            ?.longEdge
            ?: deduped.first().longEdge

        return deduped.map { c ->
            val (w, h) = targetSizeFor(c.longEdge, source)
            CompressPreset(
                label = c.label,
                targetLongEdgePx = c.longEdge,
                bitrateBps = suggestedBitrate(w, h),
                recommended = c.longEdge == recommendedLongEdge,
            )
        }
    }

    /**
     * Resolves a preset's long edge into a full (width, height) at the source's
     * aspect ratio, so the bitrate heuristic reflects the real pixel count. Falls
     * back to a 16:9 landscape frame when the source ratio is unknown.
     */
    private fun targetSizeFor(targetLongEdge: Int, source: MediaDimensions?): Pair<Int, Int> {
        if (source == null || source.longEdge <= 0 || source.shortEdge <= 0) {
            // Assume 16:9 landscape.
            val h = (targetLongEdge * 9 / 16).coerceAtLeast(1)
            return targetLongEdge to h
        }
        val scale = targetLongEdge.toDouble() / source.longEdge.toDouble()
        val scaledShort = (source.shortEdge * scale).roundToInt().coerceAtLeast(1)
        // Preserve the source orientation in the reported pair.
        return if (source.width >= source.height) {
            targetLongEdge to scaledShort
        } else {
            scaledShort to targetLongEdge
        }
    }

    private fun suggestedBitrate(width: Int, height: Int): Int {
        val bits = width.toLong() * height.toLong() * FRAMERATE * BITS_PER_PIXEL
        // Clamp to a sane floor so tiny frames still get a usable bitrate.
        return bits.roundToInt().coerceAtLeast(MIN_BITRATE_BPS)
    }

    private companion object {
        const val FRAMERATE = 30.0
        const val BITS_PER_PIXEL = 0.07
        const val MIN_BITRATE_BPS = 250_000
    }
}
