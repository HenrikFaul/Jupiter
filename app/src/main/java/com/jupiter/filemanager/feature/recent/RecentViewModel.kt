package com.jupiter.filemanager.feature.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SortDirection
import com.jupiter.filemanager.domain.model.SortField
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.repository.ActivityRepository
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Recent tab: surfaces recently modified files across primary storage
 * (capped) plus the recorded activity feed.
 *
 * The repository performs all blocking IO on a background dispatcher; this
 * ViewModel only orchestrates state on [viewModelScope]. Recent files are derived
 * by walking the user-visible storage tree once and keeping the most recently
 * modified entries — there is no fake/seeded data.
 */
@HiltViewModel
class RecentViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val activityRepository: ActivityRepository,
    private val bookmarkRepository: BookmarkRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentUiState())
    val uiState: StateFlow<RecentUiState> = _uiState.asStateFlow()

    init {
        observeActivity()
        loadRecentFiles()
    }

    /** Re-scans storage for recently modified files. */
    fun refresh() {
        loadRecentFiles()
    }

    /** Dismisses any currently displayed error message. */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ---- internals -------------------------------------------------------

    private fun observeActivity() {
        activityRepository.observeActivity()
            .onEach { entries ->
                _uiState.value = _uiState.value.copy(activity = entries)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Lists files recently modified across primary storage. Uses [FileRepository.search]
     * to recursively enumerate the storage tree, then keeps the [RECENT_LIMIT]
     * most recently modified non-directory entries.
     */
    private fun loadRecentFiles() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            // Seed from recently visited folders first so the list is meaningful
            // even before the (potentially slow) full scan completes.
            val seeded = loadFromRecentFolders()
            if (seeded.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(recentFiles = seeded)
            }
            scanStorage()
        }
    }

    /** Resolves files from the most recently visited folders as a fast first pass. */
    private suspend fun loadFromRecentFolders(): List<FileItem> {
        val recents = runCatching {
            bookmarkRepository.observeRecents().firstOrNull() ?: emptyList()
        }.getOrDefault(emptyList())

        val collected = mutableListOf<FileItem>()
        for (path in recents.take(MAX_RECENT_FOLDERS)) {
            when (val result = fileRepository.listFiles(path, MODIFIED_SORT, NO_FILTER)) {
                is AppResult.Success -> collected += result.data.filter { !it.isDirectory }
                is AppResult.Failure -> Unit
            }
        }
        return collected
            .distinctBy { it.path }
            .sortedByDescending { it.lastModified }
            .take(RECENT_LIMIT)
    }

    /** Performs the recursive scan of primary storage for recently modified files. */
    private fun scanStorage() {
        val root = fileRepository.rootDirectory()
        val collected = sortedSetOf(
            compareByDescending<FileItem> { it.lastModified }.thenBy { it.path },
        )
        var emitted = 0

        fileRepository.search(root, NO_FILTER)
            .onEach { item ->
                if (item.isDirectory) return@onEach
                collected.add(item)
                // Keep only the freshest RECENT_LIMIT entries to bound memory.
                while (collected.size > RECENT_LIMIT) {
                    collected.remove(collected.last())
                }
                // Throttle UI updates to avoid churning state on every emission.
                emitted++
                if (emitted % UPDATE_BATCH == 0) {
                    _uiState.value = _uiState.value.copy(recentFiles = collected.toList())
                }
            }
            .launchIn(viewModelScope)
            .invokeOnCompletion { cause ->
                val current = _uiState.value
                _uiState.value = if (cause != null && current.recentFiles.isEmpty()) {
                    // Only surface an error when we have nothing to show.
                    current.copy(
                        isLoading = false,
                        error = "Couldn't scan storage for recent files.",
                    )
                } else {
                    current.copy(
                        recentFiles = collected.toList(),
                        isLoading = false,
                    )
                }
            }
    }

    private companion object {
        const val RECENT_LIMIT = 60
        const val MAX_RECENT_FOLDERS = 8
        const val UPDATE_BATCH = 25

        val MODIFIED_SORT = SortOption(
            field = SortField.DATE_MODIFIED,
            direction = SortDirection.DESCENDING,
            foldersFirst = false,
        )
        val NO_FILTER = FilterOption()
    }
}
