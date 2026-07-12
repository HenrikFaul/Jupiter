package com.jupiter.filemanager.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.ui.components.ErrorView
import com.jupiter.filemanager.ui.components.JupiterCard
import com.jupiter.filemanager.ui.components.JupiterIconBadge
import com.jupiter.filemanager.ui.components.LoadingView
import java.util.Locale

/**
 * Full-screen audio player.
 *
 * Pure UI: all playback is owned by [MusicPlayerViewModel] (which holds the framework
 * [android.media.MediaPlayer]). This composable renders the current track, a draggable
 * progress slider, and transport controls (skip back 10s / play-pause / skip forward
 * 10s), forwarding intents to the ViewModel.
 *
 * Visual language matches Jupiter's midnight-and-teal surfaces and compact hierarchy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    onBack: () -> Unit,
) {
    val viewModel: MusicPlayerViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Now Playing",
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

                state.error != null && !state.isPrepared -> ErrorView(
                    message = state.error ?: "Couldn't play this audio file.",
                    modifier = Modifier.fillMaxSize(),
                )

                else -> PlayerBody(
                    state = state,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSeekTo = viewModel::seekTo,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PlayerBody(
    state: MusicPlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Honest artwork placeholder: no fabricated album art is rendered.
        JupiterCard(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .padding(top = 16.dp),
            contentPadding = PaddingValues(vertical = 40.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                JupiterIconBadge(icon = Icons.Default.MusicNote, size = 96.dp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = state.title.ifBlank { state.file?.name ?: "Audio" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        // Subtitle: prefer the track artist; fall back to
        // the file size when no artist tag is present.
        val sizeText = state.file?.let { formatBytes(it.sizeBytes) }
        val subtitle = state.artist.ifBlank { sizeText.orEmpty() }
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Seek bar. Slider value is the playback position in milliseconds.
        val duration = state.durationMs.coerceAtLeast(0)
        Slider(
            value = state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
            onValueChange = { value -> onSeekTo(value.toInt()) },
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
            enabled = state.isPrepared && duration > 0,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(state.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Transport controls.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onSeekTo((state.positionMs - SKIP_MS).coerceAtLeast(0)) },
                enabled = state.isPrepared,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            FilledIconButton(
                onClick = onTogglePlayPause,
                enabled = state.isPrepared,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(76.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            IconButton(
                onClick = {
                    val max = state.durationMs.coerceAtLeast(0)
                    onSeekTo((state.positionMs + SKIP_MS).let { if (max > 0) it.coerceAtMost(max) else it })
                },
                enabled = state.isPrepared,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "Forward 10 seconds",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/** Number of milliseconds skipped by the rewind / forward controls. */
private const val SKIP_MS: Int = 10_000

/**
 * Formats a millisecond position as `m:ss` (or `h:mm:ss` for tracks an hour or longer).
 * Negative values are clamped to zero.
 */
private fun formatTime(millis: Int): String {
    val totalSeconds = (millis.coerceAtLeast(0)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
