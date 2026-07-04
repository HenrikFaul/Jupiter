package com.jupiter.filemanager.feature.compress

import com.jupiter.filemanager.data.media.CompressPreset
import com.jupiter.filemanager.data.media.MediaDimensions
import com.jupiter.filemanager.domain.model.FileItem

/**
 * Outcome of a completed compression, carrying the before/after byte sizes and
 * the path of the produced file so the UI can render an "Original -> Compressed"
 * summary and offer open/share actions.
 *
 * @property originalBytes size of the source file in bytes.
 * @property compressedBytes size of the produced file in bytes.
 * @property outputPath absolute path of the compressed output file.
 */
data class CompressResult(
    val originalBytes: Long,
    val compressedBytes: Long,
    val outputPath: String,
) {
    /**
     * Bytes reclaimed by the compression. Never negative: if the output somehow
     * ended up larger than the source (rare, e.g. tiny already-optimised media),
     * the saving is reported as zero rather than a misleading negative number.
     */
    val savedBytes: Long get() = (originalBytes - compressedBytes).coerceAtLeast(0L)

    /**
     * Fraction of the original size that was saved, in the range 0f..1f. Returns
     * 0f when the original size is unknown/zero to avoid a divide-by-zero.
     */
    val savedFraction: Float
        get() = if (originalBytes <= 0L) 0f else savedBytes.toFloat() / originalBytes.toFloat()
}

/**
 * Immutable UI state for the Compress screen.
 *
 * The screen moves through three loose phases, all represented here so the view
 * stays a pure function of state:
 *  1. **Pick** — [availableMedia] is populated (largest first) and the user
 *     chooses a [sourceItem].
 *  2. **Configure** — once a source is picked, [sourceDims] and [presets] are
 *     computed and one preset is [selectedPreset].
 *  3. **Run / Result** — [isCompressing] with live [progress], resolving to a
 *     [result] or an [error].
 *
 * @property deviceLabel a human-facing device/screen line, e.g.
 *   "Your screen: 1080×2400 — recommended 1080p".
 * @property isLoadingMedia whether the compressible-media picker list is loading.
 * @property availableMedia compressible images + videos on the device, largest first.
 * @property sourceItem the media the user chose to compress, or null before a pick.
 * @property sourceDims decoded pixel dimensions of [sourceItem], or null when
 *   unknown/undecodable.
 * @property presets never-upscale compression presets for the current device +
 *   source pairing; exactly one is marked recommended when non-empty.
 * @property selectedPreset the preset the user (or the default recommendation)
 *   has chosen, or null before a source is picked.
 * @property estimates estimated compressed size (bytes) per preset, keyed by
 *   [CompressPreset.targetLongEdgePx]. A pure pre-compression heuristic; empty
 *   until estimates have been computed for the current source.
 * @property isCompressing whether a compression job is currently running.
 * @property progress 0..100 progress of the running compression (videos report
 *   real progress; images complete effectively instantly).
 * @property result the completed compression outcome, or null before completion.
 * @property error a human-readable error message, or null when there is none.
 */
data class CompressUiState(
    val deviceLabel: String = "",
    val isLoadingMedia: Boolean = false,
    val availableMedia: List<FileItem> = emptyList(),
    val sourceItem: FileItem? = null,
    val sourceDims: MediaDimensions? = null,
    val presets: List<CompressPreset> = emptyList(),
    val selectedPreset: CompressPreset? = null,
    val estimates: Map<Int, Long> = emptyMap(),
    val isCompressing: Boolean = false,
    val progress: Int = 0,
    val result: CompressResult? = null,
    val error: String? = null,
) {
    /** True when there is no media to compress and nothing is loading. */
    val isEmpty: Boolean get() = !isLoadingMedia && availableMedia.isEmpty() && sourceItem == null

    /** A compression can be started only with a source, a preset, and no job in flight. */
    val canCompress: Boolean
        get() = sourceItem != null && selectedPreset != null && !isCompressing
}
