package com.jupiter.filemanager.feature.preview

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.LoadingView
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Full-screen video player for the file at the navigation argument
 * [com.jupiter.filemanager.ui.navigation.Destination.VideoPlayer.ARG_PATH].
 *
 * The rendering surface is a framework [VideoView] embedded via [AndroidView]; it must
 * live in the view hierarchy to draw frames, so the composable owns it while
 * [VideoPlayerViewModel] owns playback intent and metadata. The two are kept in sync:
 *  - User intents (play/pause/seek) update the ViewModel, and a [LaunchedEffect] applies
 *    the resulting state to the live [VideoView].
 *  - The [VideoView]'s own callbacks (prepared / completed / error) and a position ticker
 *    feed back into the ViewModel via [VideoPlayerViewModel.onPrepared] and friends.
 *
 * Lifecycle is handled cleanly: playback is paused when the host stops, the surface is
 * re-seeked to the last known position when the host returns to the foreground so a frame
 * re-renders, and the underlying [VideoView]/MediaPlayer is released via the
 * [AndroidView] `onRelease` callback on disposal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
) {
    val viewModel: VideoPlayerViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // The live VideoView is created once and shared between the AndroidView factory and
    // the control effects below.
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    // While the user is scrubbing, the slider should reflect the seek target rather than
    // the live player clock. isSeeking stays true until the player's reported position
    // converges to within SEEK_CONVERGE_TOLERANCE_MS of the target; until then the
    // position ticker is suppressed so it cannot snap the slider back.
    var isSeeking by remember { mutableStateOf(false) }
    var seekTargetMs by remember { mutableStateOf(0) }

    // Apply play/pause intent from state onto the live VideoView.
    LaunchedEffect(state.isPlaying, state.isPrepared, videoView) {
        val view = videoView ?: return@LaunchedEffect
        if (!state.isPrepared) return@LaunchedEffect
        if (state.isPlaying) {
            if (!view.isPlaying) view.start()
        } else {
            if (view.isPlaying) view.pause()
        }
    }

    // While playing, mirror the live VideoView clock back into the ViewModel so the
    // scrubber stays in sync without the ViewModel ever touching the surface.
    LaunchedEffect(state.isPlaying, state.isPrepared, videoView) {
        if (!state.isPlaying || !state.isPrepared) return@LaunchedEffect
        val view = videoView ?: return@LaunchedEffect
        while (true) {
            if (view.isPlaying) {
                val position = view.currentPosition
                if (isSeeking) {
                    // Suppress ticker-driven updates until the player has actually moved
                    // to (close to) the user's seek target, so an in-flight seek is not
                    // snapped back by a stale clock reading. Once converged, resume normal
                    // mirroring of the live player clock.
                    if (kotlin.math.abs(position - seekTargetMs) <= SEEK_CONVERGE_TOLERANCE_MS) {
                        isSeeking = false
                        viewModel.onPositionChanged(position)
                    }
                } else {
                    viewModel.onPositionChanged(position)
                }
            }
            delay(POSITION_POLL_MS)
        }
    }

    // Pause playback when the host lifecycle stops. When the host returns to the
    // foreground, Android has torn down the SurfaceView's surface, so re-seek the live
    // VideoView to the last known position; this re-binds the surface and renders a frame
    // instead of leaving a black stage. The VideoView itself is released in the
    // AndroidView onRelease callback on disposal.
    val currentState by rememberUpdatedState(state)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.pause()
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    val view = videoView
                    if (view != null && currentState.isPrepared) {
                        // Re-seek to re-bind the recreated surface so a frame renders.
                        view.seekTo(currentState.positionMs)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title.ifBlank { "Video" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> LoadingView(modifier = Modifier.fillMaxSize())

                state.error != null && state.file == null -> ErrorView(
                    message = state.error ?: "Unable to play this video.",
                    modifier = Modifier.fillMaxSize(),
                )

                state.file == null -> ErrorView(
                    message = state.error ?: "Unable to play this video.",
                    modifier = Modifier.fillMaxSize(),
                )

                else -> VideoContent(
                    state = state,
                    // While seeking, the slider/time should track the user's target rather
                    // than the (still-converging) live player clock.
                    displayPositionMs = if (isSeeking) seekTargetMs else state.positionMs,
                    onSurfaceReady = { view -> videoView = view },
                    onSurfaceReleased = { videoView = null },
                    onPrepared = viewModel::onPrepared,
                    onCompleted = viewModel::onCompleted,
                    onError = viewModel::onError,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSeek = { positionMs ->
                        val duration = state.durationMs
                        val target = if (duration > 0) {
                            positionMs.coerceIn(0, duration)
                        } else {
                            positionMs.coerceAtLeast(0)
                        }
                        isSeeking = true
                        seekTargetMs = target
                        videoView?.seekTo(target)
                        viewModel.seekTo(target)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun VideoContent(
    state: VideoPlayerUiState,
    displayPositionMs: Int,
    onSurfaceReady: (VideoView) -> Unit,
    onSurfaceReleased: () -> Unit,
    onPrepared: (Int) -> Unit,
    onCompleted: () -> Unit,
    onError: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val path = state.file?.path

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (path != null) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setOnPreparedListener { mp ->
                                // Looping is disabled; honor a single linear playback.
                                mp.isLooping = false
                                onPrepared(duration.coerceAtLeast(0))
                            }
                            setOnCompletionListener { onCompleted() }
                            setOnErrorListener { _, _, _ ->
                                onError("This video couldn't be played.")
                                true
                            }
                            setVideoURI(Uri.fromFile(File(path)))
                            onSurfaceReady(this)
                        }
                    },
                    onRelease = { view ->
                        // Release the VideoView's native MediaPlayer/decoder/surface so
                        // repeatedly opening videos does not leak codecs from the limited
                        // MediaCodec pool.
                        view.stopPlayback()
                        onSurfaceReleased()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Surface a non-fatal playback error as an overlay while keeping the surface.
            if (state.error != null && state.isPrepared.not() && state.isLoading.not()) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        PlaybackControls(
            state = state,
            displayPositionMs = displayPositionMs,
            onTogglePlayPause = onTogglePlayPause,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlaybackControls(
    state: VideoPlayerUiState,
    displayPositionMs: Int,
    onTogglePlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Slider(
            value = displayPositionMs.toFloat(),
            onValueChange = { value -> onSeek(value.toInt()) },
            valueRange = 0f..(state.durationMs.takeIf { it > 0 } ?: 1).toFloat(),
            enabled = state.isPrepared && state.durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPlaybackTime(displayPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatPlaybackTime(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onSeek((displayPositionMs - SKIP_MS).coerceAtLeast(0)) },
                enabled = state.isPrepared,
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Rewind 10 seconds",
                )
            }

            FilledIconButton(
                onClick = onTogglePlayPause,
                enabled = state.isPrepared,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp),
                )
            }

            IconButton(
                onClick = {
                    val max = state.durationMs
                    val target = displayPositionMs + SKIP_MS
                    onSeek(if (max > 0) target.coerceAtMost(max) else target)
                },
                enabled = state.isPrepared,
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "Forward 10 seconds",
                )
            }
        }
    }
}

/** Formats a millisecond position as `m:ss` (or `h:mm:ss` for hour-plus durations). */
private fun formatPlaybackTime(positionMs: Int): String {
    val safe = positionMs.coerceAtLeast(0).toLong()
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(safe)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Interval (ms) between live position polls while playing. */
private const val POSITION_POLL_MS: Long = 500L

/**
 * Tolerance (ms) within which the live player clock is considered to have converged to a
 * pending seek target. Once the reported position is this close to the target, ticker-driven
 * position updates resume. Sized comfortably above one poll interval of playback drift so the
 * seek reliably clears even when the player lands a little short of or past the exact target.
 */
private const val SEEK_CONVERGE_TOLERANCE_MS: Int = 750

/** Step (ms) used by the rewind/forward controls. */
private const val SKIP_MS: Int = 10_000
