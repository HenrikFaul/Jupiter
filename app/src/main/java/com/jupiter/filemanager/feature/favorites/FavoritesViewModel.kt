package com.jupiter.filemanager.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.getOrNull
import com.jupiter.filemanager.domain.model.Bookmark
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [FavoritesScreen]. Observes persisted bookmarks and resolves each one to
 * a concrete [com.jupiter.filemanager.domain.model.FileItem] so the UI can route
 * folders and files appropriately.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val fileRepository: FileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            bookmarkRepository.observeBookmarks()
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true) }
                .collect { bookmarks ->
                    val entries = bookmarks.map { it.toEntry() }
                    _uiState.value = FavoritesUiState(
                        isLoading = false,
                        entries = entries,
                    )
                }
        }
    }

    /** Removes the favorite pointing at [path]. */
    fun removeFavorite(path: String) {
        viewModelScope.launch {
            bookmarkRepository.removeBookmark(path)
        }
    }

    private suspend fun Bookmark.toEntry(): FavoriteEntry {
        val resolved = fileRepository.getFile(path).getOrNull()
        val isDirectory = resolved?.isDirectory ?: guessIsDirectory(path)
        return FavoriteEntry(
            bookmark = this,
            item = resolved,
            isDirectory = isDirectory,
        )
    }

    /**
     * Best-effort directory heuristic for paths that could not be resolved on
     * disk: a path with no file extension in its final segment is treated as a
     * folder.
     */
    private fun guessIsDirectory(path: String): Boolean {
        val name = path.trimEnd('/').substringAfterLast('/')
        return !name.contains('.')
    }
}
