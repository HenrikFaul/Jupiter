package com.jupiter.filemanager.feature.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.media.CategorySort
import com.jupiter.filemanager.data.media.MediaStoreCategorySource
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        load()
    }

    /** Queries the source for the current category using the current sort order. */
    fun load() {
        val sort = _uiState.value.sort
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val items = source.query(category, sort)
            _uiState.value = _uiState.value.copy(
                items = items,
                isLoading = false,
                error = null,
            )
        }
    }

    /** Changes the sort order and re-queries. No-op when the order is unchanged. */
    fun setSort(sort: CategorySort) {
        if (sort == _uiState.value.sort) return
        _uiState.value = _uiState.value.copy(sort = sort)
        load()
    }

    /** Retries the query after a failure. */
    fun retry() {
        load()
    }
}
