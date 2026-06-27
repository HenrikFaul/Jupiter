package com.jupiter.filemanager.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.MergeRecommendation
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
 * Drives the Smart Merge screen.
 *
 * Scans the primary storage root for real duplicate groups via
 * [StorageAnalyticsRepository.findDuplicates] and converts each group into a
 * [MergeRecommendation]. The recommended copy to keep is the newest file in the
 * group (falling back to the first one), and the remaining copies are marked as
 * removable. The user can override which copy to keep per group, and applying
 * the merge deletes the non-kept copies on a background dispatcher.
 */
@HiltViewModel
class SmartMergeViewModel @Inject constructor(
    private val analyticsRepository: StorageAnalyticsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartMergeUiState())
    val uiState: StateFlow<SmartMergeUiState> = _uiState.asStateFlow()

    init {
        scan()
    }

    /** Starts (or restarts) a duplicate scan and rebuilds merge recommendations. */
    fun scan() {
        viewModelScope.launch {
            val root = android.os.Environment.getExternalStorageDirectory()?.absolutePath
                ?: "/storage/emulated/0"
            val collected = mutableListOf<MergeRecommendation>()
            analyticsRepository.findDuplicates(root)
                .onStart {
                    _uiState.value = _uiState.value.copy(
                        isScanning = true,
                        recommendations = emptyList(),
                        keepSelections = emptyMap(),
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
                        collected.add(buildRecommendation(group))
                        _uiState.value = _uiState.value.copy(recommendations = collected.toList())
                    }
                }
        }
    }

    /**
     * Overrides which copy to keep for the duplicate group identified by
     * [groupHash]. The removable set is implicitly "everything else in the group".
     */
    fun selectKeep(groupHash: String, path: String) {
        val updated = _uiState.value.keepSelections + (groupHash to path)
        _uiState.value = _uiState.value.copy(keepSelections = updated)
    }

    /** Dismisses any transient error or info message. */
    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    /**
     * Applies every recommendation: for each group, deletes all copies except the
     * one currently chosen to keep. Runs on a background dispatcher and then
     * removes merged groups from state.
     */
    fun mergeAll() {
        val state = _uiState.value
        if (state.recommendations.isEmpty() || state.isMerging) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMerging = true, errorMessage = null, infoMessage = null)

            // Resolve the paths to remove for every recommendation using the
            // current keep selection (user override or recommended default).
            val toRemove = state.recommendations.flatMap { rec ->
                val keepPath = state.keepPathFor(rec.group.hash, rec.recommendedKeepPath)
                rec.group.files.map { it.path }.filter { it != keepPath }
            }

            val (deleted, failed) = withContext(Dispatchers.IO) {
                val deletedPaths = mutableSetOf<String>()
                var failures = 0
                for (path in toRemove) {
                    val file = File(path)
                    if (!file.exists() || file.delete()) {
                        deletedPaths.add(path)
                    } else {
                        failures++
                    }
                }
                deletedPaths to failures
            }

            // Rebuild recommendations from what survived; drop fully-merged groups.
            val remaining = _uiState.value.recommendations
                .mapNotNull { rec ->
                    val survivors = rec.group.files.filter { it.path !in deleted }
                    if (survivors.size <= 1) {
                        null
                    } else {
                        buildRecommendation(rec.group.copy(files = survivors))
                    }
                }

            val message = when {
                deleted.isEmpty() -> "Could not remove the duplicate copies"
                failed > 0 -> "Merged ${deleted.size} duplicate" +
                    (if (deleted.size == 1) "" else "s") + ", $failed failed"
                else -> "Merged ${deleted.size} duplicate" + if (deleted.size == 1) "" else "s"
            }

            _uiState.value = _uiState.value.copy(
                isMerging = false,
                recommendations = remaining,
                keepSelections = _uiState.value.keepSelections.filterKeys { hash ->
                    remaining.any { it.group.hash == hash }
                },
                errorMessage = if (deleted.isEmpty()) message else null,
                infoMessage = if (deleted.isEmpty()) null else message,
            )
        }
    }

    /**
     * Builds a [MergeRecommendation] for [group], recommending that the newest
     * file (most recently modified) be kept and the rest removed.
     */
    private fun buildRecommendation(group: DuplicateGroup): MergeRecommendation {
        val keep: FileItem = group.files.maxByOrNull { it.lastModified } ?: group.files.first()
        val removable = group.files.map { it.path }.filter { it != keep.path }
        return MergeRecommendation(
            group = group,
            recommendedKeepPath = keep.path,
            removablePaths = removable,
        )
    }
}
