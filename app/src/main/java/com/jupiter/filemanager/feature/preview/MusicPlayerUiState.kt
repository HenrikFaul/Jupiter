package com.jupiter.filemanager.feature.preview

import com.jupiter.filemanager.domain.model.FileItem

/**
 * Immutable UI state for the audio [MusicPlayerScreen].
 *
 * The underlying [android.media.MediaPlayer] lives in [MusicPlayerViewModel]; this
 * state is a passive snapshot the screen renders from. Positions are expressed in
 * milliseconds to align with the framework player's clock.
 *
 * @property file the resolved [FileItem] for the audio track, or null before it loads
 *   (or when the path could not be resolved).
 * @property title a display title for the track (typically the file name).
 * @property isLoading whether the file metadata and player are still being prepared.
 * @property isPrepared whether the [android.media.MediaPlayer] has finished preparing
 *   and is ready to play/seek.
 * @property isPlaying whether playback is currently running.
 * @property positionMs the current playback position in milliseconds.
 * @property durationMs the total track duration in milliseconds, or 0 when unknown.
 * @property error a human-readable error message, or null when there is none.
 */
data class MusicPlayerUiState(
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
