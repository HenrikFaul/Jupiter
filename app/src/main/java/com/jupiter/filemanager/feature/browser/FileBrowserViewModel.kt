package com.jupiter.filemanager.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Drives the file browser screen: directory navigation, selection, sorting and
 * filtering, and long-running copy/move/delete operations.
 *
 * All blocking IO is delegated to [FileRepository], whose members already run on
 * a background dispatcher; this ViewModel only orchestrates state on
 * [viewModelScope].
 */
@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val settings: SettingsDataStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    /** Tracks the active copy/move operation so it can be cancelled. */
    private var operationJob: Job? = null

    /** Schedules auto-dismissal of a terminal operation card after a short delay. */
    private var operationDismissJob: Job? = null

    init {
        val rawArg = savedStateHandle.get<String>(Destination.Browser.ARG_PATH)
        val decoded = rawArg?.takeIf { it.isNotBlank() }
            ?.let { android.net.Uri.decode(it) }
        val startPath = decoded ?: fileRepository.rootDirectory()

        // Load persisted sort preference, then open the initial directory.
        viewModelScope.launch {
            val persistedSort = settings.sortOption.first()
            val persistedShowHidden = settings.showHidden.first()
            _uiState.value = _uiState.value.copy(
                sortOption = persistedSort,
                filter = _uiState.value.filter.copy(showHidden = persistedShowHidden),
            )
            loadDirectory(startPath)
        }
    }

    /** Opens [path], replacing the current listing. */
    fun openDirectory(path: String) {
        clearSelection()
        viewModelScope.launch {
            loadDirectory(path)
            bookmarkRepository.addRecent(path)
        }
    }

    /** Navigates to the parent of the current directory, when one exists. */
    fun navigateUp() {
        val parent = parentOf(_uiState.value.currentPath) ?: return
        openDirectory(parent)
    }

    /** Reloads the current directory using the active sort and filter. */
    fun refresh() {
        viewModelScope.launch { loadDirectory(_uiState.value.currentPath) }
    }

    /** Toggles selection of [item], leaving selection mode when nothing remains selected. */
    fun toggleSelection(item: FileItem) {
        val current = _uiState.value
        val updated = current.selectedPaths.toMutableSet().apply {
            if (!add(item.path)) remove(item.path)
        }
        _uiState.value = current.copy(
            selectedPaths = updated,
            selectionMode = updated.isNotEmpty(),
        )
    }

    /** Enters multi-select mode with [item] as the first selected entry. */
    fun enterSelection(item: FileItem) {
        _uiState.value = _uiState.value.copy(
            selectionMode = true,
            selectedPaths = setOf(item.path),
        )
    }

    /** Exits multi-select mode and clears any selection. */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectionMode = false,
            selectedPaths = emptySet(),
        )
    }

    /** Applies a new [sort] order, persists it, and reloads the listing. */
    fun setSort(sort: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = sort)
        viewModelScope.launch {
            settings.setSortOption(sort)
            loadDirectory(_uiState.value.currentPath)
        }
    }

    /** Applies a new [filter] and reloads the listing. */
    fun setFilter(filter: FilterOption) {
        _uiState.value = _uiState.value.copy(filter = filter)
        viewModelScope.launch {
            settings.setShowHidden(filter.showHidden)
            loadDirectory(_uiState.value.currentPath)
        }
    }

    /** Creates a new folder named [name] inside the current directory. */
    fun createFolder(name: String) {
        val parent = _uiState.value.currentPath
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = fileRepository.createFolder(parent, name.trim())) {
                is AppResult.Success -> loadDirectory(parent)
                is AppResult.Failure ->
                    _uiState.value = _uiState.value.copy(error = result.error.displayMessage)
            }
        }
    }

    /** Renames [item] to [newName] and reloads the listing on success. */
    fun rename(item: FileItem, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            when (val result = fileRepository.rename(item, newName.trim())) {
                is AppResult.Success -> loadDirectory(_uiState.value.currentPath)
                is AppResult.Failure ->
                    _uiState.value = _uiState.value.copy(error = result.error.displayMessage)
            }
        }
    }

    /** Deletes all currently selected items. */
    fun deleteSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            when (val result = fileRepository.delete(targets)) {
                is AppResult.Success -> {
                    clearSelection()
                    loadDirectory(_uiState.value.currentPath)
                }
                is AppResult.Failure ->
                    _uiState.value = _uiState.value.copy(error = result.error.displayMessage)
            }
        }
    }

    /** Copies the current selection into [destinationPath], streaming progress into state. */
    fun copySelectedTo(destinationPath: String) {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        runOperation(fileRepository.copy(targets, destinationPath), destinationPath)
    }

    /** Moves the current selection into [destinationPath], streaming progress into state. */
    fun moveSelectedTo(destinationPath: String) {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        runOperation(fileRepository.move(targets, destinationPath), destinationPath)
    }

    /** Cancels the in-flight copy/move operation, if any. */
    fun cancelOperation() {
        operationDismissJob?.cancel()
        operationDismissJob = null
        operationJob?.cancel()
        operationJob = null
        _uiState.value = _uiState.value.copy(operation = null)
    }

    /** Adds a bookmark pointing at [item]. */
    fun addBookmark(item: FileItem) {
        viewModelScope.launch {
            bookmarkRepository.addBookmark(item.path, item.name)
        }
    }

    /** Dismisses any currently displayed error message. */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ---- internals -------------------------------------------------------

    /**
     * Collects a copy/move progress [flow] into [FileBrowserUiState.operation].
     * On terminal states the selection is cleared and the listing reloaded; the
     * terminal card ("Copied", "Moved", "… failed") is left visible so the user
     * sees a completion confirmation, then auto-dismissed after a short delay.
     */
    private fun runOperation(
        flow: kotlinx.coroutines.flow.Flow<FileOperationProgress>,
        destinationPath: String,
    ) {
        operationDismissJob?.cancel()
        operationDismissJob = null
        operationJob?.cancel()
        operationJob = flow
            .onEach { progress ->
                _uiState.value = _uiState.value.copy(operation = progress)
                when (progress.state) {
                    OperationState.COMPLETED -> {
                        clearSelection()
                        loadDirectory(_uiState.value.currentPath)
                        // Re-apply the terminal progress; loadDirectory may have
                        // replaced state, but the card must stay visible.
                        _uiState.value = _uiState.value.copy(operation = progress)
                        scheduleOperationDismiss(progress)
                    }
                    OperationState.FAILED -> {
                        _uiState.value = _uiState.value.copy(
                            error = progress.errorMessage ?: "Operation failed.",
                        )
                        scheduleOperationDismiss(progress)
                    }
                    else -> Unit
                }
            }
            .onCompletion { cause ->
                operationJob = null
                // Only clear immediately on cancellation; terminal COMPLETED/FAILED
                // cards stay visible and are dismissed by the scheduled delay so the
                // user actually sees the completion confirmation.
                if (cause != null) {
                    operationDismissJob?.cancel()
                    operationDismissJob = null
                    _uiState.value = _uiState.value.copy(operation = null)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Keeps the terminal operation card visible briefly, then clears it (only if
     * it is still showing the same terminal [progress], so a newer operation is
     * never dismissed by an earlier one's timer).
     */
    private fun scheduleOperationDismiss(progress: FileOperationProgress) {
        operationDismissJob?.cancel()
        operationDismissJob = viewModelScope.launch {
            delay(1_500)
            if (_uiState.value.operation === progress) {
                _uiState.value = _uiState.value.copy(operation = null)
            }
        }
    }

    /** Loads [path] into state, updating breadcrumbs and navigation flags. */
    private suspend fun loadDirectory(path: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
        )
        val state = _uiState.value
        when (val result = fileRepository.listFiles(path, state.sortOption, state.filter)) {
            is AppResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    currentPath = path,
                    breadcrumbs = buildBreadcrumbs(path),
                    items = result.data,
                    isLoading = false,
                    error = null,
                    canNavigateUp = parentOf(path) != null,
                )
            }
            is AppResult.Failure -> {
                _uiState.value = _uiState.value.copy(
                    currentPath = path,
                    breadcrumbs = buildBreadcrumbs(path),
                    isLoading = false,
                    error = result.error.displayMessage,
                    canNavigateUp = parentOf(path) != null,
                )
            }
        }
    }

    /** Resolves the [FileItem]s for the currently selected paths from the visible listing. */
    private fun selectedItems(): List<FileItem> {
        val selected = _uiState.value.selectedPaths
        return _uiState.value.items.filter { it.path in selected }
    }

    /**
     * Returns the parent directory of [path], or null when [path] is at (or above)
     * the storage root and therefore has no navigable parent.
     */
    private fun parentOf(path: String): String? {
        if (path.isBlank()) return null
        val root = fileRepository.rootDirectory().trimEnd('/')
        val normalized = path.trimEnd('/')
        if (normalized.isEmpty() || normalized == root || normalized == "") return null
        val parent = File(normalized).parent ?: return null
        // Do not allow navigating above the storage root.
        if (root.isNotEmpty() && !normalized.startsWith(root)) return parent.ifEmpty { null }
        return if (parent.length < root.length) null else parent.ifEmpty { null }
    }

    /**
     * Splits [path] into an ordered list of [Breadcrumb]s, each pointing at a
     * progressively deeper directory.
     */
    private fun buildBreadcrumbs(path: String): List<Breadcrumb> {
        val normalized = path.trimEnd('/')
        if (normalized.isEmpty()) {
            return listOf(Breadcrumb(name = "/", path = "/"))
        }
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        val crumbs = ArrayList<Breadcrumb>(segments.size + 1)
        crumbs.add(Breadcrumb(name = "/", path = "/"))
        val builder = StringBuilder()
        for (segment in segments) {
            builder.append('/').append(segment)
            crumbs.add(Breadcrumb(name = segment, path = builder.toString()))
        }
        return crumbs
    }
}
