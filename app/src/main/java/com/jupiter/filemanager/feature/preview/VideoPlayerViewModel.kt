package com.jupiter.filemanager.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Drives the [VideoPlayerScreen].
 *
 * Unlike audio playback, the video rendering surface ([android.widget.VideoView]) is
 * owned by the composable and embedded via
 * [androidx.compose.ui.viewinterop.AndroidView], because it must be attached to the view
 * hierarchy to draw frames. This ViewModel therefore acts as the source of truth for
 * playback *intent* and metadata:
 *
 *  - It resolves and validates the [FileItem] at the navigation argument
 *    [Destination.VideoPlayer.ARG_PATH] on the injected [ioDispatcher].
 *  - It tracks user intent ([VideoPlayerUiState.isPlaying]) and the latest known clock
 *    ([VideoPlayerUiState.positionMs] / [VideoPlayerUiState.durationMs]), which the
 *    screen mirrors from the live [android.widget.VideoView].
 *
 * The composable forwards player callbacks (prepared / completed / error / position
 * ticks) back into this ViewModel via the [onPrepared], [onCompleted], [onError], and
 * [onPositionChanged] hooks so that all state lives in one place and survives
 * recomposition.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    init {
        val rawArg = savedStateHandle.get<String>(Destination.VideoPlayer.ARG_PATH)
        val path = rawArg
            ?.takeIf { it.isNotBlank() }
            ?.let { android.net.Uri.decode(it) }
        if (path == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "No video file to play.",
                )
            }
        } else {
            load(path)
        }
    }

    /**
     * Resolves the [FileItem] at [path] and validates that it exists and is readable.
     * Metadata resolution runs on [ioDispatcher]; on success the screen is unblocked and
     * can attach the rendering surface to begin preparation.
     */
    private fun load(path: String) {
        _uiState.update {
            it.copy(
                isLoading = true,
                isPrepared = false,
                isPlaying = false,
                positionMs = 0,
                durationMs = 0,
                error = null,
            )
        }
        viewModelScope.launch {
            when (val result = fileRepository.getFile(path)) {
                is AppResult.Success -> {
                    val item = result.data
                    val validation = validate(item)
                    when (validation) {
                        is AppResult.Success -> _uiState.update {
                            it.copy(
                                file = item,
                                title = item.name,
                                isLoading = false,
                                error = null,
                            )
                        }

                        is AppResult.Failure -> _uiState.update {
                            it.copy(
                                file = item,
                                title = item.name,
                                isLoading = false,
                                error = validation.error.displayMessage,
                            )
                        }
                    }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.error.displayMessage,
                    )
                }
            }
        }
    }

    /** Confirms the backing file for [item] exists and is readable, off the main thread. */
    private suspend fun validate(item: FileItem): AppResult<Unit> = withContext(ioDispatcher) {
        val file = File(item.path)
        when {
            !file.exists() -> AppResult.Failure(AppError.NotFound(item.path))
            !file.canRead() -> AppResult.Failure(AppError.AccessDenied(item.path))
            else -> AppResult.Success(Unit)
        }
    }

    /** Toggles between play and pause based on the current intent. */
    fun togglePlayPause() {
        if (!_uiState.value.isPrepared) return
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    /** Requests playback to start/resume. */
    fun play() {
        if (!_uiState.value.isPrepared) return
        _uiState.update { it.copy(isPlaying = true) }
    }

    /** Requests playback to pause, leaving the position intact for resume. */
    fun pause() {
        _uiState.update { it.copy(isPlaying = false) }
    }

    /**
     * Records a user seek to [positionMs] (clamped to the known duration). The screen
     * applies the seek to the live [android.widget.VideoView]; this mirrors it into state
     * immediately so the scrubber stays responsive.
     */
    fun seekTo(positionMs: Int) {
        val duration = _uiState.value.durationMs
        val target = if (duration > 0) {
            positionMs.coerceIn(0, duration)
        } else {
            positionMs.coerceAtLeast(0)
        }
        _uiState.update { it.copy(positionMs = target) }
    }

    /**
     * Invoked by the screen once the [android.widget.VideoView] has finished preparing.
     * Records the resolved [durationMs] and unblocks play/seek controls.
     */
    fun onPrepared(durationMs: Int) {
        _uiState.update {
            it.copy(
                isPrepared = true,
                isLoading = false,
                durationMs = durationMs.coerceAtLeast(0),
                error = null,
            )
        }
    }

    /**
     * Invoked by the screen on each position tick while playback is active. Mirrors the
     * live player clock into [VideoPlayerUiState.positionMs].
     */
    fun onPositionChanged(positionMs: Int) {
        val duration = _uiState.value.durationMs
        val clamped = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        _uiState.update { it.copy(positionMs = clamped) }
    }

    /** Invoked by the screen when the player reaches the end of the video. */
    fun onCompleted() {
        _uiState.update {
            it.copy(
                isPlaying = false,
                positionMs = it.durationMs,
            )
        }
    }

    /**
     * Invoked by the screen when the underlying player reports an error. Surfaces a
     * human-readable [message] and halts playback.
     */
    fun onError(message: String) {
        _uiState.update {
            it.copy(
                isPlaying = false,
                isPrepared = false,
                isLoading = false,
                error = message,
            )
        }
    }
}
