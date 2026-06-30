package com.jupiter.filemanager.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.media.MediaQualityProbe
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.MediaQuality
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
 *
 * It additionally probes each discovered file's intrinsic media quality via
 * [MediaQualityProbe] (off the main thread) so the UI can show a per-file
 * quality label.
 */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val analyticsRepository: StorageAnalyticsRepository,
    private val mediaQualityProbe: MediaQualityProbe,
    private val storageAccessManager: StorageAccessManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    /**
     * Bounds concurrent media-quality probes so deferred [probeGroup] calls never
     * flood the IO dispatcher while (or after) the scan is running.
     */
    private val probeGate = Semaphore(permits = 2)

    /** Tracks deferred probe jobs so a rescan can cancel stale probing. */
    private val probeJobs = mutableListOf<Job>()

    init {
        scan()
    }

    /**
     * Starts (or restarts) a duplicate scan over the primary storage root.
     *
     * If the app lacks All-Files-Access, the walk is skipped entirely and
     * [DuplicatesUiState.permissionRequired] is set so the screen can render an
     * actionable CTA instead of an indefinite spinner.
     */
    fun scan() {
        if (!storageAccessManager.hasAllFilesAccess()) {
            cancelProbes()
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                permissionRequired = true,
                groups = emptyList(),
                selectedPaths = emptySet(),
                qualities = emptyMap(),
                errorMessage = null,
                infoMessage = null,
            )
            return
        }

        cancelProbes()
        viewModelScope.launch {
            val root = android.os.Environment.getExternalStorageDirectory()?.absolutePath
                ?: "/storage/emulated/0"
            val collected = mutableListOf<DuplicateGroup>()
            analyticsRepository.findDuplicates(root)
                .onStart {
                    _uiState.value = _uiState.value.copy(
                        isScanning = true,
                        permissionRequired = false,
                        groups = emptyList(),
                        selectedPaths = emptySet(),
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
                        // Probe quality only after the scan finishes, so probing never
                        // competes with the (CPU/IO-heavy) hashing walk on the IO pool.
                        probeGroups(collected.toList())
                    }
                }
                .collect { group ->
                    if (group.files.size > 1) {
                        collected.add(group)
                        // First group leaves the spinner; the screen renders content as
                        // soon as `groups` is non-empty even while `isScanning` is true.
                        _uiState.value = _uiState.value.copy(groups = collected.toList())
                    }
                }
        }
    }

    /**
     * Probes media quality for every discovered [groups] off the scan critical
     * path, one group per coroutine, with concurrency bounded by [probeGate].
     * Merges results into UI state as they resolve. Never throws; the probe itself
     * guards each file and falls back to a safe empty quality.
     */
    private fun probeGroups(groups: List<DuplicateGroup>) {
        for (group in groups) {
            val paths = group.files.map { it.path }
            if (paths.isEmpty()) continue
            val job = viewModelScope.launch {
                probeGate.withPermit {
                    val probed: Map<String, MediaQuality> = mediaQualityProbe.probeAll(paths)
                    if (probed.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            qualities = _uiState.value.qualities + probed,
                        )
                    }
                }
            }
            probeJobs.add(job)
        }
    }

    /** Cancels any in-flight deferred probe jobs (e.g. before a rescan). */
    private fun cancelProbes() {
        probeJobs.forEach { it.cancel() }
        probeJobs.clear()
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

    /** Surfaces a transient informational message (e.g. "Path copied"). */
    fun notify(message: String) {
        _uiState.value = _uiState.value.copy(infoMessage = message)
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
                qualities = _uiState.value.qualities - deleted,
                errorMessage = if (deleted.isEmpty()) message else null,
                infoMessage = if (deleted.isEmpty()) null else message,
            )
        }
    }
}
