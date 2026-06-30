package com.jupiter.filemanager.feature.remote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.RemoteConnection
import com.jupiter.filemanager.domain.model.RemoteEntry
import com.jupiter.filemanager.domain.repository.ConnectionRepository
import com.jupiter.filemanager.domain.repository.RemoteAccessRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [RemoteBrowserScreen].
 *
 * Reads the target connection id and initial path from [SavedStateHandle]
 * (the navigation arguments). Lists the remote directory via
 * [RemoteAccessRepository.list]; tapping a directory reloads in-place with the
 * new path while tapping a file downloads it via [RemoteAccessRepository.download].
 *
 * All protocol I/O happens inside the repositories on background dispatchers and
 * is surfaced as [AppResult], so this view model never throws for network
 * failures: errors become [RemoteBrowserUiState.error] (for the listing) or a
 * one-shot [RemoteBrowserUiState.message] (for downloads).
 */
@HiltViewModel
class RemoteBrowserViewModel @Inject constructor(
    private val remoteAccessRepository: RemoteAccessRepository,
    private val connectionRepository: ConnectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** The stored connection id from the navigation argument (may be blank). */
    private val connectionId: String = savedStateHandle
        .get<String>(Destination.RemoteBrowser.ARG_CONNECTION)
        ?.let { android.net.Uri.decode(it) }
        .orEmpty()

    /** The initial path argument, decoded; blank means "use the protocol root". */
    private val initialPathArg: String = savedStateHandle
        .get<String>(Destination.RemoteBrowser.ARG_PATH)
        ?.let { android.net.Uri.decode(it) }
        .orEmpty()

    private val _uiState = MutableStateFlow(RemoteBrowserUiState(isLoading = true))
    val uiState: StateFlow<RemoteBrowserUiState> = _uiState.asStateFlow()

    /** Resolved connection, looked up once; null until resolved or if not found. */
    private var connection: RemoteConnection? = null

    init {
        if (connectionId.isBlank()) {
            _uiState.update {
                it.copy(isLoading = false, error = NO_CONNECTION_MESSAGE)
            }
        } else {
            bootstrap()
        }
    }

    /** Resolves the connection, derives the starting path and loads its listing. */
    private fun bootstrap() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val resolved = runCatching {
                connectionRepository.observeRemotes().first()
                    .firstOrNull { it.id == connectionId }
            }.getOrNull()

            if (resolved == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = NO_CONNECTION_MESSAGE)
                }
                return@launch
            }

            connection = resolved
            val startPath = initialPathArg.ifBlank {
                remoteAccessRepository.defaultRootPath(resolved.type)
            }
            _uiState.update { it.copy(title = resolved.displayName) }
            load(startPath)
        }
    }

    /** Loads the listing for [path], replacing the current contents. */
    private fun load(path: String) {
        _uiState.update {
            it.copy(path = path, isLoading = true, error = null)
        }
        viewModelScope.launch {
            when (val result = remoteAccessRepository.list(connectionId, path)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        path = path,
                        entries = result.data.sortedWith(ENTRY_ORDER),
                        isLoading = false,
                        error = null,
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        path = path,
                        entries = emptyList(),
                        isLoading = false,
                        error = result.error.displayMessage,
                    )
                }
            }
        }
    }

    /**
     * Opens [entry]: navigates into a directory (reloading the listing) or
     * downloads a file. No-op while a download is already in flight.
     */
    fun openEntry(entry: RemoteEntry) {
        if (entry.isDirectory) {
            load(entry.path)
        } else {
            downloadEntry(entry)
        }
    }

    /** Reloads the current directory (used by the error-state retry action). */
    fun retry() {
        load(_uiState.value.path)
    }

    /**
     * Navigates one level up from the current path, when not already at the
     * connection root.
     */
    fun navigateUp() {
        val state = _uiState.value
        if (state.isAtRoot) return
        load(parentOf(state.path))
    }

    /** Reloads the listing for an explicit [path] (used by breadcrumb taps). */
    fun navigateTo(path: String) {
        load(path)
    }

    /**
     * Downloads [entry] into local storage via [RemoteAccessRepository.download]
     * and reports the outcome through the one-shot [RemoteBrowserUiState.message].
     */
    private fun downloadEntry(entry: RemoteEntry) {
        if (_uiState.value.isDownloading) return
        _uiState.update {
            it.copy(isDownloading = true, downloadingPath = entry.path)
        }
        viewModelScope.launch {
            val result = remoteAccessRepository.download(
                connectionId = connectionId,
                remotePath = entry.path,
                fileName = entry.name,
            )
            val message = when (result) {
                is AppResult.Success -> "Saved \"" + entry.name + "\" to " + result.data
                is AppResult.Failure -> result.error.displayMessage
            }
            _uiState.update {
                it.copy(
                    isDownloading = false,
                    downloadingPath = null,
                    message = message,
                )
            }
        }
    }

    /** Clears the one-shot [RemoteBrowserUiState.message] once it has been shown. */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private companion object {
        const val NO_CONNECTION_MESSAGE = "Connection not found."

        /** Directories first, then case-insensitive name order. */
        val ENTRY_ORDER: Comparator<RemoteEntry> =
            compareByDescending<RemoteEntry> { it.isDirectory }
                .thenBy { it.name.lowercase() }

        /**
         * Computes the parent path of [path]. SMB/NAS share-relative paths use no
         * leading slash and an empty string denotes the share root; SFTP/FTP/WebDAV
         * paths are absolute and collapse to "/".
         */
        fun parentOf(path: String): String {
            val trimmed = path.trimEnd('/')
            val slash = trimmed.lastIndexOf('/')
            return if (slash <= 0) {
                // Going above the first segment: fall back to the protocol root.
                if (trimmed.startsWith("/")) "/" else ""
            } else {
                trimmed.substring(0, slash)
            }
        }
    }
}
