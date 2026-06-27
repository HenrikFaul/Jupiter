package com.jupiter.filemanager.feature.preview

import com.jupiter.filemanager.domain.model.FileItem

/**
 * Immutable UI state for the [ImageGalleryScreen].
 *
 * The gallery presents every image that lives in the same folder as the file passed
 * via navigation, paged horizontally. [images] is the ordered list of sibling images
 * (including the initial one), and [initialIndex] points at the image the user opened
 * so the pager can start there.
 *
 * @property images the sibling images in the folder, in listing order.
 * @property initialIndex the index of the originally-opened image within [images].
 * @property isLoading whether the folder is still being scanned for images.
 * @property error a human-readable error message, or null when there is none.
 */
data class ImageGalleryUiState(
    val images: List<FileItem> = emptyList(),
    val initialIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /** True when there are no images to display and loading has finished. */
    val isEmpty: Boolean
        get() = !isLoading && error == null && images.isEmpty()
}
