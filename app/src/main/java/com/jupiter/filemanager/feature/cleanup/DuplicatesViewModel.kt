package com.jupiter.filemanager.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Drives the Duplicates screen: scans the primary storage root for duplicate
 * files (real data via [StorageAnalyticsRepository.findDuplicates]), lets the
 * user select redundant copies to remove, and deletes them on a background
 * dispatcher.
 */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val analyticsRepository: StorageAnalyticsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    init {
        scan()
    }

    /** Starts (or restarts) a duplicate scan over the primary storage root. */
    fun scan() {
        viewModelScope.launch {
            val root = android.os.Environment.getExternalStorageDirectory()?.absolutePath
                ?: "/storage/emulated/0"
            val collected = mutableListOf<DuplicateGroup>()
            analyticsRepository.findDuplicates(root)
                .onStart {
                    _uiState.value = _uiState.value.copy(
                        isScanning = true,
                        groups = emptyList(),
                        selectedPaths = emptySet(),
                        errorMessage = null,
                        infoMessage = null,
                    )
                }
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        errorMessage = throwable.message ?: "Failed to scan for duplicates",
                    )
                }
                .onCompletion { cause ->
                    if (cause == null) {
                        _uiState.value = _uiState.value.copy(isScanning = false)
                    }
                }
                .collect { group ->
                    if (group.files.size > 1) {
                        collected.add(group)
                        _uiState.value = _uiState.value.copy(groups = collected.toList())
                    }
                }
        }
    }

    /** Toggles selection of a single file path within a duplicate group. */
    fun toggleSelection(path: String) {
        val current = _uiState.value.selectedPaths
        val updated = if (path in current) current - path else current + path
        _uiState.value = _uiState.value.copy(selectedPaths = updated)
    }

    /**
     * Selects every duplicate copy except the first (kept) file in each group,
     * which is the recommended way to reclaim the most space safely.
     */
    fun selectAllExtras() {
        val extras = _uiState.value.groups
            .asSequence()
            .flatMap { it.files.drop(1).asSequence() }
            .map { it.path }
            .toSet()
        _uiState.value = _uiState.value.copy(selectedPaths = extras)
    }

    /** Clears the current selection. */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
    }

    /** Dismisses any transient error or info message. */
    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    /**
     * Deletes the currently selected files on a background dispatcher, then
     * refreshes group state by removing the deleted files and dropping any group
     * that no longer contains duplicates.
     */
    fun deleteSelected() {
        val state = _uiState.value
        if (state.selectedPaths.isEmpty() || state.isDeleting) return

        val targets = state.selectedPaths.toList()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, errorMessage = null, infoMessage = null)

            val (deleted, failed) = withContext(Dispatchers.IO) {
                val deletedPaths = mutableSetOf<String>()
                var failures = 0
                for (path in targets) {
                    val file = File(path)
                    if (!file.exists() || file.delete()) {
                        deletedPaths.add(path)
                    } else {
                        failures++
                    }
                }
                deletedPaths to failures
            }

            val remainingGroups = _uiState.value.groups
                .map { group -> group.copy(files = group.files.filter { it.path !in deleted }) }
                .filter { it.files.size > 1 }

            val message = when {
                deleted.isEmpty() -> "Could not delete the selected files"
                failed > 0 -> "Deleted ${deleted.size} file" +
                    (if (deleted.size == 1) "" else "s") + ", $failed failed"
                else -> "Deleted ${deleted.size} file" + if (deleted.size == 1) "" else "s"
            }

            _uiState.value = _uiState.value.copy(
                isDeleting = false,
                groups = remainingGroups,
                selectedPaths = _uiState.value.selectedPaths - deleted,
                errorMessage = if (deleted.isEmpty()) message else null,
                infoMessage = if (deleted.isEmpty()) null else message,
            )
        }
    }
}
