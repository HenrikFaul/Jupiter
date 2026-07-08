package com.jupiter.filemanager.feature.version

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileVersion
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.VersionRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [VersionHistoryScreen].
 *
 * Resolves the file at the navigation argument [Destination.VersionHistory.ARG_PATH]
 * and observes its version history via [VersionRepository.versionsFor]. No real
 * versioning backend exists yet, so the observed list starts empty and the screen
 * shows an honest empty state. Invoking [restore] delegates to
 * [VersionRepository.restore], which reports the not-configured failure; that
 * message is surfaced in the UI rather than fabricating a successful restore.
 *
 * All file metadata resolution and version observation run on background
 * dispatchers inside the repositories; the composable stays pure UI.
 */
@HiltViewModel
class VersionHistoryViewModel @Inject constructor(
    private val versionRepository: VersionRepository,
    private val fileRepository: FileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Decoded file path from the navigation argument, or null when absent/blank. */
    private val path: String? = savedStateHandle
        .get<String>(Destination.VersionHistory.ARG_PATH)
        ?.takeIf { it.isNotBlank() }
        ?.let { android.net.Uri.decode(it) }

    /** Mutable overlay carrying transient flags the repository flows don't own. */
    private val overlay = MutableStateFlow(Overlay())

    private val versionsFlow: Flow<List<FileVersion>> =
        if (path == null) {
            flowOf(emptyList())
        } else {
            versionRepository.versionsFor(path)
                .catch { emit(emptyList()) }
        }

    val uiState: StateFlow<VersionHistoryUiState> =
        combine(versionsFlow, overlay) { versions, ov ->
            VersionHistoryUiState(
                file = ov.file,
                title = ov.title,
                versions = versions,
                isLoading = ov.isLoading,
                isRestoring = ov.isRestoring,
                error = ov.error,
                message = ov.message,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = VersionHistoryUiState(
                    title = "",
                    isLoading = path != null,
                    error = if (path == null) NO_FILE_MESSAGE else null,
                ),
            )

    init {
        if (path == null) {
            overlay.update {
                it.copy(isLoading = false, error = NO_FILE_MESSAGE)
            }
        } else {
            loadFile(path)
        }
    }

    /** Resolves the [FileItem] metadata for the title and detail context. */
    private fun loadFile(path: String) {
        overlay.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = fileRepository.getFile(path)) {
                is AppResult.Success -> overlay.update {
                    it.copy(
                        file = result.data,
                        title = result.data.name,
                        isLoading = false,
                        error = null,
                    )
                }

                is AppResult.Failure -> overlay.update {
                    it.copy(
                        isLoading = false,
                        error = result.error.displayMessage,
                    )
                }
            }
        }
    }

    /**
     * Restores the file to the version identified by [version].
     *
     * Delegates to [VersionRepository.restore]; until a versioning backend is
     * configured this returns [AppResult.Failure] and the message is surfaced
     * honestly via [VersionHistoryUiState.message].
     */
    fun restore(version: FileVersion) {
        if (overlay.value.isRestoring) return
        overlay.update { it.copy(isRestoring = true) }
        viewModelScope.launch {
            val result = versionRepository.restore(version.id)
            val message = when (result) {
                is AppResult.Success -> "Restored \"${version.label}\"."
                is AppResult.Failure -> result.error.displayMessage
            }
            overlay.update {
                it.copy(isRestoring = false, message = message)
            }
        }
    }

    /** Clears the one-shot [VersionHistoryUiState.message] after it is shown. */
    fun consumeMessage() {
        overlay.update { it.copy(message = null) }
    }

    /**
     * Backing overlay state combined with the repository's version flow to form
     * the public [VersionHistoryUiState].
     */
    private data class Overlay(
        val file: FileItem? = null,
        val title: String = "",
        val isLoading: Boolean = true,
        val isRestoring: Boolean = false,
        val error: String? = null,
        val message: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val NO_FILE_MESSAGE = "No file selected."
    }
}
