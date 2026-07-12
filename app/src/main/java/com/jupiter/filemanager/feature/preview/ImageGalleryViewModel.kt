package com.jupiter.filemanager.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SortDirection
import com.jupiter.filemanager.domain.model.SortField
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the [ImageGalleryScreen].
 *
 * Given the navigation argument [Destination.ImageGallery.ARG_PATH], this ViewModel
 * loads every image that lives in the same folder so the screen can present them in a
 * swipeable pager with a thumbnail strip. The originally-opened image is highlighted
 * via [ImageGalleryUiState.initialIndex] so the pager can start on it.
 *
 * All file IO is delegated to the injected [FileRepository] (which performs listing on a
 * background dispatcher); the composable itself remains pure UI.
 */
@HiltViewModel
class ImageGalleryViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageGalleryUiState())
    val uiState: StateFlow<ImageGalleryUiState> = _uiState.asStateFlow()
    private var loadGeneration: Long = 0L

    init {
        val rawArg = savedStateHandle.get<String>(Destination.ImageGallery.ARG_PATH)
        val path = rawArg
            ?.takeIf { it.isNotBlank() }
            ?.let { android.net.Uri.decode(it) }
        if (path == null) {
            _uiState.update {
                it.copy(isLoading = false, error = "No image to display.")
            }
        } else {
            load(path)
        }
    }

    /**
     * Replaces sibling discovery with an in-memory, already-filtered launch list.
     *
     * Photos uses this hand-off for slideshow launches so the exact visible filter
     * and sort order is retained without encoding a potentially huge path list into
     * the navigation route. The ViewModel owns the list after this call, which also
     * survives configuration changes. A process-recreated destination safely falls
     * back to the route seed and normal sibling discovery.
     */
    fun useLaunchImages(images: List<FileItem>, initialPath: String?) {
        val launchImages = images
            .asSequence()
            .filter { !it.isDirectory && it.type == FileType.IMAGE }
            .distinctBy { it.path }
            .toList()
        if (launchImages.isEmpty()) return

        // Invalidate a repository listing which may still be completing from init.
        loadGeneration += 1L
        val initialIndex = launchImages
            .indexOfFirst { it.path == initialPath }
            .coerceAtLeast(0)
        _uiState.update {
            it.copy(
                images = launchImages,
                initialIndex = initialIndex,
                isLoading = false,
                error = null,
            )
        }
    }

    /**
     * Resolves the folder containing [path] and lists its image siblings.
     *
     * Resolution is best-effort: if the opened path itself is a directory, its images
     * are listed directly; otherwise the parent directory is scanned and the result is
     * filtered down to [FileType.IMAGE] entries. The opened file is located within the
     * resulting list to seed [ImageGalleryUiState.initialIndex].
     */
    private fun load(path: String) {
        val generation = ++loadGeneration
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val target = when (val result = fileRepository.getFile(path)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> null
            }
            if (generation != loadGeneration) return@launch

            // The folder to scan: the file's parent, or the path itself when it's a folder.
            val folderPath = when {
                target != null && target.isDirectory -> target.path
                target?.parentPath != null -> target.parentPath
                else -> path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }
            }

            if (folderPath == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Couldn't locate this image's folder.")
                }
                return@launch
            }

            val sort = SortOption(field = SortField.NAME, direction = SortDirection.ASCENDING, foldersFirst = false)
            val filter = FilterOption(showHidden = false, typeFilter = FileType.IMAGE)

            when (val listing = fileRepository.listFiles(folderPath, sort, filter)) {
                is AppResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    val images = listing.data.filter { it.type == FileType.IMAGE && !it.isDirectory }
                    val initialIndex = images
                        .indexOfFirst { it.path == path }
                        .coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            images = images,
                            initialIndex = initialIndex,
                            isLoading = false,
                            error = null,
                        )
                    }
                }

                is AppResult.Failure -> if (generation == loadGeneration) {
                    _uiState.update {
                        it.copy(isLoading = false, error = listing.error.displayMessage)
                    }
                }
            }
        }
    }
}
