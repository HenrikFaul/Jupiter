package com.jupiter.filemanager.feature.albums

import com.jupiter.filemanager.data.media.Album
import com.jupiter.filemanager.domain.model.FileItem

/**
 * Immutable UI state for the gallery-style image [AlbumsScreen].
 *
 * The screen has two modes driven by [selectedAlbum]:
 *  - null   -> show the grid of [albums] (album covers).
 *  - non-null -> show [images] inside the selected album, with a back-to-albums
 *    affordance.
 *
 * All listings are produced off the main thread by
 * [com.jupiter.filemanager.data.media.AlbumsSource] (a `MediaStore` query), so this
 * state is purely presentational: the already-resolved data plus honest
 * loading/error flags.
 *
 * @param albums the device's image albums (one per `MediaStore` bucket).
 * @param selectedAlbum the album currently drilled into, or null at the albums grid.
 * @param images images inside [selectedAlbum]; empty at the albums grid.
 * @param isLoading true while a query is in flight and there is nothing to show yet.
 * @param error a user-facing error message, or null when there is no error.
 */
data class AlbumsUiState(
    val albums: List<Album> = emptyList(),
    val selectedAlbum: Album? = null,
    val images: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /** True when an album is open (showing its images) rather than the albums grid. */
    val isInAlbum: Boolean get() = selectedAlbum != null
}
