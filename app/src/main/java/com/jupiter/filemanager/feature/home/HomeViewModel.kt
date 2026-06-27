package com.jupiter.filemanager.feature.home

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.CategoryUsage
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
 * Drives the redesigned NEXUS-style Home dashboard.
 *
 * Surfaces:
 *  - mounted storage volumes and the primary volume (storage overview card),
 *  - Quick Access folder shortcuts (Downloads / Documents / Images) backed by
 *    real well-known device directories and the categorized storage overview,
 *  - a categorized storage breakdown,
 *  - recently visited locations and user bookmarks (observed reactively).
 *
 * Bookmarks and recents are observed reactively so the dashboard stays in sync
 * as the user pins/visits locations elsewhere. Volume listing, the storage
 * overview, and Quick Access resolution are loaded on demand via [refresh],
 * which runs all blocking work inside repository suspend functions / coroutines
 * on background dispatchers.
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
     * Reloads storage volumes, the categorized storage overview and the Quick
     * Access shortcuts. Bookmarks and recents update independently via their own
     * observers and are not refetched here.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val volumes = fileRepository.storageVolumes()
            val primary = volumes.firstOrNull { it.isPrimary } ?: volumes.firstOrNull()

            when (val overview = analyticsRepository.storageOverview()) {
                is AppResult.Success -> {
                    val categories = overview.data.categories
                    _uiState.update {
                        it.copy(
                            volumes = volumes,
                            primaryVolume = primary,
                            categories = categories,
                            quickAccess = buildQuickAccess(categories),
                            isLoading = false,
                            error = null,
                        )
                    }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        volumes = volumes,
                        primaryVolume = primary,
                        categories = emptyList(),
                        quickAccess = buildQuickAccess(emptyList()),
                        isLoading = false,
                        error = overview.error.displayMessage,
                    )
                }
            }
        }
    }

    /**
     * Builds the Quick Access shortcuts from well-known external storage
     * directories, enriching each with the matching [CategoryUsage] aggregate
     * when one is available. Only directories that actually exist on the device
     * are surfaced, so the row reflects real, browsable folders.
     */
    private fun buildQuickAccess(categories: List<CategoryUsage>): List<QuickAccessShortcut> {
        fun usageFor(category: StorageCategory): CategoryUsage? =
            categories.firstOrNull { it.category == category }

        val specs = listOf(
            Triple("downloads", Environment.DIRECTORY_DOWNLOADS, StorageCategory.DOWNLOADS) to "Downloads",
            Triple("documents", Environment.DIRECTORY_DOCUMENTS, StorageCategory.DOCUMENTS) to "Documents",
            Triple("images", Environment.DIRECTORY_PICTURES, StorageCategory.IMAGES) to "Images",
        )

        return specs.mapNotNull { (spec, label) ->
            val (id, dirName, category) = spec
            val dir: File = try {
                Environment.getExternalStoragePublicDirectory(dirName)
            } catch (_: Throwable) {
                null
            } ?: return@mapNotNull null
            if (!dir.exists() || !dir.isDirectory) return@mapNotNull null

            val usage = usageFor(category)
            QuickAccessShortcut(
                id = id,
                label = label,
                path = dir.absolutePath,
                itemCount = usage?.fileCount,
                sizeBytes = usage?.sizeBytes,
            )
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
