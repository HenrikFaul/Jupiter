package com.jupiter.filemanager.feature.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.core.util.StorageExclusions
import com.jupiter.filemanager.data.media.CategorySort
import com.jupiter.filemanager.data.media.MediaStoreCategorySource
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FileOperationType
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.domain.model.SortDirection
import com.jupiter.filemanager.domain.model.SortField
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [CategoryBrowseScreen]: resolves an instant, device-wide listing for a
 * single [StorageCategory] by delegating to [MediaStoreCategorySource] (a
 * `MediaStore` query — never a recursive filesystem walk), so it stays fast even
 * with tens of thousands of files.
 *
 * The category is read once from the navigation argument
 * ([Destination.CategoryBrowse.ARG_TYPE]); an unknown/missing value falls back to
 * [StorageCategory.IMAGES] so the screen never lands in an undefined state. All
 * IO happens inside the source on a background dispatcher; this ViewModel only
 * orchestrates state on [viewModelScope] and never crashes on a failed query.
 */
@HiltViewModel
class CategoryBrowseViewModel @Inject constructor(
    private val source: MediaStoreCategorySource,
    private val fileRepository: FileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** The category to browse, resolved from the navigation argument. */
    private val category: StorageCategory = savedStateHandle
        .get<String>(Destination.CategoryBrowse.ARG_TYPE)
        ?.let { raw -> runCatching { StorageCategory.valueOf(raw) }.getOrNull() }
        ?: StorageCategory.IMAGES

    private val _uiState = MutableStateFlow(
        CategoryBrowseUiState(category = category, isLoading = true),
    )
    val uiState: StateFlow<CategoryBrowseUiState> = _uiState.asStateFlow()

    /** The last complete MediaStore result, before a Photos location filter. */
    private var sourceItems: List<FileItem> = emptyList()

    /** Ensures an earlier slow query cannot overwrite a later sort selection. */
    private var queryGeneration: Long = 0L

    /** Old MediaStore paths moved successfully during this VM lifetime. */
    private val suppressedPhotoPaths = LinkedHashSet<String>()

    private var folderQueryGeneration: Long = 0L
    private var moveJob: Job? = null

    init {
        load()
    }

    /** Queries the source for the current category using the current sort order. */
    fun load() {
        val sort = _uiState.value.sort
        val generation = ++queryGeneration
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { source.query(category, sort) }
            // Sort changes can issue overlapping MediaStore queries. Only the
            // newest request is allowed to publish its result.
            if (generation != queryGeneration) return@launch

            result.onSuccess { queriedItems ->
                // MediaStore can briefly return the old row after a successful
                // filesystem move. Never allow that stale path back into state.
                val items = PhotoMovePolicy.withoutSuppressedPaths(
                    queriedItems,
                    suppressedPhotoPaths,
                )
                sourceItems = items
                _uiState.update { current ->
                    val visibleItems = filterItems(items, current.photoFilter)
                    val visiblePaths = visibleItems.asSequence().map(FileItem::path).toHashSet()
                    current.copy(
                        items = visibleItems,
                        sourceItemCount = items.size,
                        sourceTotalSizeBytes = items.sumOf { it.sizeBytes },
                        selectedPaths = current.selectedPaths.intersect(visiblePaths),
                        isLoading = false,
                        error = null,
                    )
                }
            }.onFailure {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = "Couldn't load ${category.name.lowercase()} right now.",
                    )
                }
            }
        }
    }

    /** Changes the sort order and re-queries. No-op when the order is unchanged. */
    fun setSort(sort: CategorySort) {
        if (sort == _uiState.value.sort) return
        _uiState.update { it.copy(sort = sort) }
        load()
    }

    /**
     * Applies a real folder refinement to the already-indexed Photos result.
     * Other categories intentionally ignore this: their MediaStore category
     * query is the meaningful filter for those content types.
     */
    fun setPhotoFilter(filter: PhotoLocationFilter) {
        val current = _uiState.value
        if (category != StorageCategory.IMAGES || filter == current.photoFilter) return

        _uiState.update {
            val visibleItems = filterItems(sourceItems, filter)
            val visiblePaths = visibleItems.asSequence().map(FileItem::path).toHashSet()
            it.copy(
                photoFilter = filter,
                items = visibleItems,
                sourceItemCount = sourceItems.size,
                sourceTotalSizeBytes = sourceItems.sumOf { source -> source.sizeBytes },
                selectedPaths = it.selectedPaths.intersect(visiblePaths),
            )
        }
    }

    /** Enters/exits photo selection. Leaving selection clears stale paths. */
    fun toggleSelectionMode() {
        if (category != StorageCategory.IMAGES || _uiState.value.isMoveBusy) return
        _uiState.update { state ->
            val enabled = !state.selectionMode
            state.copy(
                selectionMode = enabled,
                selectedPaths = if (enabled) state.selectedPaths else emptySet(),
            )
        }
    }

    /** Toggles one visible photo by stable path; no file operation occurs here. */
    fun togglePhotoSelection(item: FileItem) {
        if (
            category != StorageCategory.IMAGES ||
            _uiState.value.isMoveBusy ||
            _uiState.value.items.none { it.path == item.path }
        ) return
        _uiState.update { state ->
            val selected = state.selectedPaths.toMutableSet()
            if (!selected.add(item.path)) selected.remove(item.path)
            state.copy(selectionMode = true, selectedPaths = selected)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectionMode = false, selectedPaths = emptySet()) }
    }

    /** Opens Jupiter's real in-app folder chooser at the repository root. */
    fun openMoveFolderPicker() {
        val state = _uiState.value
        if (category != StorageCategory.IMAGES || state.isMoveBusy || moveJob?.isActive == true) return
        if (state.selectedItems.isEmpty()) {
            _uiState.update { it.copy(moveError = "Select at least one photo to move.") }
            return
        }

        val root = runCatching { fileRepository.rootDirectory() }.getOrElse { error ->
            _uiState.update { current ->
                current.copy(moveError = error.message ?: "Storage root is unavailable.")
            }
            return
        }
        if (root.isBlank()) {
            _uiState.update { it.copy(moveError = "Storage root is unavailable.") }
            return
        }
        _uiState.update {
            it.copy(
                moveFolderPicker = CategoryFolderPickerState(
                    rootPath = root,
                    currentPath = root,
                    isLoading = true,
                ),
                moveError = null,
            )
        }
        loadMoveFolder(root)
    }

    fun dismissMoveFolderPicker() {
        if (_uiState.value.isMoveBusy) return
        folderQueryGeneration += 1
        _uiState.update { it.copy(moveFolderPicker = null) }
    }

    /** Navigates only to repository-returned folders contained by the storage root. */
    fun navigateMoveFolder(path: String) {
        val picker = _uiState.value.moveFolderPicker ?: return
        if (_uiState.value.isMoveBusy || !PhotoMovePolicy.isWithinRoot(path, picker.rootPath)) return
        loadMoveFolder(path)
    }

    fun navigateMoveFolderUp() {
        val picker = _uiState.value.moveFolderPicker ?: return
        val parent = PhotoMovePolicy.parentWithinRoot(picker.currentPath, picker.rootPath) ?: return
        loadMoveFolder(parent)
    }

    fun retryMoveFolder() {
        val picker = _uiState.value.moveFolderPicker ?: return
        if (_uiState.value.isMoveBusy) return
        loadMoveFolder(picker.currentPath)
    }

    /**
     * Starts one guarded repository move. FileRepository owns the full-plan
     * collision preflight and CREATE_NEW write semantics, so this layer never
     * implements a weaker overwrite path.
     */
    fun confirmMoveToCurrentFolder() {
        val state = _uiState.value
        val picker = state.moveFolderPicker ?: return
        if (state.isMoveBusy || moveJob?.isActive == true || picker.isLoading) return

        val targets = state.selectedItems
        val validationError = PhotoMovePolicy.validationError(
            items = targets,
            destinationPath = picker.currentPath,
            rootPath = picker.rootPath,
        )
        if (validationError != null) {
            _uiState.update {
                it.copy(
                    moveFolderPicker = picker.copy(error = validationError),
                    moveError = validationError,
                )
            }
            return
        }

        val initialProgress = FileOperationProgress(
            type = FileOperationType.MOVE,
            state = OperationState.RUNNING,
            totalItems = targets.size,
            totalBytes = targets.sumOf { it.sizeBytes },
        )
        _uiState.update {
            it.copy(
                moveFolderPicker = null,
                moveOperation = initialProgress,
                moveError = null,
            )
        }

        val movedPaths = targets.map(FileItem::path).toSet()
        moveJob = fileRepository
            .move(targets, picker.currentPath)
            .catch { throwable ->
                emit(
                    FileOperationProgress(
                        type = FileOperationType.MOVE,
                        state = OperationState.FAILED,
                        totalItems = targets.size,
                        totalBytes = targets.sumOf { it.sizeBytes },
                        errorMessage = throwable.message ?: "Move failed.",
                    ),
                )
            }
            .onEach { progress ->
                when (progress.state) {
                    OperationState.COMPLETED -> completeMove(movedPaths, progress)
                    OperationState.FAILED -> _uiState.update {
                        it.copy(
                            moveOperation = progress,
                            moveError = progress.errorMessage ?: "Move failed.",
                        )
                    }
                    OperationState.CANCELLED -> _uiState.update {
                        it.copy(moveOperation = progress)
                    }
                    OperationState.RUNNING -> _uiState.update {
                        it.copy(moveOperation = progress, moveError = null)
                    }
                }
            }
            .onCompletion { cause ->
                moveJob = null
                if (cause != null && _uiState.value.moveOperation?.state == OperationState.RUNNING) {
                    _uiState.update {
                        it.copy(
                            moveOperation = FileOperationProgress(
                                type = FileOperationType.MOVE,
                                state = OperationState.CANCELLED,
                            ),
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun cancelMove() {
        moveJob?.cancel()
        moveJob = null
    }

    fun dismissMoveError() {
        _uiState.update { it.copy(moveError = null) }
    }

    fun dismissMoveFeedback() {
        if (_uiState.value.isMoveBusy) return
        _uiState.update { it.copy(moveOperation = null, moveError = null) }
    }

    /** Collapses/expands a real local-date photo group without changing its items. */
    fun toggleDateGroup(dayStart: Long) {
        if (category != StorageCategory.IMAGES) return
        _uiState.update { state ->
            val collapsed = state.collapsedDateGroups.toMutableSet()
            if (!collapsed.add(dayStart)) collapsed.remove(dayStart)
            state.copy(collapsedDateGroups = collapsed)
        }
    }

    /** Retries the query after a failure. */
    fun retry() {
        load()
    }

    private fun loadMoveFolder(path: String) {
        val picker = _uiState.value.moveFolderPicker ?: return
        if (!PhotoMovePolicy.isWithinRoot(path, picker.rootPath)) return
        val generation = ++folderQueryGeneration
        _uiState.update {
            it.copy(
                moveFolderPicker = picker.copy(
                    currentPath = path,
                    folders = emptyList(),
                    isLoading = true,
                    error = null,
                ),
            )
        }
        viewModelScope.launch {
            val result = fileRepository.listFiles(
                path = path,
                sort = MOVE_FOLDER_SORT,
                filter = MOVE_FOLDER_FILTER,
            )
            if (generation != folderQueryGeneration) return@launch
            val currentPicker = _uiState.value.moveFolderPicker ?: return@launch
            if (!PhotoMovePolicy.samePath(currentPicker.currentPath, path)) return@launch

            when (result) {
                is AppResult.Success -> _uiState.update { state ->
                    state.copy(
                        moveFolderPicker = currentPicker.copy(
                            folders = result.data
                                .asSequence()
                                .filter(FileItem::isDirectory)
                                .filterNot { StorageExclusions.isExcluded(it.path) }
                                .toList(),
                            isLoading = false,
                            error = null,
                        ),
                    )
                }
                is AppResult.Failure -> {
                    val message = result.error.displayMessage
                    _uiState.update { state ->
                        state.copy(
                            moveFolderPicker = currentPicker.copy(
                                folders = emptyList(),
                                isLoading = false,
                                error = message,
                            ),
                            moveError = message,
                        )
                    }
                }
            }
        }
    }

    private fun completeMove(
        movedPaths: Set<String>,
        progress: FileOperationProgress,
    ) {
        suppressedPhotoPaths.addAll(movedPaths)
        sourceItems = PhotoMovePolicy.withoutSuppressedPaths(sourceItems, suppressedPhotoPaths)
        _uiState.update { state ->
            val visibleItems = filterItems(sourceItems, state.photoFilter)
            state.copy(
                items = visibleItems,
                sourceItemCount = sourceItems.size,
                sourceTotalSizeBytes = sourceItems.sumOf { it.sizeBytes },
                selectionMode = false,
                selectedPaths = emptySet(),
                moveOperation = progress,
                moveError = null,
            )
        }
        // Starts a new generation, invalidating any query that began before the
        // move. Suppression above also protects against MediaStore propagation lag.
        load()
    }

    private fun filterItems(
        items: List<FileItem>,
        filter: PhotoLocationFilter,
    ): List<FileItem> = if (category == StorageCategory.IMAGES) {
        items.filter(filter::matches)
    } else {
        items
    }

    private companion object {
        val MOVE_FOLDER_SORT = SortOption(
            field = SortField.NAME,
            direction = SortDirection.ASCENDING,
            foldersFirst = true,
        )
        val MOVE_FOLDER_FILTER = FilterOption(
            showHidden = false,
            typeFilter = FileType.FOLDER,
        )
    }
}
