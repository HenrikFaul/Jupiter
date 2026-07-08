package com.jupiter.filemanager.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.media.Album
import com.jupiter.filemanager.data.media.AlbumsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [AlbumsScreen]: resolves gallery-style image albums (and, on drill-in,
 * the images inside one album) by delegating to [AlbumsSource] — a `MediaStore`
 * query rather than a recursive filesystem walk, so it stays fast.
 *
 * All IO happens inside the source on a background dispatcher; this ViewModel only
 * orchestrates state on [viewModelScope] and never crashes on a failed query
 * (the source returns an empty list, which surfaces as an honest empty state).
 */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val source: AlbumsSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumsUiState(isLoading = true))
    val uiState: StateFlow<AlbumsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Loads the album grid. Clears any drilled-in album selection. */
    fun load() {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            images = emptyList(),
            isLoading = true,
            error = null,
        )
        viewModelScope.launch {
            val albums = source.imageAlbums()
            _uiState.value = _uiState.value.copy(
                albums = albums,
                selectedAlbum = null,
                images = emptyList(),
                isLoading = false,
                error = null,
            )
        }
    }

    /** Drills into [album], loading the images it contains. */
    fun openAlbum(album: Album) {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = album,
            images = emptyList(),
            isLoading = true,
            error = null,
        )
        viewModelScope.launch {
            val images = source.imagesIn(album.bucketId)
            // Guard against a stale result if the user backed out meanwhile.
            if (_uiState.value.selectedAlbum?.bucketId != album.bucketId) return@launch
            _uiState.value = _uiState.value.copy(
                images = images,
                isLoading = false,
                error = null,
            )
        }
    }

    /** Returns from an open album back to the album grid. */
    fun backToAlbums() {
        if (_uiState.value.selectedAlbum == null) return
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            images = emptyList(),
            isLoading = false,
            error = null,
        )
    }

    /** Retries the current view (album grid or the open album's images). */
    fun retry() {
        val album = _uiState.value.selectedAlbum
        if (album != null) openAlbum(album) else load()
    }
}
