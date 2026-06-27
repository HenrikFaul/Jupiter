package com.jupiter.filemanager.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.Bookmark
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Home screen.
 *
 * @property volumes the mounted storage volumes (internal storage, SD cards, etc.).
 * @property categories per-category storage usage from the storage overview.
 * @property recents recently visited locations, resolved to [FileItem]s.
 * @property bookmarks user-saved bookmarks.
 * @property isLoading whether a refresh is currently in progress.
 * @property error a human-readable error message, or null when there is none.
 */
data class HomeUiState(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val categories: List<CategoryUsage> = emptyList(),
    val recents: List<FileItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Drives the Home screen: surfaces storage volumes, a categorized storage
 * overview, recently visited locations, and user bookmarks.
 *
 * Bookmarks and recents are observed reactively so the home screen stays in sync
 * as the user pins/visits locations elsewhere. Volume listing and the (more
 * expensive) storage overview are loaded on demand via [refresh], which runs all
 * blocking work inside repository suspend functions on background dispatchers.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val analyticsRepository: StorageAnalyticsRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val settings: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeBookmarks()
        observeRecents()
        refresh()
    }

    /**
     * Reloads storage volumes and the categorized storage overview. Bookmarks and
     * recents update independently via their own observers and are not refetched
     * here.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val volumes = fileRepository.storageVolumes()

            when (val overview = analyticsRepository.storageOverview()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        volumes = volumes,
                        categories = overview.data.categories,
                        isLoading = false,
                        error = null,
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        volumes = volumes,
                        categories = emptyList(),
                        isLoading = false,
                        error = overview.error.displayMessage,
                    )
                }
            }
        }
    }

    /**
     * Observes saved bookmarks and mirrors them into the UI state as they change.
     */
    private fun observeBookmarks() {
        bookmarkRepository.observeBookmarks()
            .onEach { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Observes recently visited paths, resolving each to a [FileItem] via
     * [FileRepository.getFile]. Paths that can no longer be resolved (deleted or
     * inaccessible) are silently dropped so stale recents do not appear.
     */
    private fun observeRecents() {
        bookmarkRepository.observeRecents()
            .onEach { paths ->
                val items = resolveRecents(paths)
                _uiState.update { it.copy(recents = items) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Resolves [paths] to [FileItem]s concurrently, preserving the original
     * (most-recent-first) ordering and discarding any that fail to resolve.
     */
    private suspend fun resolveRecents(paths: List<String>): List<FileItem> = coroutineScope {
        paths
            .map { path -> async { fileRepository.getFile(path) } }
            .awaitAll()
            .mapNotNull { result ->
                when (result) {
                    is AppResult.Success -> result.data
                    is AppResult.Failure -> null
                }
            }
    }
}
