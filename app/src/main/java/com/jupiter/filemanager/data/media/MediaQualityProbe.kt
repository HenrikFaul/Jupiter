package com.jupiter.filemanager.data.media

import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.MediaQuality
import com.jupiter.filemanager.domain.model.QualityKind
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Probes media files for quality metrics (resolution, bitrate, duration).
 *
 * Every JNI / decode call is individually guarded so this can be pointed at
 * arbitrary, possibly-corrupt files without ever throwing. Worst case it falls
 * back to a plain [MediaQuality] carrying only the on-disk size.
 *
 * All work is dispatched off the main thread via [IoDispatcher].
 */
@Singleton
class MediaQualityProbe @Inject constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun probe(path: String): MediaQuality = withContext(dispatcher) {
        val sizeBytes = safeSize(path)
        when (kindForPath(path)) {
            QualityKind.IMAGE -> probeImage(path, sizeBytes)
            QualityKind.VIDEO -> probeVideo(path, sizeBytes)
            QualityKind.AUDIO -> probeAudio(path, sizeBytes)
            QualityKind.OTHER -> fallback(sizeBytes)
        }
    }

    suspend fun probeAll(paths: List<String>): Map<String, MediaQuality> =
        withContext(dispatcher) {
            paths.associateWith { path -> probe(path) }
        }

    // ---------------------------------------------------------------------
    // IMAGE
    // ---------------------------------------------------------------------

    private fun probeImage(path: String, sizeBytes: Long): MediaQuality {
        var width = 0
        var height = 0
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth > 0) width = options.outWidth
            if (options.outHeight > 0) height = options.outHeight
        } catch (_: Throwable) {
            // ignore — keep zeros
        }

        // Swap dimensions for rotated orientations so the reported resolution
        // matches what the user actually sees.
        try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            if (
                orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            ) {
                val tmp = width
                width = height
                height = tmp
            }
        } catch (_: Throwable) {
            // ignore — orientation is best-effort
        }

        if (width <= 0 || height <= 0) {
            return MediaQuality(
                kind = QualityKind.IMAGE,
                sizeBytes = sizeBytes,
                label = "",
                score = sizeBytes,
            )
        }

        val megaPixels = (width.toLong() * height.toLong()) / 1_000_000.0
        val label = "$width×$height · ${formatMegaPixels(megaPixels)} MP"
        return MediaQuality(
            kind = QualityKind.IMAGE,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            label = label,
            score = width.toLong() * height.toLong(),
        )
    }

    // ---------------------------------------------------------------------
    // VIDEO
    // ---------------------------------------------------------------------

    private fun probeVideo(path: String, sizeBytes: Long): MediaQuality {
        var width = 0
        var height = 0
        var bitrate = 0L
        var durationMs = 0L

        withRetriever(path) { retriever ->
            width = readInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            height = readInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            bitrate = readLong(retriever, MediaMetadataRetriever.METADATA_KEY_BITRATE)
            durationMs = readLong(retriever, MediaMetadataRetriever.METADATA_KEY_DURATION)
        }

        if (width <= 0 && height <= 0 && bitrate <= 0L) {
            return MediaQuality(
                kind = QualityKind.VIDEO,
                sizeBytes = sizeBytes,
                label = "",
                score = sizeBytes,
            )
        }

        val resolutionLabel = resolutionLabel(height, width)
        val bitrateLabel = if (bitrate > 0L) "${formatMbps(bitrate)} Mbps" else ""
        val label = listOf(resolutionLabel, bitrateLabel)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")

        // Resolution dominates; bitrate is a tie-breaker weight (in pixels/sec
        // it would dwarf pixel counts, so scale it down before adding).
        val pixels = width.toLong() * height.toLong()
        val bitrateWeight = bitrate / 1_000L
        return MediaQuality(
            kind = QualityKind.VIDEO,
            width = width,
            height = height,
            bitrate = bitrate,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            label = label,
            score = pixels + bitrateWeight,
        )
    }

    // ---------------------------------------------------------------------
    // AUDIO
    // ---------------------------------------------------------------------

    private fun probeAudio(path: String, sizeBytes: Long): MediaQuality {
        var bitrate = 0L
        var durationMs = 0L

        withRetriever(path) { retriever ->
            bitrate = readLong(retriever, MediaMetadataRetriever.METADATA_KEY_BITRATE)
            durationMs = readLong(retriever, MediaMetadataRetriever.METADATA_KEY_DURATION)
        }

        if (bitrate <= 0L) {
            return MediaQuality(
                kind = QualityKind.AUDIO,
                durationMs = durationMs,
                sizeBytes = sizeBytes,
                label = "",
                score = sizeBytes,
            )
        }

        val kbps = bitrate / 1_000L
        return MediaQuality(
            kind = QualityKind.AUDIO,
            bitrate = bitrate,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            label = "$kbps kbps",
            score = bitrate,
        )
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun fallback(sizeBytes: Long): MediaQuality =
        MediaQuality(
            kind = QualityKind.OTHER,
            sizeBytes = sizeBytes,
            label = "",
            score = sizeBytes,
        )

    private fun safeSize(path: String): Long =
        try {
            File(path).length()
        } catch (_: Throwable) {
            0L
        }

    private inline fun withRetriever(path: String, block: (MediaMetadataRetriever) -> Unit) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            block(retriever)
        } catch (_: Throwable) {
            // ignore — leave caller's values at defaults
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun readInt(retriever: MediaMetadataRetriever, key: Int): Int =
        try {
            retriever.extractMetadata(key)?.trim()?.toIntOrNull() ?: 0
        } catch (_: Throwable) {
            0
        }

    private fun readLong(retriever: MediaMetadataRetriever, key: Int): Long =
        try {
            retriever.extractMetadata(key)?.trim()?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        }

    private fun kindForPath(path: String): QualityKind {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "tiff", "tif" ->
                QualityKind.IMAGE
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "3g2", "m4v", "flv", "wmv", "ts", "mpeg", "mpg" ->
                QualityKind.VIDEO
            "mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "wma", "amr", "mid", "midi" ->
                QualityKind.AUDIO
            else -> QualityKind.OTHER
        }
    }

    /** Map a video height to a friendly label like "1080p"; fall back to "WxH". */
    private fun resolutionLabel(height: Int, width: Int): String {
        if (height <= 0 && width <= 0) return ""
        val p = when {
            height >= 4320 -> "4320p"
            height >= 2160 -> "2160p"
            height >= 1440 -> "1440p"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            height >= 480 -> "480p"
            height >= 360 -> "360p"
            height >= 240 -> "240p"
            else -> ""
        }
        if (p.isNotEmpty()) return p
        if (width > 0 && height > 0) return "$width×$height"
        return ""
    }

    private fun formatMegaPixels(mp: Double): String =
        if (mp >= 10.0) {
            mp.toLong().toString()
        } else {
            // one decimal place without locale-sensitive String.format quirks
            val rounded = Math.round(mp * 10.0) / 10.0
            rounded.toString()
        }

    private fun formatMbps(bitrate: Long): String {
        val mbps = bitrate / 1_000_000.0
        val rounded = Math.round(mbps * 10.0) / 10.0
        return rounded.toString()
    }
}
