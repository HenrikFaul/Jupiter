package com.jupiter.filemanager.domain.model

/**
 * Discriminates the kind of media a [MediaQuality] describes so callers can
 * interpret its dimensions/bitrate fields and rank comparable items.
 */
enum class QualityKind { IMAGE, VIDEO, AUDIO, OTHER }

/**
 * A probed snapshot of a single media file's intrinsic quality.
 *
 * Produced by `data/media/MediaQualityProbe`. The [score] is a comparable
 * quality rank within the same [kind] (higher == better), and [label] is a
 * short human-readable summary, e.g. "4032×3024 · 12 MP", "1080p · 8.0 Mbps",
 * or "320 kbps".
 */
data class MediaQuality(
    val kind: QualityKind,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Long = 0L,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val label: String = "",
    val score: Long = 0L,
) {
    companion object {
        /** Neutral, empty quality used as a safe fallback when probing fails. */
        val EMPTY = MediaQuality(QualityKind.OTHER)
    }
}
