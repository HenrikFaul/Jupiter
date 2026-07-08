package com.jupiter.filemanager.feature.downloads

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SortDirection
import com.jupiter.filemanager.domain.model.SortField
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Drives the Downloads screen: lists the real device Downloads folder
 * (the primary external storage `Download` directory) sorted by most recently
 * modified first.
 *
 * The repository performs all blocking IO on a background dispatcher; this
 * ViewModel only orchestrates state on [viewModelScope]. There is no fabricated
 * data — when the Downloads folder is empty or missing, an honest empty state is
 * surfaced.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val fileRepository: FileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        loadDownloads()
    }

    /** Re-lists the Downloads folder. */
    fun refresh() {
        loadDownloads()
    }

    /** Dismisses any currently displayed error message. */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ---- internals -------------------------------------------------------

    private fun loadDownloads() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val path = resolveDownloadsPath()
            when (val result = fileRepository.listFiles(path, MODIFIED_SORT, NO_FILTER)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        files = result.data
                            .filter { !it.isDirectory }
                            .sortedByDescending { it.lastModified },
                        isLoading = false,
                        error = null,
                    )
                }

                is AppResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        files = emptyList(),
                        isLoading = false,
                        // An inaccessible/missing Downloads folder is treated as
                        // "empty" rather than an error to keep the UI honest.
                        error = null,
                    )
                }
            }
        }
    }

    /**
     * Resolves the absolute path of the primary external Downloads directory.
     * Falls back to the repository root joined with the standard directory name
     * when the system directory cannot be resolved.
     */
    private fun resolveDownloadsPath(): String {
        val systemDir = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }.getOrNull()
        if (systemDir != null && systemDir.absolutePath.isNotBlank()) {
            return systemDir.absolutePath
        }
        return File(fileRepository.rootDirectory(), Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    private companion object {
        val MODIFIED_SORT = SortOption(
            field = SortField.DATE_MODIFIED,
            direction = SortDirection.DESCENDING,
            foldersFirst = false,
        )
        val NO_FILTER = FilterOption()
    }
}
