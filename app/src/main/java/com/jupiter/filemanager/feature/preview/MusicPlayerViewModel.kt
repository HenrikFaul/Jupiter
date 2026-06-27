package com.jupiter.filemanager.feature.preview

import android.media.MediaPlayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Drives the audio [MusicPlayerScreen].
 *
 * Owns a single [MediaPlayer] for the track at the navigation argument
 * [Destination.MusicPlayer.ARG_PATH]. The player is created and prepared on the
 * injected [ioDispatcher] (preparation can block on file/codec setup) and released in
 * [onCleared]. While playing, a lightweight ticker mirrors the player's clock into
 * [MusicPlayerUiState.positionMs] so the UI can show a live progress bar without doing
 * any IO itself.
 *
 * The composable is pure UI: it forwards user intents (play/pause/seek) to this
 * ViewModel and renders the resulting state.
 */
@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    /** The framework player. Null until prepared, and after release. */
    private var player: MediaPlayer? = null

    /** Coroutine that polls the player position while playback is active. */
    private var tickerJob: Job? = null

    init {
        val rawArg = savedStateHandle.get<String>(Destination.MusicPlayer.ARG_PATH)
        val path = rawArg
            ?.takeIf { it.isNotBlank() }
            ?.let { android.net.Uri.decode(it) }
        if (path == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "No audio file to play.",
                )
            }
        } else {
            load(path)
        }
    }

    /**
     * Resolves the [FileItem] at [path] and prepares a [MediaPlayer] for it on the IO
     * dispatcher. On success the player is ready and the UI is unblocked; failures
     * surface an error in [MusicPlayerUiState.error].
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
                    _uiState.update { it.copy(file = item, title = item.name) }
                    prepare(item)
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

    /**
     * Builds and prepares a [MediaPlayer] for [item] on [ioDispatcher]. Any previously
     * held player is released first. Preparation is synchronous ([MediaPlayer.prepare])
     * and therefore runs off the main thread.
     */
    private suspend fun prepare(item: FileItem) {
        // Tear down any prior player before building a new one.
        releasePlayer()

        val outcome: AppResult<Pair<MediaPlayer, Int>> = withContext(ioDispatcher) {
            val file = File(item.path)
            if (!file.exists()) {
                return@withContext AppResult.Failure(
                    com.jupiter.filemanager.core.result.AppError.NotFound(item.path),
                )
            }
            if (!file.canRead()) {
                return@withContext AppResult.Failure(
                    com.jupiter.filemanager.core.result.AppError.AccessDenied(item.path),
                )
            }
            var mp: MediaPlayer? = null
            try {
                mp = MediaPlayer()
                mp.setDataSource(item.path)
                mp.prepare()
                AppResult.Success(mp to mp.duration.coerceAtLeast(0))
            } catch (e: IOException) {
                mp?.release()
                AppResult.Failure(
                    com.jupiter.filemanager.core.result.AppError.Io(
                        detail = e.message ?: "Couldn't open this audio file.",
                        cause = e,
                    ),
                )
            } catch (e: IllegalArgumentException) {
                mp?.release()
                AppResult.Failure(
                    com.jupiter.filemanager.core.result.AppError.Io(
                        detail = "This audio format isn't supported.",
                        cause = e,
                    ),
                )
            } catch (e: IllegalStateException) {
                mp?.release()
                AppResult.Failure(
                    com.jupiter.filemanager.core.result.AppError.Io(
                        detail = "Couldn't prepare playback.",
                        cause = e,
                    ),
                )
            } catch (e: SecurityException) {
                mp?.release()
                AppResult.Failure(
                    com.jupiter.filemanager.core.result.AppError.AccessDenied(item.path),
                )
            }
        }

        when (outcome) {
            is AppResult.Success -> {
                val (mp, duration) = outcome.data
                mp.setOnCompletionListener { onPlaybackCompleted() }
                player = mp
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPrepared = true,
                        isPlaying = false,
                        positionMs = 0,
                        durationMs = duration,
                        error = null,
                    )
                }
            }

            is AppResult.Failure -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isPrepared = false,
                    error = outcome.error.displayMessage,
                )
            }
        }
    }

    /** Toggles between play and pause based on the current state. */
    fun togglePlayPause() {
        if (_uiState.value.isPlaying) pause() else play()
    }

    /** Starts (or resumes) playback if a prepared player is available. */
    fun play() {
        val mp = player ?: return
        try {
            mp.start()
        } catch (_: IllegalStateException) {
            return
        }
        _uiState.update { it.copy(isPlaying = true) }
        startTicker()
    }

    /** Pauses playback, leaving the position intact for resume. */
    fun pause() {
        val mp = player ?: return
        try {
            if (mp.isPlaying) mp.pause()
        } catch (_: IllegalStateException) {
            // Player not in a pausable state; ignore.
        }
        stopTicker()
        _uiState.update { it.copy(isPlaying = false, positionMs = currentPositionMs()) }
    }

    /**
     * Seeks the player to [positionMs] (clamped to the track duration) and mirrors the
     * new position into the UI state immediately.
     */
    fun seekTo(positionMs: Int) {
        val mp = player ?: return
        val duration = _uiState.value.durationMs
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        try {
            mp.seekTo(target)
        } catch (_: IllegalStateException) {
            return
        }
        _uiState.update { it.copy(positionMs = target) }
    }

    /** Invoked by the [MediaPlayer.OnCompletionListener] when the track ends. */
    private fun onPlaybackCompleted() {
        stopTicker()
        _uiState.update {
            it.copy(
                isPlaying = false,
                positionMs = it.durationMs,
            )
        }
    }

    /** Starts the polling coroutine that mirrors the player clock into the UI state. */
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isPlaying) {
                _uiState.update { it.copy(positionMs = currentPositionMs()) }
                delay(POSITION_POLL_MS)
            }
        }
    }

    /** Cancels the position-polling coroutine if running. */
    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** Reads the player's current position defensively, returning 0 on any error. */
    private fun currentPositionMs(): Int = try {
        player?.currentPosition ?: 0
    } catch (_: IllegalStateException) {
        0
    }

    /** Releases the held [MediaPlayer] and stops the ticker. Safe to call repeatedly. */
    private fun releasePlayer() {
        stopTicker()
        player?.let { mp ->
            try {
                mp.setOnCompletionListener(null)
                mp.release()
            } catch (_: IllegalStateException) {
                // Already released; ignore.
            }
        }
        player = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }

    private companion object {
        /** Interval (ms) between position polls while playing. */
        const val POSITION_POLL_MS: Long = 500L
    }
}
