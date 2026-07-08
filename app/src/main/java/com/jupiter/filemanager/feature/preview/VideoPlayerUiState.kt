package com.jupiter.filemanager.feature.preview

import com.jupiter.filemanager.domain.model.FileItem

/**
 * Immutable UI state for the [VideoPlayerScreen].
 *
 * The actual rendering surface (an [android.widget.VideoView]) is owned by the
 * composable via [androidx.compose.ui.viewinterop.AndroidView]; this state is a passive
 * snapshot the screen renders from. The ViewModel resolves the file, validates it, and
 * mirrors the player's clock into [positionMs] while playing so the UI can show a live
 * scrubber without performing any IO itself.
 *
 * Positions and durations are expressed in milliseconds to align with the framework
 * player's clock.
 *
 * @property file the resolved [FileItem] for the video, or null before it loads (or when
 *   the navigation path could not be resolved).
 * @property title a display title for the video (typically the file name).
 * @property isLoading whether the file metadata is still being resolved.
 * @property isPrepared whether the underlying media player has finished preparing and is
 *   ready to play/seek.
 * @property isPlaying whether playback is currently running.
 * @property positionMs the current playback position in milliseconds.
 * @property durationMs the total video duration in milliseconds, or 0 when unknown.
 * @property error a human-readable error message, or null when there is none.
 */
data class VideoPlayerUiState(
    val file: FileItem? = null,
    val title: String = "",
    val isLoading: Boolean = true,
    val isPrepared: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val error: String? = null,
) {
    /** Playback progress in the range 0f..1f, or 0f when the duration is unknown. */
    val fraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
