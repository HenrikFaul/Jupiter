package com.jupiter.filemanager.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.StorageOverview
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import com.jupiter.filemanager.feature.ai.AiAssistant
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Cleanup screen.
 *
 * @property isScanning whether a storage scan is currently running.
 * @property overview the categorized storage overview, or null before a scan completes.
 * @property largeFiles files discovered above the large-file size threshold.
 * @property duplicateGroups groups of identical files that could be de-duplicated.
 * @property selectedForDeletion the set of file paths the user has marked for deletion.
 * @property reclaimableBytes total size of the currently selected files, in bytes.
 * @property aiExplanation a natural-language explanation of the overview, when available.
 * @property error a human-readable error message, or null when there is none.
 * @property permissionRequired true when the scan was skipped because the app lacks
 *   All-Files-Access; the screen shows an actionable CTA instead of spinning.
 */
data class CleanupUiState(
    val isScanning: Boolean = false,
    val overview: StorageOverview? = null,
    val largeFiles: List<FileItem> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val selectedForDeletion: Set<String> = emptySet(),
    val reclaimableBytes: Long = 0L,
    val aiExplanation: String? = null,
    val error: String? = null,
    val permissionRequired: Boolean = false,
)

/**
 * Drives the Cleanup screen.
 *
 * A [scan] produces a categorized [StorageOverview] and concurrently streams large
 * files (>= [LARGE_FILE_THRESHOLD_BYTES]) and duplicate groups from the storage
 * analytics repository, populating the UI state incrementally as results arrive.
 *
 * The user selects files for deletion via [toggleSelection]; the total
 * [CleanupUiState.reclaimableBytes] is recomputed from the current selection after
 * each change and after each scan. [deleteSelected] removes the chosen files and
 * prunes them from the scan results. [explainWithAi] requests a natural-language
 * summary of the overview, guarded by [AiAssistant.isEnabled].
 *
 * All blocking IO is performed inside repository flows/suspend functions on
 * background dispatchers; this ViewModel only orchestrates collection on
 * `viewModelScope`.
 */
