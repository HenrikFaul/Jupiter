package com.jupiter.filemanager.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolves and exposes details for a single file/folder identified by the
 * `path` argument carried in [savedStateHandle].
 *
 * All file IO is delegated to [FileRepository] (which already runs on a
 * background dispatcher); this ViewModel only orchestrates state on
 * [viewModelScope]. Favorite (bookmark) state is observed from
 * [BookmarkRepository] so the action row stays in sync.
 */
@HiltViewModel
class FileDetailsViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val bookmarkRepository: BookmarkRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileDetailsUiState())
    val uiState: StateFlow<FileDetailsUiState> = _uiState.asStateFlow()

    /** The decoded absolute path this screen describes, if any. */
    private val path: String? = savedStateHandle
        .get<String>(Destination.FileDetails.ARG_PATH)
        ?.takeIf { it.isNotBlank() }
        ?.let { android.net.Uri.decode(it) }

    init {
        load()
    }

    /** Loads (or reloads) the file at [path] and refreshes favorite state. */
    fun load() {
        val target = path
        if (target == null) {
            _uiState.value = FileDetailsUiState(
                isLoading = false,
                file = null,
                error = "No file was provided.",
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val result = fileRepository.getFile(target)) {
                is AppResult.Success -> {
                    val favorite = isBookmarked(result.data.path)
                    _uiState.value = FileDetailsUiState(
                        isLoading = false,
                        file = result.data,
                        isFavorite = favorite,
                        error = null,
                    )
                }

                is AppResult.Failure -> {
                    _uiState.value = FileDetailsUiState(
                        isLoading = false,
                        file = null,
                        error = "Couldn't load this file.",
                    )
                }
            }
        }
    }

    /**
     * Adds the current file to favorites (bookmarks) and reflects the change in
     * the UI state. No-op when the file failed to load.
     */
    fun addToFavorites() {
        val item = _uiState.value.file ?: return
        viewModelScope.launch {
            bookmarkRepository.addBookmark(path = item.path, label = item.name)
            _uiState.value = _uiState.value.copy(isFavorite = true)
        }
    }

    /** Removes the current file from favorites (bookmarks). */
    fun removeFromFavorites() {
        val item = _uiState.value.file ?: return
        viewModelScope.launch {
            bookmarkRepository.removeBookmark(item.path)
            _uiState.value = _uiState.value.copy(isFavorite = false)
        }
    }

    /** Toggles favorite state for the current file. */
    fun toggleFavorite() {
        if (_uiState.value.isFavorite) removeFromFavorites() else addToFavorites()
    }

    private suspend fun isBookmarked(filePath: String): Boolean {
        val bookmarks = bookmarkRepository.observeBookmarks().first()
        return bookmarks.any { it.path == filePath }
    }
}
