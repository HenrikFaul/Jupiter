package com.jupiter.filemanager.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.media.MediaQualityProbe
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.MediaQuality
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
 * [MergeRecommendation]. Each group's files are probed for intrinsic media
 * quality with [MediaQualityProbe], and the recommended copy to keep is the
 * highest-quality one (ties broken by larger size, then most recent
 * modification). The user can override which copy to keep per group, and
 * applying the merge deletes the non-kept copies on a background dispatcher.
 */
@HiltViewModel
class SmartMergeViewModel @Inject constructor(
    private val analyticsRepository: StorageAnalyticsRepository,
    private val qualityProbe: MediaQualityProbe,
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
            val qualities = mutableMapOf<String, MediaQuality>()
            analyticsRepository.findDuplicates(root)
                .onStart {
                    _uiState.value = _uiState.value.copy(
                        isScanning = true,
                        recommendations = emptyList(),
                        keepSelections = emptyMap(),
                        qualities = emptyMap(),
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
                        // Probe quality for this group's files (off-main), then
                        // build a quality-ranked recommendation from it.
                        val groupQualities = qualityProbe.probeAll(group.files.map { it.path })
                        qualities.putAll(groupQualities)
                        collected.add(buildRecommendation(group, qualities))
                        _uiState.value = _uiState.value.copy(
                            recommendations = collected.toList(),
                            qualities = qualities.toMap(),
                        )
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

            val qualities = _uiState.value.qualities

            // Rebuild recommendations from what survived; drop fully-merged groups.
            val remaining = _uiState.value.recommendations
                .mapNotNull { rec ->
                    val survivors = rec.group.files.filter { it.path !in deleted }
                    if (survivors.size <= 1) {
                        null
                    } else {
                        buildRecommendation(rec.group.copy(files = survivors), qualities)
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
                qualities = qualities.filterKeys { path ->
                    path !in deleted
                },
                errorMessage = if (deleted.isEmpty()) message else null,
                infoMessage = if (deleted.isEmpty()) null else message,
            )
        }
    }

    /**
     * Builds a [MergeRecommendation] for [group], recommending that the
     * highest-quality copy be kept and the rest removed.
     *
     * Files are ranked by probed [MediaQuality.score] (desc), then on-disk size
     * (desc), then last-modified time (desc), so the best available copy wins.
     */
    private fun buildRecommendation(
        group: DuplicateGroup,
        qualities: Map<String, MediaQuality>,
    ): MergeRecommendation {
        val keep: FileItem = group.files.maxWithOrNull(qualityComparator(qualities))
            ?: group.files.first()
        val removable = group.files.map { it.path }.filter { it != keep.path }
        return MergeRecommendation(
            group = group,
            recommendedKeepPath = keep.path,
            removablePaths = removable,
        )
    }

    /**
     * Orders [FileItem]s so the "best" copy is greatest: highest quality score
     * first, then largest size, then most recently modified.
     */
    private fun qualityComparator(qualities: Map<String, MediaQuality>): Comparator<FileItem> =
        compareBy<FileItem> { qualities[it.path]?.score ?: 0L }
            .thenBy { it.sizeBytes }
            .thenBy { it.lastModified }
}