@HiltViewModel
class CleanupViewModel @Inject constructor(
    private val analyticsRepository: StorageAnalyticsRepository,
    private val fileRepository: FileRepository,
    private val aiAssistant: AiAssistant,
    private val storageAccessManager: StorageAccessManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanupUiState())
    val uiState: StateFlow<CleanupUiState> = _uiState.asStateFlow()

    /** Job for the in-flight scan so a new scan cancels the previous one. */
    private var scanJob: Job? = null

    /**
     * Starts a fresh storage scan.
     *
     * Resets prior results, loads the storage overview, then collects large files
     * and duplicate groups concurrently. Any running scan is cancelled first.
     */
    fun scan() {
        scanJob?.cancel()

        // Permission gate: without All-Files-Access the walk silently returns 0 results,
        // so skip it entirely and surface an actionable CTA instead of spinning.
        if (!storageAccessManager.hasAllFilesAccess()) {
            _uiState.update {
                it.copy(
                    isScanning = false,
                    permissionRequired = true,
                    error = null,
                    overview = null,
                    largeFiles = emptyList(),
                    duplicateGroups = emptyList(),
                    selectedForDeletion = emptySet(),
                    reclaimableBytes = 0L,
                    aiExplanation = null,
                )
            }
            return
        }

        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    permissionRequired = false,
                    error = null,
                    overview = null,
                    largeFiles = emptyList(),
                    duplicateGroups = emptyList(),
                    selectedForDeletion = emptySet(),
                    reclaimableBytes = 0L,
                    aiExplanation = null,
                )
            }

            loadOverview()

            val rootPath = fileRepository.rootDirectory()
            // Duplicate scanning is the slowest pass; let its groups stream in as a
            // secondary indicator rather than gating isScanning on its completion.
            launch { collectDuplicates(rootPath) }
            // The primary scan (overview + large files) determines when the
            // full-screen spinner clears; duplicates continue in the background.
            collectLargeFiles(rootPath)
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    /**
     * Toggles whether the file at [path] is selected for deletion and recomputes
     * the reclaimable-bytes total from the new selection.
     */
    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val updated = if (path in state.selectedForDeletion) {
                state.selectedForDeletion - path
            } else {
                state.selectedForDeletion + path
            }
            state.copy(
                selectedForDeletion = updated,
                reclaimableBytes = reclaimableBytesFor(updated, state),
            )
        }
    }

    /**
     * Deletes every currently selected file. On success the deleted paths are
     * removed from the large-file list and duplicate groups, and the selection is
     * cleared; on failure the selection is preserved and an error is surfaced.
     */
    fun deleteSelected() {
        val state = _uiState.value
        val selectedPaths = state.selectedForDeletion
        if (selectedPaths.isEmpty()) return

        val itemsToDelete = collectSelectedItems(selectedPaths, state)
        if (itemsToDelete.isEmpty()) {
            _uiState.update {
                it.copy(selectedForDeletion = emptySet(), reclaimableBytes = 0L)
            }
            return
        }

        viewModelScope.launch {
            when (val result = fileRepository.delete(itemsToDelete)) {
                is AppResult.Success -> _uiState.update { current ->
                    val remainingLarge = current.largeFiles
                        .filterNot { it.path in selectedPaths }
                    val remainingGroups = current.duplicateGroups
                        .map { group ->
                            group.copy(files = group.files.filterNot { it.path in selectedPaths })
                        }
                        .filter { it.files.size > 1 }
                    current.copy(
                        largeFiles = remainingLarge,
                        duplicateGroups = remainingGroups,
                        selectedForDeletion = emptySet(),
                        reclaimableBytes = 0L,
                        error = null,
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(error = result.error.displayMessage)
                }
            }
        }
    }

    /**
     * Requests a natural-language explanation of the current [StorageOverview] from
     * the [AiAssistant]. No-ops when AI is disabled or no overview is available; on
     * failure the assistant's message is surfaced as an error.
     */
    fun explainWithAi() {
        val overview = _uiState.value.overview ?: return
        if (!aiAssistant.isEnabled) return

        viewModelScope.launch {
            when (val result = aiAssistant.explainStorage(overview)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(aiExplanation = result.data, error = null)
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(error = result.error.displayMessage)
                }
            }
        }
    }

    /** Loads the categorized storage overview into the UI state. */
    private suspend fun loadOverview() {
        when (val result = analyticsRepository.storageOverview()) {
            is AppResult.Success -> _uiState.update { it.copy(overview = result.data) }
            is AppResult.Failure -> _uiState.update {
                it.copy(error = result.error.displayMessage)
            }
        }
    }

    /**
     * Streams large files under [rootPath] into the UI state as they are found.
     */
    private suspend fun collectLargeFiles(rootPath: String) {
        val collected = mutableListOf<FileItem>()
        analyticsRepository.findLargeFiles(rootPath, LARGE_FILE_THRESHOLD_BYTES)
            .catch { throwable ->
                _uiState.update {
                    it.copy(error = throwable.message ?: "Failed to scan large files.")
                }
            }
            .collect { item ->
                collected.add(item)
                _uiState.update { it.copy(largeFiles = collected.toList()) }
            }
    }

    /**
     * Streams duplicate groups under [rootPath] into the UI state as they are found.
     */
    private suspend fun collectDuplicates(rootPath: String) {
        val collected = mutableListOf<DuplicateGroup>()
        analyticsRepository.findDuplicates(rootPath)
            .catch { throwable ->
                _uiState.update {
                    it.copy(error = throwable.message ?: "Failed to scan duplicates.")
                }
            }
            .collect { group ->
                collected.add(group)
                _uiState.update { it.copy(duplicateGroups = collected.toList()) }
            }
    }

    /**
     * Sums the sizes of all files in [state] whose paths are in [selectedPaths],
     * looking across both the large-file list and every duplicate group.
     */
    private fun reclaimableBytesFor(selectedPaths: Set<String>, state: CleanupUiState): Long {
        if (selectedPaths.isEmpty()) return 0L
        return knownItems(state)
            .filter { it.path in selectedPaths }
            .sumOf { it.sizeBytes }
    }

    /**
     * Resolves [selectedPaths] to the concrete [FileItem]s known from the current
     * scan results, de-duplicating by path.
     */
    private fun collectSelectedItems(
        selectedPaths: Set<String>,
        state: CleanupUiState,
    ): List<FileItem> =
        knownItems(state)
            .filter { it.path in selectedPaths }
            .distinctBy { it.path }

    /**
     * All files surfaced by the current scan: large files plus every member of
     * every duplicate group.
     */
    private fun knownItems(state: CleanupUiState): List<FileItem> =
        state.largeFiles + state.duplicateGroups.flatMap { it.files }

    private companion object {
        /** Minimum size, in bytes, for a file to be reported as a "large file" (100 MB). */
        const val LARGE_FILE_THRESHOLD_BYTES: Long = 100L * 1024L * 1024L
    }
}
